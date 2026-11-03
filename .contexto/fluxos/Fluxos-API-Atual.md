# Fluxos API Atual

Gate: JWT obrigatório após login (exceto register/login/refresh).

## Auth
- Register → Login → tokens (access + refresh)
- Logout: `DELETE /authentication/logout` com refresh
- Refresh: `POST /authentication/refresh`

## Descoberta
- Lista comunidades paginada
- Minhas comunidades
- Busca unificada (posts / communities / users)

## Comunidade
- Detalhe + join/leave
- Lista posts (`sort=new|hot|top`, `period` se top)
- Criar comunidade (topic/type/status via seeds)
- Icon/banner (owner)

## Conteúdo
- Criar post (membro)
- Detalhe + árvore de comentários + votos ±1/0
- Soft-delete post/comment (autor/ADMIN)

## Conta
- Perfil por `userId` (JWT `sub`)
- Soft-delete `DELETE /users/me`

## Fora do escopo UI
Inbox HTTP · Feed pessoal · Edit metadata · Rules · Ban · Browse anônimo

## Relacionados
- [[Telas-MVP]]
- [[Entidades-MVP]]
