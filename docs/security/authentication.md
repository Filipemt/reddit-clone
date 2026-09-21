# Camada de Autenticação

Este documento descreve a arquitetura e o comportamento da camada de autenticação do clone-reddit: os endpoints, o fluxo de sessão (login, refresh, logout), a estrutura dos tokens JWT, o modelo de dados do refresh token e as decisões de segurança aplicadas.

## 1. Visão geral

A aplicação usa **JWTs assimétricos (RSA)** como access tokens e **refresh tokens rotativos persistidos no banco**. O servidor de recursos é o *OAuth2 Resource Server* do Spring Security, com validação de assinatura, expiração e `issuer`.

Fluxo de alto nível:

```
POST /users/register          ──► cria usuário (senha BCrypt, role BASIC)
                                                     │
POST /authentication/login    ──► valida credenciais ─┴─► emite JWT (5 min) + refresh token (24 h)
                                                     │
POST /authentication/refresh ──► rotaciona refresh token (single-use) ─► emite novo JWT + novo refresh token
                                                     │
DELETE /authentication/logout ──► revoga o refresh token
```

Sessão é **stateless**: o servidor não mantém estado de autenticação em memória (nem sessa), apenas a validade dos refresh tokens no banco.

## 2. Arquitetura

A camada vive em `src/main/java/com/motadev/clone_reddit/auth/` e se conecta ao domínio de usuário em `user/`.

```
AuthenticationController  (REST, /authentication)
        │
        ├──► AuthenticationServiceI ──► AuthenticationServiceImpl
        │        ├── UserServiceI.validateCredentials()          (user/domain)
        │        ├── RefreshTokenServiceI.createRefreshToken()
        │        └── TokenServiceI.generateToken()
        │
        ├──► RefreshTokenServiceI ──► RefreshTokenServiceImpl
        │        ├── RefreshTokenRepository (JPA)
        │        ├── TokenServiceI.generateToken()
        │        └── UserServiceI.findAuthInfoById()             (user/domain)
        │
SecurityConfig (jwtEncoder / jwtDecoder / BCrypt / SecurityFilterChain)
```

Responsabilidades de cada componente:

| Componente | Responsabilidade |
|---|---|
| `SecurityConfig` | Define a cadeia de filtros, rotas públicas, encoder/decoder JWT e o `BCryptPasswordEncoder` |
| `AuthenticationController` | Expõe os endpoints de login, refresh e logout |
| `AuthenticationServiceImpl` | Orquestra o login: valida credenciais, cria refresh token e gera o par de tokens |
| `TokenServiceImpl` | Constrói e assina o JWT com os claims `iss`, `sub`, `iat`, `exp`, `scope` |
| `RefreshTokenServiceImpl` | Gerencia o ciclo de vida do refresh token: criação, rotação, verificação e revogação |
| `RefreshTokenRepository` | Persistência do refresh token (`findByToken`, `revokeAllByUserId`) |
| `UserService` | Valida credenciais e fornece `UserAuthInfo(userId, roles)` |

## 3. JWT (access token)

### 3.1 Estrutura

O JWT é produzido por `TokenServiceImpl` via `JwtEncoder` (`NimbusJwtEncoder`, assinatura RSA). Claims emitidas:

| Claim | Valor |
|---|---|
| `iss` | `jwt.issuer` (`backend-reddit-clone`) |
| `sub` | `userId` do usuário (UUID) |
| `iat` | momento da emissão |
| `exp` | `iat + jwt.expiresIn` (300 s / 5 min) |
| `scope` | roles do usuário ordenadas, separadas por espaço (ex.: `"ADMIN BASIC"`) |

**Obs.:** o `scope` reflete as roles no momento do *login/refresh*; alterações de role só têm efeito na próxima emissão de token.

### 3.2 Validação no lado do servidor

`SecurityConfig.jwtDecoder()` cria um `NimbusJwtDecoder` com a chave pública e aplica `JwtValidators.createDefaultWithIssuer(issuer)` — toda requisição autenticada tem a assinatura, a expiração e o `issuer` verificados.

### 3.3 Chaves e TTLs

| Propriedade | Valor padrão | Significado |
|---|---|---|
| `jwt.public.key` | `classpath:app.pub` | Chave pública — valida assinatura |
| `jwt.private.key` | `classpath:app.key` | Chave privada — assina os tokens (não versionada) |
| `jwt.issuer` | `backend-reddit-clone` | `iss` validado no decode |
| `jwt.expiresIn` | `300` (s) | Validade do access token |
| `jwt.refreshExpirationMs` | `86400000` (24 h) | Validade do refresh token |

Geração e rotação de chaves: ver [docs/security/jwt-keys.md](jwt-keys.md).

## 4. Ciclo de vida da sessão

### 4.1 Login — `POST /authentication/login`

`AuthenticationServiceImpl.authenticate()`:

1. `UserService.validateCredentials(username, password)` — busca usuário **ativo** por username e confere o hash BCrypt.
2. Falha → `ResourceInvalidException("User or Password Invalid.")` (resposta **422**).
3. Sucesso → cria um refresh token e gera o par de tokens.

### 4.2 Refresh — `POST /authentication/refresh`

`RefreshTokenServiceImpl.refresh()` implementa **rotação com uso único (single-use)**:

1. Verifica o token: deve existir, não estar revogado e não estar expirado; senão → 404/422 e nenhum novo token é emitido.
2. **Revoga o token antigo** antes de emitir o novo — um replay do mesmo token falha.
3. Rebusca o `UserAuthInfo` do usuário (falha se o usuário foi excluído).
4. Cria um novo refresh token e devolve um novo par (JWT + refresh token).

### 4.3 Logout — `DELETE /authentication/logout`

`RefreshTokenServiceImpl.revoke()` marca o token como `revoked = true`. Revogar um token inexistente é idempotente (não gera erro).

### 4.4 Exclusão de conta — `DELETE /users/me`

`UserServiceImpl.softDeleteMyAccount()` desativa o usuário e chama `RefreshTokenServiceImpl.revokeAllByUserId()` — revoga **todos** os refresh tokens ativos do usuário, invalidando sessões antigas.

> **Nota:** um token que já passou pela rotação tem o antigo revogado; apenas o token mais recente permanece válido.

## 5. Endpoints

`SecurityConfig` libera apenas as rotas de autenticação e registro; tudo o mais exige `Authorization: Bearer <JWT>`.

| Método | Rota | Autenticação | Validações do payload | Status de sucesso |
|---|---|---|---|---|
| POST | `/users/register` | pública | `username`, `email`, senha 8–72 chars (`@Valid`) | 201 |
| POST | `/authentication/login` | pública | `username` não vazio; senha 8–72 chars | 200 |
| POST | `/authentication/refresh` | pública | `refreshToken` não vazio | 200 |
| DELETE | `/authentication/logout` | pública* | `tokenValue` não vazio | 200 |
| GET | `/users/{userId}` | JWT | — | 200 |
| DELETE | `/users/me` | JWT | — | 204 |

Payload de resposta dos endpoints de token (`TokenData`):

```json
{
  "accessToken": "<jwt>",
  "expiresIn": 300,
  "refreshToken": "<refresh token>"
}
```

(*) Logout permanece público porque o refresh token só é válido se existir/estar ativo no banco; a revogação é idempotente e não expõe dados.

Códigos de erro mais comuns na camada (ver `GlobalExceptionHandler`):

| Status | Quando |
|---|---|
| 400 | Payload inválido / corpo mal formado |
| 404 | Refresh token inexistente, usuário não encontrado |
| 422 | Credenciais inválidas, refresh token expirado ou revogado |
| 401 | Requisição autenticada sem token JWT válido |

## 6. Modelo de dados

Tabela `tb_refresh_token` (migration Liquibase `20261009_create_refresh_token_table.xml`):

| Coluna | Tipo | Restrições |
|---|---|---|
| `refresh_token_id` | UUID | PK, gerada pela aplicação |
| `token` | VARCHAR(255) | `NOT NULL`, **UNIQUE** (valor UUID gerado em `RefreshTokenServiceImpl`) |
| `user_id` | UUID | FK → `tb_users.user_id`, indexada (`idx_refresh_token_user`) |
| `expiry_date` | TIMESTAMPTZ | expiração do token |
| `revoked` | BOOLEAN | `NOT NULL`, default `false` |

Não há FK cíclica em memória: `RefreshToken` guarda apenas o `userId` (sem `@ManyToOne`), o que mantém o desacoplamento entre os pacotes `auth` e `user`.

## 7. Segurança

- **Senhas**: hash `BCryptPasswordEncoder`; senha em texto só existe na requisição de login (normas 8–72 chars, limite do BCrypt).
- **Anti-enumeração de usuários**: usuário inexistente e senha errada retornam a **mesma** mensagem (`"User or Password Invalid."`), evitando descobrir usernames válidos.
- **Uso único do refresh token**: rotação revoga o token anterior antes de emitir o novo.
- **Tokens inválidos não emitem novos tokens**: fluxo de refresh não prossegue se a verificação falhar.
- **Sem dados sensíveis em logs**: senha, valores de JWT/refresh token e emails nunca são logados. Catálogo completo de eventos de log em [docs/logging/logs.md](../logging/logs.md).
- **Chave privada fora do versionamento**: `app.key` está no `.gitignore`; ver [docs/security/jwt-keys.md](jwt-keys.md).

## 8. Configuração

Propriedades relevantes em `src/main/resources/application.yaml`:

```yaml
jwt:
  public:
    key: classpath:app.pub
  private:
    key: classpath:app.key
  expiresIn: 300
  refreshExpirationMs: 86400000
  issuer: backend-reddit-clone
```

Para diminuir o impacto de uma rotação de chaves ou de vazamento, reduza `jwt.expiresIn`; para encurtar sessões, reduza `jwt.refreshExpirationMs`.

## 9. Pontos para evolução

- **Logout de todos os dispositivos**: hoje existe `revoke` (um token) e `revokeAllByUserId` (todos ao excluir conta), mas não há endpoint dedicado de logout global.
- **Limpeza de tokens expirados**: a tabela `tb_refresh_token` acumula tokens revogados/expirados; não há job de purga.
- **Revogação de access token**: o JWT não é invalidável antes da expiração — o `exp` curto (5 min) mitiga isso.
- **Detecção de replay**: tentativas com token já revogado retornam erro 422 e geram `auth.refresh.failed` no log, útil para monitorar ataques (ver `logs.md`).