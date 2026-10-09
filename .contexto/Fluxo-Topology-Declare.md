# Fluxo-Topology-Declare

[[Index]] · [[Messaging-RabbitMQ]]

1. Spring registra beans Declarable (`TopicExchange`, `Queue`, `Binding`).
2. `ApplicationReadyEvent` dispara `RabbitMqTopologyReadyListener`.
3. Listener chama `RabbitAdmin.initialize()` (abre conexão e declara no broker).
4. Verifica filas com `AmqpAdmin.getQueueInfo`.
5. Sucesso → `messaging.topology.ready`. Falha → `messaging.topology.failed` + exceção.

## Sintoma histórico

Log `messaging.topology.ready` existia sem declaração real (só ecoava nomes das properties). Management UI: No queues / só exchanges `amq.*`.
