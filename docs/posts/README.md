# Posts

## Create

`POST /communities/{communityId}/posts` (`multipart/form-data`)

- Part `data`: JSON `{ "title", "body?" }`
- Part `media` (optional): image upload via `MediaServiceI` under `posts/{postId}/...`
- Caller must be an **active member** of the community
- Community must be active (not soft-deleted)
- On success, enqueues `notification.post.created` in the outbox (recipient: community owner — consumed in the notification stage)

## Read

- `GET /communities/{communityId}/posts` — paginated, newest first
- `GET /posts/{postId}` — single active post (`404` if missing/removed)

## Soft delete

`DELETE /posts/{postId}` — author or `SCOPE_ADMIN`. Idempotent if already deleted. Media is retained on soft delete (row keeps `media_id`; S3 object stays).

## Media purge

Scheduled weekly, Sunday 03:00 America/Sao_Paulo (`cron`/`zone`), enabled by default. Uses Postgres advisory lock (`pg_try_advisory_lock`/`pg_advisory_unlock`) with a dedicated lock id. Selects soft-deleted posts with `deleted_at <= now - retentionDays` and `media_id IS NOT NULL` in batches. For each candidate: clears `media_id` on the post, then `deleteAfterCommit` removes the `tb_media` row and the S3 object after commit. Does **not** physically delete the post row (comments/votes FKs have no CASCADE). Controlled by `app.purge.posts.{retention-days,batch-size,enabled,advisory-lock-id,cron,zone}`.

See also [sorting.md](sorting.md).
