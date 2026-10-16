# Actuator health (PR1)

## Decisão

Expor só `health` com probes habilitados na mesma porta da API. `permitAll` restrito a `GET` em `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`. Detalhes de componentes não vão na resposta pública (`show-details: never`).

## Motivo

Deploy e orquestradores precisam de liveness/readiness HTTP. Sem Actuator + liberação no Security, o probe recebe 401.

## Fora deste PR

Porta management, Prometheus, grupos customizados de readiness (só DB), Dockerfile e app no compose. Ver [[Index]].

## Doc canônica

`docs/ops/actuator.md`
