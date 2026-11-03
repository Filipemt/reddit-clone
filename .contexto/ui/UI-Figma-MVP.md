# UI Figma MVP

Arquivo: [Clone Reddit — UI Flows (MVP)](https://www.figma.com/design/EqrowqaqF3pemBVsxAXHqx)  
`fileKey`: `EqrowqaqF3pemBVsxAXHqx`

## Objetivo
UI no estilo **Twitter clássico** (sidebar esquerda + coluna central + trilho direito) para consumir os endpoints atuais.

## Layout autenticado
- **Esquerda:** logo, Home, Communities, CTA New post, chip do usuário (abre Profile)
- **Centro:** header com caixa de busca + timeline / formulário / detalhe
- **Direita:** “Communities for you” (sem duplicar a busca — ela vive no header)

### Nav (iteração)
- Sem item Search no menu → input no header.
- Sem item Profile no menu → clique no chip do usuário.


## Home após login
A Home mostra **publicações** imediatamente (compose + cards de post).  
Na API atual não há feed pessoal: a UI assume agregação client-side de `GET /communities/me` + posts por comunidade.

## Organização (Starter = 3 páginas)
| Página | Conteúdo |
|---|---|
| `01 · Mint Harbor + Cover` | Cover + telas Mint |
| `02 · Ember Ink` | Telas Ember |
| `03 · Cobalt Paper` | Telas Cobalt |

## Telas (×3 temas)
Login · Register · Home · Communities · My Communities · Community · Create Community · Compose Post · Post Detail · Search · Profile

## Relacionados
- [[Temas-UI]] · [[Telas-MVP]] · [[Fluxos-API-Atual]]
