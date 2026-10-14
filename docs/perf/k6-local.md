# Testes de performance locais com k6

Scripts HTTP de carga/smoke para a API Spring Boot, executados na máquina local com [k6](https://k6.io/). Não substituem MockMvc nem Testcontainers: medem latência, taxa de erro e comportamento sob VUs reais contra a API no ar.

## Pré-requisitos

1. PostgreSQL e RabbitMQ via `docker/docker-compose.yml`
2. Variáveis `AWS_*` exportadas (obrigatórias para o contexto Spring subir; os scripts smoke/load-read **não** enviam mídia ao S3)
3. Aplicação escutando em `BASE_URL` (padrão `http://localhost:8080`)
4. k6 instalado ([instalação oficial](https://grafana.com/docs/k6/latest/set-up/install-k6/)) ou imagem Docker `grafana/k6`

## Scripts

| Arquivo | Objetivo |
|---|---|
| `perf/smoke.js` | 1 VU / 1 iteração: register → login → criar comunidade (sem mídia) → criar post → listar → detalhe → votar |
| `perf/load-read.js` | Rampa leve (até 5 VUs): login + listagem de comunidades + get de post |
| `perf/lib/helpers.js` | Helpers compartilhados (HTTP, multipart `data` JSON) |

## Como rodar

```bash
# smoke
k6 run -e BASE_URL=http://localhost:8080 perf/smoke.js

# carga leve de leitura
k6 run -e BASE_URL=http://localhost:8080 perf/load-read.js
```

Com Docker (API no host):

```bash
docker run --rm -i --network host \
  -v "$PWD/perf:/scripts" \
  grafana/k6 run -e BASE_URL=http://localhost:8080 /scripts/smoke.js
```

## Grafana Cloud (opcional, só visualização)

Com tudo local, **não** use “Run” na UI do Grafana Cloud contra `localhost`. Para ver o relatório no `*.grafana.net` mantendo a execução na sua máquina:

```bash
k6 login cloud
k6 run --out cloud -e BASE_URL=http://localhost:8080 perf/smoke.js
```

## Observações

- Cada execução cria usuário/comunidade/post com sufixo único para evitar conflito de username/slug.
- `multipart/form-data` usa a parte `data` com JSON (`CreateCommunityRequestDTO` / `CreatePostRequestDTO`), sem arquivos.
- Thresholds do smoke são estritos (`http_req_failed < 1%`). O `load-read` tolera até 5% de falha e p95 &lt; 1s — ajuste conforme a máquina.
- Não versionar credenciais reais; os scripts geram usuários de teste.
