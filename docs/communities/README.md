# Communities lifecycle (summary)

- **Create**: POST /communities (multipart) creates active community. Icon/banner saved under `communities/{communityId}/icon/{uuid}.{ext}` and `communities/{communityId}/banner/{uuid}.{ext}`.
- **List**: GET /communities (offset, default 20, max 50). Only active communities (deletedAt IS NULL). Topic/type loaded via EntityGraph; media URLs fetched in batch.
- **Get**: GET /communities/{id} or GET /communities/slug/{slug}. 404 if not found or removed.
- **Update media**: PUT /communities/{id}/icon, PUT /communities/{id}/banner (multipart) — owner or SCOPE_ADMIN. Old object deleted after commit.
- **Remove media**: DELETE /communities/{id}/icon, DELETE /communities/{id}/banner — owner or SCOPE_ADMIN. Old object deleted after commit.
- **Soft delete**: DELETE /communities/{id} — owner or SCOPE_ADMIN. Marks `deleted_at`/`deleted_by`, keeps row, reserves `name`/`slug` (30d retention). Returns 204 idempotently. Logs `community.delete.success` with `isOwner`, `mediaCount`. `community.create.conflict` includes `deleted=true` if the slug/name belongs to a removed community (still generic 409). Media objects deleted after commit.
- **Purge**: Scheduled (60s delay), enabled by default. Uses Postgres advisory lock (`pg_try_advisory_lock`/`pg_advisory_unlock`) to avoid cross-instance overlap. Deletes communities with `deleted_at <= now - retentionDays` in batches (size configurable). For each candidate, S3 objects removed after commit, then row deleted. Controlled by `app.purge.communities.{retention-days,batch-size,enabled,advisory-lock-id}`.

## Migration notes

- Added columns `deleted_at` (TIMESTAMP), `deleted_by` (UUID FK -> tb_users).
- Partial indexes: `idx_community_active_created_at` on (created_at DESC) WHERE deleted_at IS NULL; `idx_community_deleted_at` on (deleted_at) WHERE deleted_at IS NOT NULL.

## Media semantics

- S3 keys include `communityId`. Never upload before the community row exists.
- S3 writes happen before DB commit only for uploads; deletions/removals/old-asset cleanup always happen after commit (deleteAfterCommit). On upload failure before/after save, object deleted (or kept only on TRANSACTION_UNKNOWN with warning).
