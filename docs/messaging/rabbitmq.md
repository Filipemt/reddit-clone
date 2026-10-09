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

Declarada por `RabbitMqTopologyConfig` na subida da aplicação (`RabbitAdmin`):

| Recurso | Nome | Observações |
|---|---|---|
| Topic exchange | `clone-reddit.events` | Durável |
| Fila | `notification.events` | Durável; DLX de volta para o mesmo exchange |
| Binding | `notification.#` | Encaminha eventos `notification.*` para a fila |
| DLQ | `notification.events.dlq` | Ligada com a routing key `notification.dlq` |

Os nomes são configuráveis em `app.rabbitmq.*` no `application.yaml`.

Os payloads JSON usam `JacksonJsonMessageConverter`.

## Testes

`TestcontainersConfiguration` sobe um `RabbitMQContainer` com `@ServiceConnection`, então as suítes `@SpringBootTest` usam um broker real sem depender da instância do compose.
