# Votes

`PUT /posts/{postId}/vote` and `PUT /comments/{commentId}/vote` with `{ "value": -1 | 0 | 1 }`.

- `1` upvote, `-1` downvote, `0` clears the vote
- Unique vote per `(user, target_type, target_id)`
- Target `score` / `up_count` / `down_count` updated atomically
- Author `karma` adjusted by the same score delta
- Outbox `notification.vote.upvoted` when a new upvote lands (not self)
