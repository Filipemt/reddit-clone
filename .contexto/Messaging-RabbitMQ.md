# Messaging-RabbitMQ

[[Index]] · [[Fluxo-Topology-Declare]]

## Broker local

- Compose: `docker/docker-compose.yml` serviço `rabbitmq` (`rabbitmq:4-management`)
- AMQP `5672`, Management UI `15672`, user/pass padrão `reddit`/`reddit`
- Doc: `docs/messaging/rabbitmq.md`

## Topologia

Declarada em `RabbitMqTopologyConfig` (beans Declarable):

| Recurso | Nome |
|---|---|
| TopicExchange | `clone-reddit.events` |
| Queue | `notification.events` (DLX → mesmo exchange) |
| Binding | `notification.#` |
| DLQ | `notification.events.dlq` (`notification.dlq`) |

## Declaração no broker

`RabbitAdmin` só declara na primeira conexão AMQP (lazy). Sem publisher/listener ativo, a UI fica só com exchanges padrão.

`RabbitMqTopologyReadyListener` no `ApplicationReadyEvent`:

1. `RabbitAdmin.initialize()`
2. `getQueueInfo` nas filas
3. Log `messaging.topology.ready` ou `messaging.topology.failed`

## Config

- Conexão: `spring.rabbitmq.*`
- Nomes: `app.rabbitmq.*`
