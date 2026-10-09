# Notifications

Module inside the monolith. A `@RabbitListener` consumes `notification.events` and persists inbox rows idempotently by `event_id`.

## API

- `GET /notifications` — current user inbox (newest first)
- `PUT /notifications/{id}/read` — mark as read (`204`)

## Event types ingested

| Routing / type | Recipient field in payload |
|---|---|
| `notification.post.created` | `ownerId` |
| `notification.post.commented` | `recipientId` |
| `notification.comment.replied` | `recipientId` |
| `notification.vote.upvoted` | `recipientId` |
