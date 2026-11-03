# Telas MVP (Twitter clássico)

Cada tela existe **3 vezes** ([[Temas-UI]]).

| # | Tela | Layout | Endpoints / nota |
|---|---|---|---|
| 1 | Login | split brand + form | `POST /authentication/login` |
| 2 | Register | split brand + form | `POST /users/register` |
| 3 | Home | sidebar + feed de posts | composto: `GET /communities/me` + posts |
| 4 | Communities | sidebar + lista | `GET /communities` · join |
| 5 | My Communities | sidebar + lista | `GET /communities/me` · leave |
| 6 | Community | sidebar + banner + posts | community + posts + membership |
| 7 | Create Community | sidebar + form | `POST /communities` multipart |
| 8 | Compose Post | sidebar + form | `POST /communities/{id}/posts` |
| 9 | Post Detail | sidebar + thread | post · comments · votes |
| 10 | Search | sidebar + tabs | `GET /search?q=` |
| 11 | Profile | sidebar + perfil + posts | `GET /users/{id}` · logout · delete me |

## Relacionados
- [[UI-Figma-MVP]] · [[Fluxos-API-Atual]]
