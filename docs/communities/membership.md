# Inscrição em comunidades (resumo)

> Status: **implementado**.

- **Modelo**: `tb_community_membership` com PK composta `(community_id, user_id)`, `role_id`, `joined_at` e `deactivated_at`. FKs para `tb_community` e `tb_users`, ambas `ON DELETE CASCADE`. A entidade guarda `communityId`/`userId` como UUID (sem `@ManyToMany` e sem mapear `User`). Índice parcial `(user_id, joined_at DESC) WHERE deactivated_at IS NULL`.
- **Papéis**: tabela de referência `tb_community_member_roles` (`bigint identity`), com seed `MEMBER` (1) e `MODERATOR` (2), espelhada por `CommunityMemberRoleEnum`.
- **Contador de membros**: `tb_community.member_count`, alterado apenas por `UPDATE` atômico na mesma transação, e somente quando uma linha de inscrição foi de fato inserida/removida (linhas afetadas do `INSERT ... ON CONFLICT DO NOTHING` / `DELETE`). Nunca `existsBy` + `save`. Mapeado com `updatable = false` para o `UPDATE` da entidade não sobrescrever o valor.
- **Criação da comunidade**: o dono é inserido como `MODERATOR`; a comunidade nasce com `member_count = 1`.
- **Entrar**: PUT `/communities/{id}/membership` → 204, idempotente. Permitido para `PUBLIC` e `RESTRICTED` (a restrição de postagem fica no módulo de posts); `PRIVATE` → 403 até existir fluxo de solicitação/convite. Comunidade removida → 404.
- **Sair**: DELETE `/communities/{id}/membership` → 204, idempotente. Remove a linha. O dono não pode sair (403).
- **Minhas comunidades**: GET `/communities/me`, paginado, ordenado por `joined_at DESC` (o `sort` do cliente é ignorado). Exclui inscrições desativadas e comunidades removidas.
- **Resposta**: `CommunityResponseDTO` ganha `memberCount` (coluna) e `isMember` (calculado para o usuário do token; uma consulta em lote por página na listagem).
- **Exclusão de conta**: o módulo `user` define a interface `UserAccountDeletionHandler`; `UserServiceImpl.softDeleteMyAccount()` chama as implementações na mesma transação. A implementação de `community` (`CommunityAccountDeletionHandler`) não depende de `CommunityServiceI`, para não formar ciclo de beans, e faz, nesta ordem:
  1. em um único SQL, preenche `deactivated_at` nas inscrições ativas do usuário e decrementa o `member_count` exatamente dessas comunidades (`UPDATE ... RETURNING` em CTE), travando as linhas de `tb_community` em ordem de `community_id` para evitar deadlock entre exclusões simultâneas;
  2. faz soft delete das comunidades das quais o usuário é dono (`deleted_at`, `deleted_by`).

## Notas de migração

- Changelog `20261008_create_community_membership.xml`: tabelas `tb_community_member_roles` e `tb_community_membership`; coluna `tb_community.member_count BIGINT NOT NULL DEFAULT 0`.
- Changeset de dados: insere o `owner_id` de cada comunidade existente como `MODERATOR` (quando o usuário existe) e recalcula `member_count` a partir das inscrições ativas.

## Fora do escopo (to-do)

- Promover/rebaixar moderadores e sucessão automática da posse (até lá, o dono que exclui a conta remove suas comunidades).
- Fluxo de entrada em comunidades `PRIVATE` (solicitação ou convite por link).
- Purge físico de usuários inativos há mais de 30 dias (as inscrições saem junto via `ON DELETE CASCADE`).
- Em aberto: se comunidades `ARCHIVED`/`BANNED` aceitam novos membros.
- O access token continua válido até o `exp` após a exclusão da conta, e o filtro de JWT não verifica `isActive`; nesse intervalo o usuário ainda consegue entrar em comunidades.
