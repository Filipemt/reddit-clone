# Observabilidade local (Prometheus + Grafana)

Este guia cobre o scrape local das métricas expostas pelo Actuator e a visualização no Grafana. A instrumentação da app está em [actuator.md](actuator.md).

## Objetivo

Simular em miniatura o fluxo de produção:

```
App no host (:8080)
  → Prometheus (Docker, :9090) scrapea /actuator/prometheus
    → Grafana (Docker, :3000) lê o Prometheus e mostra dashboards
```

A API **continua** rodando com `./mvnw spring-boot:run` (fora do Docker). O compose sobe Postgres, RabbitMQ, Mailhog, Prometheus e Grafana.

## Pré-requisitos

1. Infra Docker:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```
2. App no host (profile `dev`), com `/actuator/prometheus` respondendo:
   ```bash
   ./mvnw spring-boot:run
   curl -s http://localhost:8080/actuator/prometheus | head
   ```

## Prometheus

| Item | Valor |
|---|---|
| UI | http://localhost:9090 |
| Targets | http://localhost:9090/targets |
| Config | `docker/prometheus/prometheus.yml` |
| Job | `clone-reddit` |
| Target | `host.docker.internal:8080` |
| Path | `/actuator/prometheus` |
| Intervalo | 15s |

O serviço Prometheus usa `extra_hosts: host.docker.internal:host-gateway` para alcançar a JVM no host a partir do container (Linux incluso).

### Validar scrape

1. Confirme que a app está no ar e o endpoint responde.
2. Abra http://localhost:9090/targets — o target `clone-reddit` deve ficar **UP**.
3. Em http://localhost:9090/graph, consulte por exemplo `jvm_memory_used_bytes`.

Se o target ficar **DOWN**:

- a app não está rodando na porta 8080;
- o endpoint não está exposto (branch sem Actuator Prometheus);
- em Docker muito antigo, `host-gateway` pode falhar — use o IP da bridge do host como fallback no `prometheus.yml`.

## Grafana

| Item | Valor |
|---|---|
| UI | http://localhost:3000 |
| Usuário / senha | `admin` / `admin` (**só para dev local**) |
| Datasource | Prometheus → `http://host.docker.internal:9090` (provisionado; porta publicada do host) |
| Dashboard | **Clone Reddit — JVM / Micrometer** (pasta *Clone Reddit*) |

Arquivos:

- `docker/grafana/provisioning/datasources/prometheus.yml`
- `docker/grafana/provisioning/dashboards/dashboards.yml`
- `docker/grafana/dashboards/jvm-micrometer.json`

O datasource usa `host.docker.internal:9090` (com `extra_hosts` no serviço Grafana) para alcançar o Prometheus pela porta publicada no host. Isso evita depender de DNS container-a-container em redes Docker restritas.

### Validar dashboard

1. Faça login em http://localhost:3000 (`admin` / `admin`).
2. Abra o dashboard provisionado (Dashboards → Clone Reddit).
3. Gere um pouco de tráfego na API (`curl` em health ou rotas públicas) e aguarde ~15–30s.
4. Os painéis de memória/CPU/threads e HTTP devem mostrar séries.

## Produção (contraste)

Em prod o Actuator sobe na porta **8081** (rede privada). O scrape local usa **8080** porque, no profile `dev`, management e API compartilham a mesma porta.

## Fora deste corte

- App dentro do Docker
- Alertas, Loki, tracing
- Métricas de domínio no Java
