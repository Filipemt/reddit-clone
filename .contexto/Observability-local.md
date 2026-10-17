# Observabilidade local

## Decisão

Subir Prometheus no `docker-compose` para scrapear a API no host (`host.docker.internal:8080/actuator/prometheus`), sem colocar a app no Docker.

## Motivo

A app já expõe métricas via Actuator/Micrometer. Sem um servidor Prometheus local não há histórico nem familiaridade com targets/scrape antes do deploy.

## Estado

- **PR1:** Prometheus no compose + doc `docs/ops/local-observability.md`.
- **PR2:** Grafana (`admin`/`admin` local) + datasource Prometheus + dashboard JVM/Micrometer provisionado.

Ver [[Actuator-health]] e [[Index]].
