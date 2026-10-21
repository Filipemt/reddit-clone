# Rate limit

Proteção contra abuso nos endpoints quentes da API, com contadores no **Redis** (janela fixa `INCR` + `EXPIRE`).

## Comportamento

- Configuração tipada em `app.rate-limit` (`application.yaml`).
- `RateLimitFilter` roda depois da autenticação; `/actuator/**` é ignorado.
- Chave `IP` nas rotas públicas de auth; chave `USER` (JWT `sub`) nas autenticadas.
- Resposta de estouro: **429**, body `ApiError` (sem campos novos), headers:
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`
  - `X-RateLimit-Reset` (epoch seconds)
  - `Retry-After` (segundos; no 429)
- Se o Redis falhar: **fail-open** (request passa) com log `ratelimit.store.failed`. Redis não entra no readiness do Actuator.

## Políticas atuais

| Política | Limite / janela | Endpoints | Chave |
|---|---|---|---|
| `auth-login` | 10 / 60s | `POST /authentication/login` | IP |
| `auth-refresh` | 30 / 60s | `POST /authentication/refresh` | IP |
| `auth-register` | 5 / 3600s | `POST /users/register` | IP |
| `vote` | 60 / 60s | `PUT /posts/*/vote`, `PUT /comments/*/vote` | USER |
| `post-create` | 10 / 3600s | `POST /communities/*/posts` | USER |
| `post-read` | 120 / 60s | `GET /communities/*/posts`, `GET /posts/*` | USER |
| `comment-create` | 30 / 60s | `POST /posts/*/comments` | USER |
| `comment-read` | 60 / 60s | `GET /posts/*/comments` | USER |
| `search` | 20 / 60s | `GET /search` | USER |

Valores calibráveis via YAML. O perfil `test` usa limites baixos (tipicamente 2/60s) para os testes de integração.

## Infra local

```bash
docker compose -f docker/docker-compose.yml up -d redis
```

Variáveis: `REDIS_HOST`, `REDIS_PORT` (dev/prod); em prod também `REDIS_PASSWORD`.

## Evolução futura

Bucket4j (token bucket mais suave) pode substituir o contador `INCR`+TTL sem mudar o contrato HTTP.
