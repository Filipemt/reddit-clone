# Telas MVP (consumidor de API)

Cada tela existe **3 vezes** (um frame por tema em [[Temas-UI]]).

| # | Tela | Endpoints |
|---|---|---|
| 1 | Login | `POST /authentication/login` |
| 2 | Register | `POST /users/register` |
| 3 | Communities Home | `GET /communities` |
| 4 | My Communities | `GET /communities/me` |
| 5 | Community Detail | `GET /communities/{id\|slug}`, posts, membership |
| 6 | Create Community | `POST /communities` multipart |
| 7 | Create Post | `POST /communities/{id}/posts` |
| 8 | Post Detail | post, comments tree, votes, delete |
| 9 | Search | `GET /search?q=` |
| 10 | Profile / Account | `GET /users/{id}`, logout, delete me |

## Relacionados
- [[Fluxos-API-Atual]]
- [[UI-Figma-MVP]]
