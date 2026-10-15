# Notification email delivery

Fluxo A1 de entrega por e-mail a partir de `tb_notification`.

## Relacionados

- [[Index]]
- Código: `notification` module
- Docs canônicas: `docs/notifications/README.md`, `docs/logging/logs.md`

## Fluxo

1. Domínio grava outbox → `OutboxPublisherJob` publica no RabbitMQ.
2. `NotificationConsumer` faz ingest idempotente → `delivery_status=PENDING`.
3. `NotificationEmailJob` (advisory lock + lotes) chama `NotificationDeliveryServiceI`.
4. Destinatário via `UserServiceI`; corpo via `NotificationEmailTemplateRendererI` (Thymeleaf); envio via `NotificationEmailSenderI`.
5. Sucesso → `SENT`. Falha SMTP → retry em `PENDING` até `max-attempts` → `FAILED`. Destinatário inválido → `FAILED` imediato.
6. Sem job de revive de `FAILED` (igual ao outbox).

## Ambientes

- Dev: Mailhog (`1025` / UI `8025`), delay 5 min (outbox e e-mail).
- Prod: SMTP por env, delay 60s.
- Profiles: `application.yaml` + `application-dev.yaml` + `application-prod.yaml`.
