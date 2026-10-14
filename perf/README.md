# Performance scripts (k6)

Execução **local** contra a API no ar. Documentação completa: [docs/perf/k6-local.md](../docs/perf/k6-local.md).

```bash
k6 run -e BASE_URL=http://localhost:8080 perf/smoke.js
k6 run -e BASE_URL=http://localhost:8080 perf/load-read.js
```
