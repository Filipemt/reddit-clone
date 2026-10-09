# Integração com RabbitMQ

Broker usado para eventos de domínio assíncronos (notificações e consumidores futuros). A topologia é declarada em Java; no desenvolvimento local não é necessário criar nada manualmente na Management UI.

## Broker local

```bash
docker compose -f docker/docker-compose.yml up -d rabbitmq
```

| Item | Padrão |
|---|---|
| AMQP | `localhost:5672` |
| Management UI | http://localhost:15672 |
| Usuário / senha | `reddit` / `reddit` (sobrescreva com `RABBITMQ_USER` / `RABBITMQ_PASSWORD`) |

O Spring conecta via `spring.rabbitmq.*` (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`).

## Topologia (código)

Declarada por `RabbitMqTopologyConfig`. No `ApplicationReadyEvent`, `RabbitMqTopologyReadyListener` chama `RabbitAdmin.initialize()`, verifica as filas no broker e só então emite `messaging.topology.ready`. Sem essa chamada explícita, o admin só declara na primeira conexão AMQP (lazy); com a app idle (sem publisher/listener ativo), a Management UI fica só com os exchanges padrão e sem filas.

| Recurso | Nome | Observações |
|---|---|---|
| Topic exchange | `clone-reddit.events` | Durável |
| Fila | `notification.events` | Durável; DLX de volta para o mesmo exchange |
| Binding | `notification.#` | Encaminha eventos `notification.*` para a fila |
| DLQ | `notification.events.dlq` | Ligada com a routing key `notification.dlq` |

Os nomes são configuráveis em `app.rabbitmq.*` no `application.yaml`.

Na Management UI (vhost `/`), após a app subir com sucesso você deve ver `clone-reddit.events`, `notification.events` e `notification.events.dlq`. O evento `messaging.topology.ready` significa declaração verificada no broker — não só registro de beans Spring.

Os payloads JSON usam `JacksonJsonMessageConverter`.

## Testes

`TestcontainersConfiguration` sobe um `RabbitMQContainer` com `@ServiceConnection`, então as suítes `@SpringBootTest` usam um broker real sem depender da instância do compose.
