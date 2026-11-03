# Telas MVP (Twitter clássico)

Cada tela autenticada existe **3 vezes** ([[Temas-UI]]).

## Navegação (decidido)
- **Menu esquerdo:** só `Home` e `Communities` (+ CTA New post + chip do usuário).
- **Busca:** caixa de texto no **header** de cada página autenticada — sem item “Search” no menu.
- **Perfil:** abre ao clicar no **chip do usuário** (avatar + @handle) no rodapé do menu — sem item “Profile” no menu.
- Tela de resultados de busca continua existindo como destino do header (`GET /search?q=`), sem entrada no rail.

| # | Tela | Layout | Endpoints / nota |
|---|---|---|---|
| 1 | Login | split brand + form | `POST /authentication/login` |
| 2 | Register | split brand + form | `POST /users/register` |
| 3 | Home | sidebar + header search + feed | composto: `GET /communities/me` + posts |
| 4 | Communities | sidebar + header search + lista | `GET /communities` · join |
| 5 | My Communities | sidebar + header search + lista | `GET /communities/me` · leave |
| 6 | Community | sidebar + header search + posts | community + posts + membership |
| 7 | Create Community | sidebar + header search + form | `POST /communities` multipart |
| 8 | Compose Post | sidebar + header search + form | `POST /communities/{id}/posts` |
| 9 | Post Detail | sidebar + header search + thread | post · comments · votes |
| 10 | Search Results | sidebar + header search + tabs | destino do header · `GET /search?q=` |
| 11 | Profile | sidebar + header search + perfil | via chip do usuário · `GET /users/{id}` · logout · delete me |

## Relacionados
- [[UI-Figma-MVP]] · [[Fluxos-API-Atual]]
