# Performance-k6

## Decisão

Adotar **k6** para smoke e carga HTTP **local** (`k6 run`), com scripts versionados em `perf/`. Grafana Cloud fica opcional só para visualizar resultado (`k6 run --out cloud`); execução na UI da Cloud exige API pública — fora de escopo enquanto tudo for localhost.

## Scripts

- `perf/smoke.js` — caminho feliz autenticado (register → vote)
- `perf/load-read.js` — rampa leve de leitura
- `perf/lib/helpers.js` — HTTP helpers

## Referência

- [[Index]]
- Doc: `docs/perf/k6-local.md`
