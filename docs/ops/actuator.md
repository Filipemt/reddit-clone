# Actuator, health e métricas

API operacional do Spring Boot para probes de deploy e scrape de métricas. Não substitui os logs de negócio (ECS em `docs/logging/logs.md`).

## O que está ligado

| Item | Valor |
|---|---|
| Dependências | `spring-boot-starter-actuator`, `micrometer-registry-prometheus` |
| Endpoints expostos na web | `health`, `prometheus` |
| Probes | `/actuator/health/liveness` e `/actuator/health/readiness` |
| Detalhes do health | `show-details: never` (sem `components` na resposta pública) |
| Porta (dev/test) | mesma da API |
| Porta (prod) | `management.server.port=8081` (Actuator só nessa porta) |

Configuração em `application.yaml` e reforço em `application-prod.yaml`.

## Segurança

Rotas públicas (sem JWT), apenas `GET`:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`

Qualquer outro path sob `/actuator/**` continua autenticado. Endpoints não expostos (ex.: `/actuator/env`) não devem ser acessíveis anonimamente.

Em **produção**, o balanceador público deve publicar só a porta da API (8080). A porta `8081` fica na rede privada do cluster/VPC para probes e scrape do Prometheus.

## Uso nos probes

- **Liveness** — o processo ainda responde; falha tende a reinício do container.
- **Readiness** — pode receber tráfego; falha tira a instância do balanceador sem necessariamente reiniciar.

### Grupos

| Probe | Contributors | Efeito |
|---|---|---|
| `liveness` | só `livenessState` | Queda de Postgres/Rabbit/Mail **não** reinicia o processo |
| `readiness` | `readinessState` + `db` | Sem Postgres a instância sai do balanceador; Rabbit/Mail **não** tiram tráfego |

`/actuator/health` (agregado geral) continua podendo refletir outros indicators; o contrato de deploy são os paths de probe acima.

## Métricas (Prometheus)

- Scrape: `GET /actuator/prometheus`
- Em prod: preferir `host:8081` na rede interna
- Métricas JVM/HTTP vêm do Micrometer automaticamente; métricas de domínio ficam para um PR futuro

## Fora desta trilha (ainda)

- Métricas de domínio (`Counter`/`Timer` nos serviços)
- Dockerfile e serviço `app` no docker-compose
