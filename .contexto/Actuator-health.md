# Actuator health (PR1)

## Decisão

Expor só `health` com probes habilitados na mesma porta da API. `permitAll` restrito a `GET` em `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`. Detalhes de componentes não vão na resposta pública (`show-details: never`).

## Motivo

Deploy e orquestradores precisam de liveness/readiness HTTP. Sem Actuator + liberação no Security, o probe recebe 401.

## Evolução

- **PR1:** Actuator mínimo + security dos probes.
- **PR2:** grupos `liveness` = `livenessState`; `readiness` = `readinessState` + `db` (Rabbit/Mail fora do LB).
- **PR3:** `micrometer-registry-prometheus`, exposure de `prometheus`, `permitAll` no scrape e `management.server.port=8081` em prod (rede privada).

## Fora desta trilha (ainda)

Métricas de domínio, Dockerfile e app no compose. Ver [[Index]].

## Doc canônica

`docs/ops/actuator.md`
