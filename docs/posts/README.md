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

`DELETE /posts/{postId}` — author or `SCOPE_ADMIN`. Idempotent if already deleted. Media is retained (same approach as communities until a purge job exists).

See also [sorting.md](sorting.md).
