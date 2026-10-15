# Notifications

Module inside the monolith. A `@RabbitListener` consumes `notification.events` and persists rows idempotently by `event_id`. An email delivery worker then sends HTML mail for `PENDING` rows. There is no inbox HTTP API.

## Persistence

`tb_notification` stores the pending/history record for each consumed event (`recipient_id`, `type`, `payload`) plus delivery tracking:

| Column | Meaning |
|---|---|
| `delivery_status` | `PENDING` → `SENT` or `FAILED` |
| `attempts` | SMTP attempts while status is `PENDING` |
| `sent_at` | Set when status becomes `SENT` |
| `last_error` | Truncated error or recipient failure reason |

Read/unread is not tracked here (email clients own that).

## Event types ingested

| Routing / type | Recipient field in payload |
|---|---|
| `notification.post.created` | `ownerId` |
| `notification.post.commented` | `recipientId` |
| `notification.comment.replied` | `recipientId` |
| `notification.vote.upvoted` | `recipientId` |

## Email delivery (A1)

`NotificationEmailJob` (`@Scheduled` + PostgreSQL advisory lock) loads `PENDING` batches and delegates to `NotificationDeliveryServiceI`:

1. Resolve recipient via `UserServiceI` (never the user repository).
2. Render Thymeleaf HTML under `templates/mail/` by `type`.
3. Send through `NotificationEmailSenderI` (`JavaMailSender`) with fixed `From` from `app.mail.from`.
4. On SMTP failure: stay `PENDING` and increment `attempts` until `max-attempts`, then `FAILED`.
5. Missing recipient or blank email: `FAILED` immediately with log `notification.email.recipient_invalid` (`reason` without email PII).
6. `FAILED` is terminal (no revive job); same semantics as the outbox publisher.

### Local (Mailhog)

Mailhog in `docker/docker-compose.yml` accepts SMTP on `1025` and shows captured messages at http://localhost:8025. It does not deliver to real mailboxes.

```bash
docker compose -f docker/docker-compose.yml up -d mailhog
```

### Configuration

| Profile | Email / outbox `fixed-delay-ms` | SMTP |
|---|---|---|
| `dev` (default) | `300000` (5 min) | Mailhog (`MAIL_HOST`/`MAIL_PORT`, defaults `localhost`/`1025`) |
| `prod` | `60000` (60s) | Real provider via env |
| `test` | job `enabled: false` | stub host |

Environment variables: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `SPRING_PROFILES_ACTIVE`.

Abstractions ports for a future architecture split: `NotificationEmailSenderI`, `NotificationEmailTemplateRendererI`, `NotificationDeliveryServiceI`.
