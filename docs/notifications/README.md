# Notifications

Module inside the monolith. A `@RabbitListener` consumes `notification.events` and persists rows idempotently by `event_id`. Delivery (e.g. email worker) is a later stage; there is no inbox HTTP API.

## Persistence

`tb_notification` stores the pending/history record for each consumed event (`recipient_id`, `type`, `payload`). Read/unread is not tracked here (email clients own that when email delivery lands).

## Event types ingested

| Routing / type | Recipient field in payload |
|---|---|
| `notification.post.created` | `ownerId` |
| `notification.post.commented` | `recipientId` |
| `notification.comment.replied` | `recipientId` |
| `notification.vote.upvoted` | `recipientId` |
