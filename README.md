# Clone Reddit

> Um projeto de aprendizado contínuo, construído do zero, com o objetivo de aplicar na prática — e não apenas em tutoriais isolados — todas as habilidades técnicas exigidas hoje no mercado: arquitetura de software, banco de dados, microsserviços, mensageria, cache, segurança, observabilidade, infraestrutura e CI/CD.

---

## 📌 Sobre o projeto

Este não é um projeto com prazo de entrega. É um **projeto vivo**, pensado para crescer indefinidamente — começando como um monólito simples e evoluindo, ao longo do tempo, para uma arquitetura distribuída completa (microsserviços, mensageria, múltiplos bancos de dados, observabilidade, infraestrutura como código).

A escolha do domínio (um clone simplificado do Reddit) não é acidental. A ideia central do "Projeto Impossível" é ter **regras de negócio propositalmente simples**, para que toda a energia e complexidade do projeto estejam concentradas em **arquitetura e infraestrutura**, e não em resolver problemas de domínio (como aconteceria, por exemplo, em um e-commerce real, cheio de regras fiscais, frete, estoque e pagamento).

### Por que um agregador de conteúdo (estilo Reddit)?

- Regra de negócio simples e intuitiva — não exige pesquisa de domínio complexo.
- Naturalmente rico em problemas técnicos reais: feed personalizado, comentários aninhados, sistema de votos em alta concorrência, notificações, busca, moderação.
- Permite migrar e comparar diferentes tipos de banco de dados (relacional, documento, chave-valor, busca) sem forçar nada artificialmente.
- Cresce organicamente: cada funcionalidade simples de produto abre, naturalmente, um problema técnico não trivial (ver seção [Habilidades vs. Funcionalidades](#-mapa-habilidades-de-mercado--funcionalidades)).

### Objetivos de aprendizado

Este projeto será o ambiente prático para estudar e aplicar, entre outros:

- Modelagem de dados relacional e não relacional
- Concorrência e condições de corrida (race conditions)
- Idempotência em sistemas distribuídos
- Cache (estratégias de invalidação, cache-aside, fan-out)
- Mensageria e processamento assíncrono
- Arquitetura de microsserviços (limites de serviço, comunicação síncrona/assíncrona)
- Resiliência (circuit breaker, retry, timeout, rate limiting)
- Segurança (autenticação, autorização contextual/RBAC)
- Observabilidade (logs, métricas, tracing distribuído)
- CI/CD e infraestrutura como código
- Padrões de projeto e boas práticas de arquitetura de software

---

## 🌐 O que é o Reddit (referência de domínio)

O Reddit é uma rede de **comunidades temáticas** (subreddits), fundada em 2005, cujo conceito central é permitir que qualquer pessoa crie uma comunidade sobre qualquer assunto. Dentro dessas comunidades, os usuários publicam conteúdo, comentam, votam, e o conteúdo mais relevante "sobe" através de um sistema de curadoria coletiva por votos — não por decisão editorial centralizada.

Principais características do produto original que servem de referência para este projeto:

- **Comunidades (subreddits):** contêiners temáticos, com moderação própria, regras e identidade.
- **Posts:** pertencem sempre a uma comunidade.
- **Comentários:** podem ter respostas aninhadas, em múltiplas camadas de profundidade.
- **Votos (upvote/downvote):** tanto posts quanto comentários podem ser votados.
- **Score:** calculado a partir dos votos, define o posicionamento do conteúdo.
- **Feed pessoal:** mistura posts das comunidades que o usuário segue.
- **Karma:** soma dos votos recebidos por um usuário em seus posts e comentários.
- **Moderação:** moderadores por comunidade podem remover conteúdo, banir usuários e fixar posts.
- **Notificações:** o usuário é avisado sobre respostas, upvotes e menções.
- **Busca:** de posts, comunidades e usuários.
- **Ordenação de conteúdo:** Hot (relevante agora), New (mais recente), Top (mais votado, com filtro de período).

> Visibilidade de conteúdo é, por padrão, pública — um usuário não precisa se inscrever em uma comunidade para visualizar seus posts. A inscrição serve apenas para personalizar o **feed pessoal**.

---

## 🧩 Domínio do projeto (MVP)

### Entidades candidatas

| Entidade | Observações |
|---|---|
| Usuário | Autenticação, perfil, karma |
| Comunidade | Contêiner estrutural do post; possui moderadores e regras |
| Post | Pertence a exatamente uma comunidade |
| Comentário | Auto-relacionamento (resposta aninhada) |
| Voto | Entidade própria (não atributo) — associada a um usuário e a um alvo (post ou comentário) |
| Membership | Relação usuário ↔ comunidade (papel: membro ou moderador) |
| Ban | Usuário banido de uma comunidade específica |
| Notificação | Evento direcionado a um usuário (resposta, menção, upvote) |
| Tag *(extensão de produto, fora do Reddit original)* | Mecanismo de descoberta transversal a comunidades — relação N:N com posts |

> **Karma** não será uma entidade própria: será tratado como um contador persistido no usuário, atualizado de forma incremental a cada evento de voto (decisão registrada na seção de [Decisões de Arquitetura](#-decisões-de-arquitetura)).

---

## 🗺️ Mapa: habilidades de mercado × funcionalidades

Cada funcionalidade do produto foi escolhida (ou vai naturalmente exigir) uma ou mais habilidades técnicas específicas:

| Funcionalidade | Habilidades treinadas |
|---|---|
| Sistema de votos | Concorrência, race conditions, operações atômicas, contadores distribuídos |
| Notificações | Idempotência, mensageria, circuit breaker, retry/backoff |
| Autenticação e autorização | Segurança, RBAC/autorização contextual (moderador só age na própria comunidade) |
| Feed pessoal | Cache, fan-out on write/read, paginação por cursor |
| Comentários aninhados | Modelagem de dados em árvore, consistência estrutural |
| Busca | Indexação assíncrona, consistência eventual |
| Rate limit (votos, posts, comentários) | Proteção contra abuso, algoritmos de rate limiting |
| Comunicação entre serviços | Microsserviços, timeout, retry, service discovery |
| Processamento de mídia (upload) | Filas, processamento assíncrono, workers |
| Recalculo de Hot score | Paralelismo/threads, jobs periódicos |

---

## 🗄️ Estratégia de dados

O projeto não usará um único banco de dados — cada tipo de dado será alocado ao banco que melhor resolve seu problema específico, evoluindo ao longo das fases do projeto:

| Tipo de dado | Banco (candidato) | Motivo |
|---|---|---|
| Domínio central (usuário, comunidade, post, membership, ban, voto) | Relacional (PostgreSQL) | Integridade referencial, transações, relacionamento bem definido |
| Comentários aninhados | Relacional (fase inicial) → avaliação futura de documento (MongoDB) ? | Começa simples (adjacency list); migração planejada como exercício de evolução de arquitetura |
| Cache de feed, contadores, rate limit | Chave-valor (Redis) | Leitura rápida, dados voláteis, alta frequência de acesso |
| Busca (posts, comunidades, usuários) | Motor de busca (Elasticsearch) ? | Busca textual otimizada, impraticável em SQL puro |
| Notificações | A avaliar (documento ou wide-column) | Alto volume de escrita, formato simples, pouco relacional |
| Log de eventos / auditoria / analytics | A avaliar (wide-column ou time-series) | Escrita massiva, dados imutáveis (append-only) |
| Imagens e vídeos | Object storage (MinIO local / S3 ) | Banco de dados não deve armazenar binários grandes; apenas a referência (URL/metadados) é persistida no domínio |

---

## 🏗️ Decisões de Arquitetura

> Registro vivo das decisões tomadas ao longo do projeto, incluindo o raciocínio por trás de cada uma. Cresce conforme o projeto avança.

### Karma do usuário
- **Decisão:** karma será um campo persistido (`users.karma_score`), atualizado de forma **incremental** (`+1`/`-1`/delta) a cada evento de voto — nunca recalculado por completo a cada leitura.
- **Motivo:** recalcular a soma de todos os votos de todos os posts/comentários de um usuário a cada visita de perfil não escala. Karma muda com frequência, mas em pequenos incrementos — perfil ideal para contador incremental.
- **Ponto de atenção:** concorrência em escritas simultâneas (dois votos ao mesmo tempo no mesmo usuário) exige operação atômica no banco, não "ler → somar em memória → gravar".
- **Evolução futura:** job periódico de reconciliação, recalculando o valor real a partir dos dados brutos, como camada de correção/auditoria.

### Comunidade vs. Tag
- **Decisão:** todo post pertence obrigatoriamente a **uma** comunidade (relação estrutural 1:N, define moderação e governança). Tags são um mecanismo **opcional e adicional** (relação N:N), usado para descoberta de conteúdo entre comunidades diferentes.
- **Motivo:** comunidade resolve "onde o conteúdo mora e quem modera"; tag resolve "sobre o que o conteúdo é", cruzando comunidades.

### Comentários: relacional ou NoSQL?
- **Decisão:** iniciar em banco relacional (adjacency list), migrar para banco de documento apenas quando a dor de performance/consulta recursiva aparecer de fato.
- **Motivo:** migração de domínio entre bancos é, por si só, uma habilidade de mercado relevante — mais valiosa como aprendizado do que já nascer com a escolha "ideal".

### Armazenamento de mídia
- **Decisão:** arquivos (imagem/vídeo) vão para object storage (MinIO, compatível com API S3); o banco de dados guarda apenas referência (URL) e metadados.
- **Motivo:** banco de dados não é otimizado para armazenar binários grandes; MinIO permite desenvolvimento local com a mesma interface usada em produção (AWS S3).

---

## 📁 Estrutura deste repositório

```
/docs
  /system-design      → Diagramas e decisões de arquitetura (C4, diagramas de serviço, etc.)
  /mer                → Modelo Entidade-Relacionamento e modelagem de dados
  /adr                → Architecture Decision Records (se adotado futuramente)
README.md              → Este documento
```

> Estrutura sujeita a evoluir conforme o projeto avança (ex: pastas por serviço, quando a migração para microsserviços acontecer).

---

## 🚧 Status atual

**Fase: Definição de domínio e System Design.**

O projeto ainda não possui código. As anotações de domínio, entidades e mapeamento de habilidades foram fechadas; o próximo passo é o desenho do System Design (definição de serviços, bancos de dados por serviço, comunicação síncrona/assíncrona, MER) antes do início da implementação.

---

## 📚 Referências de estudo

- Estudo do funcionamento real do Reddit (produto, moderação, algoritmo de relevância) como base comparativa para as decisões deste projeto.

---

## 📝 Licença

Projeto pessoal de estudo. Sem fins comerciais.
