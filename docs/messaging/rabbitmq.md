# RabbitMQ integration

Broker used for asynchronous domain events (notifications and future consumers). Topology is declared in Java; nothing needs to be created manually in the Management UI for local development.

## Local broker

```bash
docker compose -f docker/docker-compose.yml up -d rabbitmq
```

| Item | Default |
|---|---|
| AMQP | `localhost:5672` |
| Management UI | http://localhost:15672 |
| User / password | `reddit` / `reddit` (override with `RABBITMQ_USER` / `RABBITMQ_PASSWORD`) |

Spring connects via `spring.rabbitmq.*` (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`).

## Topology (code)

Declared by `RabbitMqTopologyConfig` on application startup (`RabbitAdmin`):

| Resource | Name | Notes |
|---|---|---|
| Topic exchange | `clone-reddit.events` | Durable |
| Queue | `notification.events` | Durable; DLX back to the same exchange |
| Binding | `notification.#` | Routes notification.* events to the queue |
| DLQ | `notification.events.dlq` | Bound with `notification.dlq` |

Names are configurable under `app.rabbitmq.*` in `application.yaml`.

JSON payloads use `JacksonJsonMessageConverter`.

## Tests

`TestcontainersConfiguration` starts `RabbitMQContainer` with `@ServiceConnection`, so `@SpringBootTest` suites get a real broker without relying on the compose instance.

## Transactional outbox

Domain modules must not publish to RabbitMQ inside the request thread. They call `OutboxServiceI.enqueue(...)` in the same DB transaction as the business write. A scheduled `OutboxPublisherJob`:

1. Acquires a Postgres advisory lock (`app.outbox.publisher.advisory-lock-id`)
2. Loads `PENDING` rows in batches
3. Publishes an `OutboxMessageEnvelope` to `clone-reddit.events` with the event routing key
4. Marks the row `SENT`, or retries until `FAILED` after `max-attempts`

Publisher schedule: `app.outbox.publisher.fixed-delay-ms` / `initial-delay-ms`. Tests keep `enabled=false` by default and enable it only in outbox integration tests.
