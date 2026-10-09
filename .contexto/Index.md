# Clone Reddit — Index (Obsidian Vault)

Fonte única de verdade operacional deste workspace para o agente. Visão de produto e arquitetura canônicas continuam em [[README]] (`README.md`) e `docs/`.

## Domínios

- [[Messaging-RabbitMQ]] — broker, topologia declarativa, outbox/consumers
- [[Auth-Security]] — JWT, refresh, AuthenticatedUserProvider
- [[Community]] — comunidades, membership, soft delete / purge
- [[Media-S3]] — upload, compensação, object storage

## Fluxos macro

- [[Fluxo-Topology-Declare]] — declaração da topologia no broker no ApplicationReady

## Entidades de mensageria

- Exchange `clone-reddit.events` (topic)
- Queue `notification.events` + DLQ `notification.events.dlq`
