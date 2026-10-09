# Post sorting

`GET /communities/{id}/posts?sort=new|hot|top&period=day|week|month|year|all`

| sort | Behavior |
|---|---|
| `new` (default) | `created_at DESC` |
| `hot` | `hot_score DESC` then `created_at DESC` |
| `top` | `score DESC` then `created_at DESC`, filtered by `period` |

`hot_score` uses a Reddit-like formula (`sign(score) * log10(max(|score|,1)) + epochSeconds/45000`) and is refreshed on create and on each vote.
