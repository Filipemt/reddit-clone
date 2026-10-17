# Actuator e health

API operacional do Spring Boot para probes de deploy. Não substitui os logs de negócio (ECS em `docs/logging/logs.md`).

## O que está ligado (PR1)

| Item | Valor |
|---|---|
| Dependência | `spring-boot-starter-actuator` |
| Endpoints expostos na web | só `health` |
| Probes | `/actuator/health/liveness` e `/actuator/health/readiness` |
| Detalhes do health | `show-details: never` (sem `components` na resposta pública) |
| Porta | mesma da API (sem `management.server.port` ainda) |

Configuração em `application.yaml` e reforço em `application-prod.yaml`.

## Segurança

Rotas públicas (sem JWT), apenas `GET`:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Qualquer outro path sob `/actuator/**` continua autenticado. Endpoints não expostos (ex.: `/actuator/env`) não devem ser acessíveis anonimamente.

## Uso nos probes

- **Liveness** — o processo ainda responde; falha tende a reinício do container.
- **Readiness** — pode receber tráfego; falha tira a instância do balanceador sem necessariamente reiniciar.

No corte atual, o readiness ainda usa o agregado padrão do Boot (pode incluir indicators automáticos como DB/Rabbit). O refinamento (liveness enxuto + readiness só Postgres) fica no PR seguinte.

## Fora deste corte

- Porta de management separada
- Prometheus / métricas scrapeáveis
- Métricas de domínio
- Dockerfile e serviço `app` no docker-compose
