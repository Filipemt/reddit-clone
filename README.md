# Clone Reddit

> Um projeto de aprendizado contínuo, construído do zero, com o objetivo de aplicar na prática — e não apenas em tutoriais isolados — todas as habilidades técnicas exigidas hoje no mercado: arquitetura de software, banco de dados, microsserviços, mensageria, cache, segurança, observabilidade, infraestrutura e CI/CD.

---

## 📌 Sobre o projeto

Este não é um projeto com prazo de entrega. É um **projeto vivo**, pensado para crescer indefinidamente — começando como um monólito simples e evoluindo, ao longo do tempo, para uma arquitetura distribuída completa (microsserviços, mensageria, múltiplos bancos de dados, observabilidade, infraestrutura como código).

A escolha do domínio (um clone simplificado do Reddit) não é acidental. A ideia central do "Projeto Impossível" é ter **regras de negócio propositalmente simples**, para que toda a energia e complexidade do projeto estejam concentradas em **arquitetura e infraestrutura**, e não em resolver problemas de domínio (como aconteceria, por exemplo, em um e-commerce real, cheio de regras fiscais, frete, estoque e pagamento).

### Por que um agregador de conteúdo (estilo Reddit)?

- Regra de negócio simples e intuitiva — não exige pesquisa de domínio complexo.
- Naturalmente rico em problemas técnicos reais: feed personalizado, comentários aninhados, sistema de votos em alta concorrência, notificações, busca, moderação.
- Permite migrar e comparar diferentes tipos de banco de dados (relacional, documento, chave-valor, busca) sem forçar nada artificialmente.
- Cresce organicamente: cada funcionalidade simples de produto abre, naturalmente, um problema técnico não trivial (ver seção [Habilidades vs. Funcionalidades](#-mapa-habilidades-de-mercado--funcionalidades)).

### Objetivos de aprendizado

Este projeto será o ambiente prático para estudar e aplicar, entre outros:

- Modelagem de dados relacional e não relacional
- Concorrência e condições de corrida (race conditions)
- Idempotência em sistemas distribuídos
- Cache (estratégias de invalidação, cache-aside, fan-out)
- Mensageria e processamento assíncrono
- Arquitetura de microsserviços (limites de serviço, comunicação síncrona/assíncrona)
- Resiliência (circuit breaker, retry, timeout, rate limiting)
- Segurança (autenticação, autorização contextual/RBAC)
- Observabilidade (logs, métricas, tracing distribuído)
- CI/CD e infraestrutura como código
- Padrões de projeto e boas práticas de arquitetura de software

---

## 🌐 O que é o Reddit (referência de domínio)

O Reddit é uma rede de **comunidades temáticas** (subreddits), fundada em 2005, cujo conceito central é permitir que qualquer pessoa crie uma comunidade sobre qualquer assunto. Dentro dessas comunidades, os usuários publicam conteúdo, comentam, votam, e o conteúdo mais relevante "sobe" através de um sistema de curadoria coletiva por votos — não por decisão editorial centralizada.

Principais características do produto original que servem de referência para este projeto:

- **Comunidades (subreddits):** contêiners temáticos, com moderação própria, regras e identidade.
- **Posts:** pertencem sempre a uma comunidade.
- **Comentários:** podem ter respostas aninhadas, em múltiplas camadas de profundidade.
- **Votos (upvote/downvote):** tanto posts quanto comentários podem ser votados.
- **Score:** calculado a partir dos votos, define o posicionamento do conteúdo.
- **Feed pessoal:** mistura posts das comunidades que o usuário segue.
- **Karma:** soma dos votos recebidos por um usuário em seus posts e comentários.
- **Moderação:** moderadores por comunidade podem remover conteúdo, banir usuários e fixar posts.
- **Notificações:** o usuário é avisado sobre respostas, upvotes e menções.
- **Busca:** de posts, comunidades e usuários.
- **Ordenação de conteúdo:** Hot (relevante agora), New (mais recente), Top (mais votado, com filtro de período).

> Visibilidade de conteúdo é, por padrão, pública — um usuário não precisa se inscrever em uma comunidade para visualizar seus posts. A inscrição serve apenas para personalizar o **feed pessoal**.

---

## 🧩 Domínio do projeto (MVP)

### Entidades candidatas

| Entidade | Observações | Situação |
|---|---|---|
| Usuário | Autenticação, perfil, karma | ✅ Modelada e implementada |
| Comunidade | Contêiner estrutural do post; possui moderadores e regras | 🟡 Criação, listagem, busca por id/slug, ícone/banner, soft delete e purge implementados; edição de dados não |
| Regra da comunidade | Regras ordenadas (`position`) dentro de uma comunidade | 🟡 Modelada; ainda não alimentada pelos endpoints |
| Mídia | Metadados de arquivos em object storage (bucket + object key) | ✅ Modelada e implementada |
| Refresh token | Sessão do usuário (rotação, revogação, uso único) | ✅ Modelada e implementada |
| Post | Pertence a exatamente uma comunidade | ⬜ Não iniciada |
| Comentário | Auto-relacionamento (resposta aninhada) | ⬜ Não iniciada |
| Voto | Entidade própria (não atributo) — associada a um usuário e a um alvo (post ou comentário) | ⬜ Não iniciada |
| Membership | Relação usuário ↔ comunidade (papel: membro ou moderador) | 🟡 Entrar, sair e listar implementados; promoção de moderadores não iniciada |
| Ban | Usuário banido de uma comunidade específica | ⬜ Não iniciada |
| Notificação | Evento direcionado a um usuário (resposta, menção, upvote) | ⬜ Não iniciada |
| Tag *(extensão de produto, fora do Reddit original)* | Mecanismo de descoberta transversal a comunidades — relação N:N com posts | ⬜ Não iniciada |

> **Karma** não será uma entidade própria: será tratado como um contador persistido no usuário, atualizado de forma incremental a cada evento de voto (decisão registrada na seção de [Decisões de Arquitetura](#-decisões-de-arquitetura)).

---

## 🗺️ Mapa: habilidades de mercado × funcionalidades

Cada funcionalidade do produto foi escolhida (ou vai naturalmente exigir) uma ou mais habilidades técnicas específicas:

| Funcionalidade | Habilidades treinadas | Situação |
|---|---|---|
| Sistema de votos | Concorrência, race conditions, operações atômicas, contadores distribuídos | ⬜ |
| Notificações | Idempotência, mensageria, circuit breaker, retry/backoff | ⬜ |
| Autenticação e autorização | Segurança, RBAC/autorização contextual (moderador só age na própria comunidade) | 🟡 Autenticação pronta; autorização por dono ou papel global `ADMIN`; RBAC contextual (moderador) não iniciado |
| Feed pessoal | Cache, fan-out on write/read, paginação por cursor | ⬜ |
| Comentários aninhados | Modelagem de dados em árvore, consistência estrutural | ⬜ |
| Busca | Indexação assíncrona, consistência eventual | ⬜ |
| Rate limit (votos, posts, comentários) | Proteção contra abuso, algoritmos de rate limiting | ⬜ |
| Comunicação entre serviços | Microsserviços, timeout, retry, service discovery | ⬜ |
| Processamento de mídia (upload) | Filas, processamento assíncrono, workers | 🟡 Upload síncrono direto no S3 feito; fila e worker não |
| Recalculo de Hot score | Paralelismo/threads, jobs periódicos | ⬜ |
| Transação distribuída (banco + object storage) | Consistência, compensação, outbox pattern | 🟡 Compensação no S3 implementada; outbox e GC de órfãos não |

---

## 🗄️ Estratégia de dados

O projeto não usará um único banco de dados — cada tipo de dado será alocado ao banco que melhor resolve seu problema específico, evoluindo ao longo das fases do projeto:

| Tipo de dado | Banco (candidato) | Motivo | Situação |
|---|---|---|---|
| Domínio central (usuário, comunidade, post, membership, ban, voto) | Relacional (PostgreSQL) | Integridade referencial, transações, relacionamento bem definido | 🟡 Parcial (usuário, comunidade, membership) |
| Comentários aninhados | Relacional (fase inicial) → avaliação futura de documento (MongoDB) ? | Começa simples (adjacency list); migração planejada como exercício de evolução de arquitetura | ⬜ |
| Cache de feed, contadores, rate limit | Chave-valor (Redis) | Leitura rápida, dados voláteis, alta frequência de acesso | ⬜ |
| Busca (posts, comunidades, usuários) | Motor de busca (Elasticsearch) ? | Busca textual otimizada, impraticável em SQL puro | ⬜ |
| Notificações | A avaliar (documento ou wide-column) | Alto volume de escrita, formato simples, pouco relacional | ⬜ |
| Log de eventos / auditoria / analytics | A avaliar (wide-column ou time-series) | Escrita massiva, dados imutáveis (append-only) | ⬜ |
| Imagens e vídeos | Object storage (S3) | Banco de dados não deve armazenar binários grandes; apenas a referência (UUID da mídia) é persistida no domínio | ✅ Em uso |

---

## 🏗️ Decisões de Arquitetura

> Registro vivo das decisões tomadas ao longo do projeto, incluindo o raciocínio por trás de cada uma. Cresce conforme o projeto avança.

### Karma do usuário
- **Decisão:** karma será um campo persistido (`tb_users.karma`, `INT`, hoje sempre `0` porque ainda não há votos), atualizado de forma **incremental** (`+1`/`-1`/delta) a cada evento de voto — nunca recalculado por completo a cada leitura.
- **Motivo:** recalcular a soma de todos os votos de todos os posts/comentários de um usuário a cada visita de perfil não escala. Karma muda com frequência, mas em pequenos incrementos — perfil ideal para contador incremental.
- **Ponto de atenção:** concorrência em escritas simultâneas (dois votos ao mesmo tempo no mesmo usuário) exige operação atômica no banco, não "ler → somar em memória → gravar".
- **Evolução futura:** job periódico de reconciliação, recalculando o valor real a partir dos dados brutos, como camada de correção/auditoria.

### Comunidade vs. Tag
- **Decisão:** todo post pertence obrigatoriamente a **uma** comunidade (relação estrutural 1:N, define moderação e governança). Tags são um mecanismo **opcional e adicional** (relação N:N), usado para descoberta de conteúdo entre comunidades diferentes.
- **Motivo:** comunidade resolve "onde o conteúdo mora e quem modera"; tag resolve "sobre o que o conteúdo é", cruzando comunidades.

### Comentários: relacional ou NoSQL?
- **Decisão:** iniciar em banco relacional (adjacency list), migrar para banco de documento apenas quando a dor de performance/consulta recursiva aparecer de fato.
- **Motivo:** migração de domínio entre bancos é, por si só, uma habilidade de mercado relevante — mais valiosa como aprendizado do que já nascer com a escolha "ideal".

### Armazenamento de mídia
- **Decisão:** arquivos (imagem/vídeo) vão para object storage (S3, via AWS SDK v2); o banco de dados guarda apenas a referência (`tb_media`: bucket + object key + content type + tamanho).
- **Motivo:** banco de dados não é otimizado para armazenar binários grandes; a API S3 permite o mesmo código em qualquer ambiente compatível.
- **Na prática:** a chave do objeto é `{pasta}/{uuid}{extensão}`, com pasta por comunidade (`communities/{communityId}/icon/...`, `communities/{communityId}/banner/...`). Por isso a comunidade é gravada antes do upload. O UUID evita colisão e não expõe o nome original do arquivo. A extensão vem de uma allowlist validada, nunca do nome cru do cliente.
- **Leitura:** URLs pré-assinadas (`S3Presigner`) com validade configurável em `AWS_S3_PRESIGNED_URL_SECONDS` — o domínio nunca persiste a URL, que expira.
- **Atomicidade:** o `putObject` não participa da transação do banco. A camada de mídia registra uma `TransactionSynchronization` a cada upload e, no `afterCompletion`, apaga o objeto se a transação não confirmar. O registro em `tb_media` é revertido junto com o restante do trabalho, então banco e bucket convergem — inclusive quando a falha só é detectada no commit. Uma segunda janela é coberta dentro do próprio `upload`: se o `save` da mídia falhar depois do `putObject`, o objeto é apagado imediatamente, já que sem o registro ele seria inalcançável para qualquer compensação.
- **Ponto de atenção:** se a JVM morrer entre o `putObject` e o commit, o objeto vira órfão e só um job de varredura (outbox pattern) poderia limpá-lo.

### Referências de comunidade: tabelas ou colunas de texto?
- **Decisão:** tipo, status e tópico de uma comunidade são **entidades de referência** (`tb_community_types`, `tb_community_status`, `tb_community_topics`) com identidade `bigint`, e não colunas `VARCHAR` com `CHECK`.
- **Motivo:** os conjuntos são fechados e versionáveis pelo domínio (`PUBLIC`/`PRIVATE`/`RESTRICTED`, `ACTIVE`/`ARCHIVED`/`BANNED`, 20 tópicos). Tabelas de referência permitem adicionar valores por migration — e escrever no banco — sem alterar o schema nem o código.
- **Detalhe:** as tabelas migraram de `uuid` para `bigint identity` porque são de leitura frequente, pequena cardinalidade e alto volume de `join`; a PK do domínio (`community_id`, `user_id`, `media_id`, `rule_id`) permanece `UUID`.

### Dono da comunidade: FK ou referência solta?
- **Decisão:** `tb_community.owner_id` guarda o `UUID` do usuário **sem** foreign key para `tb_users` (a FK original foi removida em `20260923_drop_community_owner_fk`).
- **Exceções no schema:** `tb_community.deleted_by` (`fk_community_deleted_by`) e `tb_community_membership.user_id` (`fk_membership_user`, `ON DELETE CASCADE`) **têm** FK para `tb_users`. O desacoplamento vale para o código — a entidade `Community` e a `CommunityMembership` guardam só o `UUID` e não mapeiam `User`.
- **Motivo:** evita dependência de ciclo entre os módulos `community` e `user` (o pacote `community` não conhece a entidade `User`) e mantém a entidade desacoplada. A integridade é garantida pela aplicação, que valida o usuário antes de criar a comunidade.
- **Evolução futura:** se a integridade referencial no banco passar a ser necessária, reintroduzir a FK é uma migration aditiva e isolada.

### Mídia como referência, não como coluna
- **Decisão:** `tb_community` guarda apenas `icon_media_id` e `banner_media_id` (UUID); a resposta da API carrega a URL pré-assinada sob demanda.
- **Motivo:** a mesma mídia pode ser referenciada por mais de uma entidade, e URLs pré-assinadas expiram — persisti-las no domínio geraria dados inválidos. A assinatura é feita no momento da leitura.

---

## 📁 Estrutura deste repositório

```
/docs
  /communities         → Ciclo de vida das comunidades (soft delete, purge) e inscrição (membership)
  /logging             → Convenções de logs estruturados (ECS) e catálogo de eventos
  /messaging           → RabbitMQ (topologia, compose, variáveis)
  /s3                  → Documentação da integração com object storage (upload, URL pré-assinada, IAM, CORS)
  /security            → Documentação de segurança (autenticação, JWT, chaves, etc.)
  /system-design       → Diagramas e decisões de arquitetura (C4, diagramas de serviço, etc.)
  /mer                 → Modelo Entidade-Relacionamento e modelagem de dados (ainda não criado)
  /adr                 → Architecture Decision Records (se adotado futuramente)
/docker                → docker-compose de desenvolvimento (PostgreSQL + RabbitMQ)
/src/main/java/com/motadev/clone_reddit
  /auth                → Autenticação (login, refresh, logout) e ciclo de vida da sessão
  /user                → Cadastro, perfil, papéis e exclusão de conta
  /community           → Comunidades, inscrições, regras e referências (tipo/status/tópico/papel de membro)
  /media               → Upload para object storage e URLs pré-assinadas
  /messaging           → Topologia RabbitMQ e (futuro) outbox/publisher
  /shared              → Configuração, tratamento global de exceções, segurança e extras transversais
README.md              → Este documento
```

> Estrutura sujeita a evoluir conforme o projeto avança (ex: pastas por serviço, quando a migração para microsserviços acontecer).

---

## 🛠️ Desenvolvimento local

**Pré-requisitos**
- Java 25
- Docker (para o PostgreSQL e o RabbitMQ)
- OpenSSL 3.x (para gerar as chaves JWT)
- Credenciais de um bucket S3 (ou de um serviço compatível com a API S3)

**1. Suba a infraestrutura local**

```bash
docker compose -f docker/docker-compose.yml up -d     # Postgres + RabbitMQ
docker compose -f docker/docker-compose.yml down -v   # para resetar volumes (recria o schema via Liquibase)
```

RabbitMQ sobe com Management UI em http://localhost:15672 (user/password padrão `reddit`/`reddit`). Detalhes em [docs/messaging/rabbitmq.md](docs/messaging/rabbitmq.md).

**2. Configure as variáveis de ambiente**

O módulo de mídia resolve as credenciais do S3 por variáveis de ambiente — sem elas a aplicação **não sobe**. Copie `.env.example` e preencha:

```bash
cp .env.example .env
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=...
export AWS_S3_BUCKET_NAME=...
export AWS_S3_PRESIGNED_URL_SECONDS=3600
```

Detalhes de criação do bucket, IAM e CORS em [docs/s3/s3-integration.md](docs/s3/s3-integration.md).

**3. Gere as chaves JWT**

O projeto assina e valida tokens com um par de chaves RSA. As chaves ficam em `src/main/resources/app.key` e `app.pub` e **não são versionadas** (estão no `.gitignore`). Gere-as antes do primeiro boot:

```bash
openssl genpkey -algorithm RSA -out src/main/resources/app.key -pkeyopt rsa_keygen_bits:2048
chmod 600 src/main/resources/app.key
openssl rsa -in src/main/resources/app.key -pubout -out src/main/resources/app.pub
```

Veja [docs/security/jwt-keys.md](docs/security/jwt-keys.md) para detalhes, testes e rotação de chaves.

**4. Suba a aplicação**

```bash
./mvnw spring-boot:run
```

O Liquibase aplica as migrations, seeda os papéis (`BASIC`, `ADMIN`), as referências de comunidade (tipos, status, 20 tópicos e os papéis de membro `MEMBER`/`MODERATOR`) e cria o schema de mídia. O `AdminUserConfig` cria o usuário admin inicial:

```
POST /authentication/login
{ "username": "admin", "password": "123" }
```

As credenciais acima são apenas para desenvolvimento local e estão fixas em `AdminUserConfig` (o usuário só é criado se ainda não existir).

**5. Testes**

```bash
./mvnw test
```

Os testes de integração sobem o PostgreSQL via Testcontainers e usam credenciais AWS falsas do perfil `test` (nenhum teste chama o S3). No macOS com Colima, o `pom.xml` já desabilita o resource-reaper (ryuk) — veja o comentário no `maven-surefire-plugin` —, mas o Testcontainers ainda precisa achar o socket do Docker:

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock ./mvnw test
```

---

## 🚧 Status atual

**Fase: autenticação completa + comunidades e inscrições + camada de mídia em S3 + infra RabbitMQ.**

Monólito modular em **Spring Boot 4.1.1 / Java 25**, organizado por domínio de negócio (`auth`, `user`, `community`, `media`, `messaging`, `shared`). Schema gerenciado por **Liquibase** (16 changelogs), **PostgreSQL** e **RabbitMQ** via Docker (dev) e Testcontainers (testes), e **AWS SDK v2** para object storage.

### O que já funciona

**Autenticação e sessão** (`auth`, `user`)
- **Access token** (JWT assinado com RSA, 5 min): validação de assinatura, expiração e **issuer**.
- **Refresh token** persistido (24 h): rotação, revogação e consumo de **uso único** (single-use).
- Anti-enumeração de usuários: usuário inexistente e senha errada retornam a mesma mensagem.
- Papéis seedados (`BASIC`, `ADMIN`), usuário admin inicial e claim `scope` já incluído no token.
- Exclusão de conta (`soft delete`) revoga **todos** os refresh tokens do usuário e, na mesma transação, desativa as inscrições dele em comunidades e remove (soft delete) as comunidades das quais é dono.
- **Logs estruturados ECS** com campo `event` nomeado por operação e nenhum dado sensível (ver [docs/logging/logs.md](docs/logging/logs.md)).

**Comunidades** (`community`)
- Modelo de domínio completo: `Community` (nome, slug, descrição, tópico, tipo, status, dono, timestamps) com **unicidade em `name` e `slug`**.
- Tabelas de referência seedadas: 3 tipos, 3 status e 20 tópicos; criação com `409 Conflict` em duplicidade.
- Dono extraído do JWT via `AuthenticatedUserProvider` — o serviço não recebe o usuário do controller.
- Entidade `CommunityRules` mapeada (`position`, título, descrição) com cascade/`orphanRemoval`; ainda sem endpoint.
- **Leitura**: listagem paginada (mais novas primeiro) e busca por id ou slug, sempre filtrando comunidades removidas; tópico e tipo carregados por `@EntityGraph` e URLs de mídia assinadas em lote.
- **Ícone e banner**: troca e remoção pelo dono ou por `ADMIN`; a mídia anterior é apagada só depois do commit.
- **Soft delete e purge**: a remoção marca `deleted_at`/`deleted_by` e reserva `name`/`slug`; um job semanal com advisory lock do PostgreSQL apaga fisicamente, em lotes, as comunidades removidas há mais de 30 dias e suas mídias. Ver [docs/communities/README.md](docs/communities/README.md).
- **Membership**: entrar e sair de comunidades de forma idempotente, com `member_count` alterado só por `UPDATE` atômico; o dono entra como moderador na criação; respostas trazem `memberCount` e `isMember`. A exclusão de conta desativa as inscrições e remove as comunidades do dono, via interface `UserAccountDeletionHandler` (sem ciclo entre `user` e `community`). Ver [docs/communities/membership.md](docs/communities/membership.md).

**Mídia** (`media`)
- `S3Client` para escrita e `S3Presigner` para leitura; `tb_media` guarda bucket, object key, content type e tamanho.
- Upload com chave `{pasta}/{uuid}{extensão}` e pasta por comunidade (`communities/{communityId}/icon|banner`).
- **URL pré-assinada** gerada sob demanda e devolvida na resposta (em lote nas listagens) — o domínio nunca persiste a URL.
- Exclusão de mídia em duas fases: a linha de `tb_media` sai na transação e o objeto do bucket só depois do commit (`deleteAfterCommit`).

**Mensageria** (`messaging`)
- RabbitMQ no compose; topologia (exchange `clone-reddit.events`, fila `notification.events` + DLQ) declarada em código via Spring AMQP.
- Converter JSON (`JacksonJsonMessageConverter`) registrado para payloads de eventos de domínio.
- **Transactional outbox**: `OutboxServiceI.enqueue` na mesma transação do domínio; `OutboxPublisherJob` publica pendências no RabbitMQ com advisory lock, retry e `FAILED`.
- Consumer de notificação ainda não implementado.

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/users/register` | Cadastro público |
| POST | `/authentication/login` | Emissão de access + refresh token |
| POST | `/authentication/refresh` | Rotação do refresh token (uso único) |
| DELETE | `/authentication/logout` | Revogação de um refresh token |
| GET | `/users/{userId}` | Perfil do usuário |
| DELETE | `/users/me` | Exclusão de conta (soft delete) |
| POST | `/communities` | Criação de comunidade (`multipart/form-data`, ícone e banner opcionais) |
| GET | `/communities` | Listagem paginada de comunidades ativas |
| GET | `/communities/{id}` | Detalhe por id (`404` se removida) |
| GET | `/communities/slug/{slug}` | Detalhe por slug (`404` se removida) |
| PUT | `/communities/{id}/icon` · `/banner` | Troca de ícone ou banner (`multipart/form-data`, parte `file`; dono ou `ADMIN`) |
| DELETE | `/communities/{id}/icon` · `/banner` | Remoção de ícone ou banner (dono ou `ADMIN`) |
| DELETE | `/communities/{id}` | Soft delete idempotente (dono ou `ADMIN`) |
| GET | `/communities/me` | Comunidades que o usuário segue, da inscrição mais recente para a mais antiga |
| PUT | `/communities/{id}/membership` | Entrar na comunidade (idempotente) |
| DELETE | `/communities/{id}/membership` | Sair da comunidade (idempotente) |

### Testes

Suíte unitária e de integração cobrindo a cadeia de filtros de segurança, as regras de validação do JWT, os fluxos E2E de autenticação, os serviços de usuário, mídia e comunidade, e a declaração da topologia RabbitMQ (Testcontainers + PostgreSQL e RabbitMQ reais).

### Dívidas e pontos de atenção conhecidos

- **Validação de arquivo crua:** o upload rejeita tamanho acima de `MEDIA_MAX_SIZE_BYTES` (5 MB) e combinações `content_type`/extensão fora das regras de `media.upload.types`, mas não inspeciona os *magic bytes* do arquivo — um JPEG renomeado de `.png` **e** declarado como `image/png` passa na validação. A checagem garante apenas que a declaração é *internamente consistente*, não que ela é verdadeira.
- **Rollback do S3 sem retry:** a compensação roda de forma síncrona no mesmo thread da requisição e sem retentativas. Se o `deleteObject` falhar, o erro fica no log (`media.upload.rollback_failed`) e o objeto vira órfão.
- **Sem job de purga de órfãos:** não há varredura periódica para localizar objetos no bucket sem registro em `tb_media` (outbox pattern).
- **Comunidade sem edição:** nome, descrição, tópico, tipo e status não mudam depois da criação; regras da comunidade, busca textual, posts e moderação ainda não foram implementados.
- **Exclusão de mídia sem retry nem auditoria:** o `deleteObject` após o commit também não tem retentativa; se falhar, o objeto vira órfão e só aparece em `media.upload.rollback_failed`.
- **`findUserOrThrow` não filtra `isActive`:** um usuário desativado ainda pode ser lido por id.
- **Sem job de purga de refresh tokens** expirados/revogados; sem revogação de access token antes do `exp`. O filtro de JWT também não verifica `isActive`: após excluir a conta, o usuário ainda age (por exemplo, entra em comunidades) até o token expirar.
- **Sem cache de URL pré-assinada:** uma nova URL é assinada a cada leitura.

### Próximos passos planejados

Edição e busca textual de comunidades, promoção de moderadores (RBAC contextual), posts, comentários aninhados e o sistema de votos — a parte do projeto que traz concorrência, cache e mensageria.

### Stack

Spring Boot 4.1.1 · Java 25 · Spring Data JPA · Spring Security (OAuth2 Resource Server) · Spring AMQP (RabbitMQ) · Liquibase · PostgreSQL · AWS SDK v2 (S3) · springdoc-openapi 3.1.0 · ECS structured logging · JUnit 5 + Mockito + Testcontainers

---

## 📚 Referências e documentação

- Estudo do funcionamento real do Reddit (produto, moderação, algoritmo de relevância) como base comparativa para as decisões deste projeto.
- Documentação interna, para ir a fundo em cada camada:
  - [docs/security/authentication.md](docs/security/authentication.md) — fluxo de autenticação, ciclo de vida da sessão e endpoints
  - [docs/security/jwt-keys.md](docs/security/jwt-keys.md) — geração, verificação e rotação das chaves
  - [docs/s3/s3-integration.md](docs/s3/s3-integration.md) — camada de mídia: upload, URL pré-assinada, IAM e CORS
  - [docs/logging/logs.md](docs/logging/logs.md) — convenções de logs estruturados e catálogo de eventos
  - [docs/messaging/rabbitmq.md](docs/messaging/rabbitmq.md) — broker, topologia declarativa e variáveis de ambiente
  - [docs/system-design/mvp-system-design.png](docs/system-design/mvp-system-design.png) — modelo do sistema do MVP

---

## 📝 Licença

Projeto pessoal de estudo. Sem fins comerciais.
