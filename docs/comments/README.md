# Comments

Adjacency list (`parent_id`) with **unlimited depth**. The read API builds the tree in memory from all comments of a post (including soft-deleted placeholders).

## API

- `POST /posts/{postId}/comments` — body `{ "body", "parentId?" }`
- `GET /posts/{postId}/comments` — nested `replies[]`
- `DELETE /comments/{commentId}` — soft delete (author or `ADMIN`); response tree keeps the node with `deleted=true` and null body/author

## Notifications (outbox)

- Top-level comment → `notification.post.commented` to the post author (skipped if self)
- Reply → `notification.comment.replied` to the parent author (skipped if self)
