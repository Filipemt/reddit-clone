# Logs da aplicação

Este documento explica o padrão de logs estruturados usado no projeto: os níveis, cada evento emitido e quando ele ocorre.

Os eventos de domínio são emitidos por classes `*EventLog` (um componente por domínio). Os serviços, jobs e configs apenas chamam os métodos dessas classes; a category do logger permanece a da classe de origem.

## 1. Visão geral

A aplicação emite **logs estruturados** no formato **ECS (Elastic Common Schema)** — uma linha JSON por evento, escrita no console (`stdout`). Isso é configurado em `application.yaml`:

```yaml
logging:
  structured:
    format:
      console: ecs
```

Exemplo real de uma linha de log:

```json
{"@timestamp":"2026-09-21T10:15:00.000Z","log.level":"WARN","message":"Authentication failed","event":"auth.login.failed","username":"ghost","service.name":"clone-reddit","ecs.version":"8.11"}
```

Cada JSON contém campos padrão do ECS (`@timestamp`, `log.level`, `message`, `service.name`) **mais o campo `event`** e os campos de contexto adicionados no código (ex.: `userId`, `username`, `status`, `path`).

## 2. Convenções

- **Todo log tem um evento nomeado** em `event` (ex.: `auth.login.failed`), para filtrar e montar dashboards.
- **Dados sensíveis nunca são logados**: senha, valor de refresh token, valor de JWT nem cabeçalho `Authorization`. Emails não são logados (PII); usa-se `username` e `userId`.
- **Níveis**: eventos de negócio saudáveis em `INFO`; falhas esperadas/tratadas (respostas 4xx) em `WARN`; detalhes internos em `DEBUG`; erro inesperado em `ERROR`.

## 3. Níveis (tipos de log)

| Nível | Quando usar | O que captura no projeto |
|---|---|---|
| `DEBUG` | Detalhes operacionais internos, baixo volume | `auth.refresh.created`, `auth.jwt.generated`, `community.*` de baixo volume, `media.*.no_active_transaction` |
| `INFO` | Fluxos de negócio que funcionam (comportamento esperado) | `user.register.success`, `auth.login.success`, `auth.logout`, etc. |
| `WARN` | Falhas esperadas/tratadas — o app responde com 4xx, mas o evento importa | `auth.login.failed`, `auth.refresh.failed`, `user.register.conflict`, `http.*` |
| `ERROR` | Falhas inesperadas, 5xx, com stack trace | `http.internal_error`, `media.upload.rollback_failed` |

O nível padrão é `INFO`; logo, os eventos `DEBUG` só aparecem se o pacote for ajustado para `DEBUG` (ver [seção 6](#6-como-mudar-o-nível)).

## 4. Catálogo de eventos por domínio

### 4.1 Usuário — `user/logging/UserEventLog.java` (chamado por `UserServiceImpl`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `user.register.success` | INFO | `userId`, `username` | Usuário criado com sucesso (`POST /users/register`) |
| `user.register.conflict` | WARN | `field`, `username`* | Tentativa de registrar username/email já existentes (resposta 409) |
| `user.get.success` | INFO | `userId` | Busca de usuário por ID concluída (`GET /users/{id}`) |
| `user.not_found` | WARN | `userId` | Busca por ID não encontrou usuário (404) |
| `user.account.deleted` | INFO | `userId`, `username` | Soft delete da própria conta concluído (`DELETE /users/me`) |

\* Quando o conflito é de email, o valor do email **não** é logado (PII); apenas `field=email`.

### 4.2 Autenticação — `auth/logging/AuthEventLog.java` (chamado por `AuthenticationServiceImpl`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `auth.login.success` | INFO | `userId`, `username` | Credenciais válidas em `POST /authentication/login` |
| `auth.login.failed` | WARN | `username` | Usuário inexistente ou senha incorreta. Importante para detectar brute-force |

A senha nunca aparece em nenhum dos dois eventos.

### 4.3 Refresh token — `auth/logging/AuthEventLog.java` (chamado por `RefreshTokenServiceImpl`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `auth.refresh.created` | DEBUG | `userId` | Novo refresh token persistido (no login ou na rotação) |
| `auth.refresh.success` | INFO | `userId` | Refresh token rotacionado com sucesso (`POST /authentication/refresh`) |
| `auth.refresh.failed` | WARN | `userId`* | Token inexistente, expirado, revogado ou usuário não encontrado |
| `auth.logout` | INFO | `userId` | Refresh token revogado (`DELETE /authentication/logout`) |
| `user.account.deleted.tokens_revoked` | INFO | `userId` | Revogação em massa dos tokens ao excluir a conta |

\* O `userId` só é logado quando é possível identificá-lo; o valor do token nunca é logado.

### 4.4 Geração de JWT — `auth/logging/AuthEventLog.java` (chamado por `TokenServiceImpl`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `auth.jwt.generated` | DEBUG | `userId` | JWT emitido. O valor do token nunca é logado |

### 4.5 Usuário autenticado — `auth/logging/AuthEventLog.java` (chamado por `AuthenticatedUserProvider`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `auth.user.unauthenticated` | WARN | — | Operação que exige o usuário logado (ex.: `DELETE /users/me`, `POST /communities`) foi chamada sem autenticação |

### 4.6 Tratamento de erros HTTP — `shared/exception/GlobalExceptionHandler.java`

Emitidos quando uma requisição resulta em erro tratado. Todos carregam `event`, `status` e `path`. Permanecem no handler (helper privado), fora das classes `*EventLog`.

| Evento | Nível | Status | Quando ocorre |
|---|---|---|---|
| `http.resource_not_found` | WARN | 404 | Recurso/usuário/token não encontrado |
| `http.resource_invalid` | WARN | 422 | Dado inválido (ex.: credenciais incorretas chegadas ao handler) |
| `http.conflict` | WARN | 409 | Conflito (ex.: username/email já existem) |
| `http.unauthorized` | WARN | 401 | Sem autenticação ou credenciais inválidas |
| `http.validation_failed` | WARN | 400 | Payload não passa nas validações de bean (`@Valid`) |
| `http.malformed_body` | WARN | 400 | Corpo da requisição mal formado |
| `http.method_not_allowed` | WARN | 405 | Método HTTP não suportado na rota |
| `http.payload_too_large` | WARN | 413 | Upload acima de `spring.servlet.multipart.max-file-size` — o container rejeita antes do serviço, então este handler evita que a exceção caia no catch-all e vire 500 |
| `http.internal_error` | ERROR | 500 | Erro inesperado — inclui stack trace na causa |

### 4.7 Seeding de admin — `shared/logging/AdminEventLog.java` (chamado por `AdminUserConfig`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `admin.seed.skipped` | INFO | `username` | Usuário `admin` já existe na inicialização |
| `admin.seed.created` | INFO | `username` | Usuário `admin` criado na inicialização |

### 4.8 Mídia — `media/logging/MediaEventLog.java` (chamado por `S3ServiceImpl`)

Eventos da compensação do objeto no S3 quando a transação do banco não confirma, e da exclusão sem sincronização ativa. Quando aplicável, carregam `mediaId` e `objectKey` para que um órfão seja localizável sem o registro em `tb_media`.

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `media.upload.rollback_failed` | ERROR | `bucket`, `objectKey` | O `deleteObject` da compensação falhou (ex.: `AccessDenied`, rede). O objeto vira órfão e a causa está no log — este é o evento que justifica alerta |
| `media.upload.rollback_unknown` | WARN | `mediaId`, `objectKey` | A transação terminou com desfecho desconhecido (`STATUS_UNKNOWN`). O objeto é **mantido** de propósito: apagar quebraria uma referência possivelmente já confirmada |
| `media.upload.no_active_transaction` | DEBUG | `mediaId`, `objectKey` | O upload rodou sem transação ativa. O `tb_media` já foi commitado pela transação curta do repositório, então não há o que reverter e a limpeza volta a ser do chamador |
| `media.delete.no_active_transaction` | DEBUG | `mediaCount` | A exclusão de mídia rodou sem sincronização de transação ativa; os objetos no S3 são apagados imediatamente |

`media.upload.rollback_failed` é o único dos eventos de upload que indica defeito real de compensação — os outros são situações esperadas: uma por decisão de segurança (`rollback_unknown`), outra por ausência de transação (`no_active_transaction`).

### 4.9 Comunidade — `community/logging/CommunityEventLog.java` (chamado por `CommunityServiceImpl` e `CommunityPurgeJob`)

| Evento | Nível | Campos | Quando ocorre |
|---|---|---|---|
| `community.create.conflict` | WARN | `name`, `slug`, `deleted` | Tentativa de criar comunidade com name/slug já existentes (409). `deleted=true` quando o conflito é com comunidade soft-deleted |
| `community.media.forbidden` | WARN | `communityId`, `userId` | Usuário sem ownership/admin tenta alterar mídia ou gerenciar a comunidade |
| `community.delete.already_deleted` | DEBUG | `communityId`, `userId` | Soft delete idempotente: comunidade já estava marcada como deletada |
| `community.delete.success` | INFO | `communityId`, `userId`, `isOwner`, `mediaCount` | Soft delete concluído; `mediaCount` mídias retidas até o purge |
| `community.purge.lock_not_acquired` | DEBUG | `advisoryLockId` | Outra instância detém o advisory lock; o job de purge pula a execução |
| `community.purge.success` | INFO | `purgedCount`, `retentionDays` | Comunidades soft-deleted além da retenção foram purgadas fisicamente |
| `community.purge.nothing` | DEBUG | `retentionDays` | Lock adquirido, mas nenhuma comunidade elegível para purge |

## 5. Regras de dados sensíveis (resumo)

| Dado | Logado? |
|---|---|
| `userId`, `username` | Sim |
| `status`, `path`, `event`, `field` | Sim |
| `mediaId`, `bucket`, `objectKey` | Sim (nenhum deles é credencial) |
| Senha (seja em texto, hash ou erro) | **Nunca** |
| Refresh token / JWT (valor) | **Nunca** |
| `Authorization`/cookies | **Nunca** |
| Email | **Nunca** (apenas o campo é nomeado: `field=email`) |

## 6. Como mudar o nível

Para expor os eventos `DEBUG` (`auth.refresh.created`, `auth.jwt.generated`, `media.upload.no_active_transaction`, `media.delete.no_active_transaction`, eventos de purge/delete already_deleted), ajuste o nível do pacote em `application.yaml`:

```yaml
logging:
  level:
    com.motadev.clone_reddit: DEBUG
```

Mantenha `INFO` ou `WARN` em produção para evitar ruído e reduzir volume.

## 7. Para onde os logs vão

Os logs são escritos apenas no `stdout` da aplicação — **não são persistidos em banco de dados**. Em um ambiente com observabilidade, um coletor (ex.: Promtail/Fluent Bit) lê esse `stdout` e envia para um agregador (Loki, Elasticsearch, serviço em nuvem) e visualização (Grafana, Kibana). A configuração disso é infraestrutura, independente do código — a estruturação em ECS é justamente o que permite essa coleta e busca por `event`.
