# Actuator health (PR1)

## Decisão

Expor só `health` com probes habilitados na mesma porta da API. `permitAll` restrito a `GET` em `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`. Detalhes de componentes não vão na resposta pública (`show-details: never`).

## Motivo

Deploy e orquestradores precisam de liveness/readiness HTTP. Sem Actuator + liberação no Security, o probe recebe 401.

## Evolução

- **PR1:** Actuator mínimo + security dos probes.
- **PR2:** grupos `liveness` = `livenessState`; `readiness` = `readinessState` + `db` (Rabbit/Mail fora do LB).
- **PR3 (próximo):** Prometheus + porta management em prod.

## Fora desta trilha (ainda)

Dockerfile e app no compose. Ver [[Index]].

## Doc canônica

`docs/ops/actuator.md`
