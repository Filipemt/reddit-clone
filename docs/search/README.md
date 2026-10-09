# Search (ILIKE baseline)

`GET /search?q=term`

Intentionally uses PostgreSQL `ILIKE '%term%'` on:

- posts: `title`, `body`
- communities: `name`, `slug`, `description`
- users: `username`

This is the slow/leading-wildcard baseline. A later change should compare against `tsvector` / full-text search without changing the API contract.
