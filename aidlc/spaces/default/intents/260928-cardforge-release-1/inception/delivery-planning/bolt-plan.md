# Plano de Bolts: CardForge Release 1.0

Um **Bolt** é uma passada de construção sobre uma parte do trabalho que termina em algo que roda e é verificado.

Base:
- `unit-of-work.md`, `unit-of-work-dependency.md` e `unit-of-work-story-map.md`;
- `contract-summary.md`;
- `requirements.md` e `stories.md`;
- `team-practices.md`;
- `delivery-planning-questions.md` (Q1 a Q6).

**Como o plano executa:**
- **Ordem:** risco primeiro, U1 → U2 → U3 → U4 → U5 → U6, uma unidade de cada vez (Q1).
- **Construção:** unidade a unidade, em série (Q4), nesta sessão, com você aprovando cada checkpoint (Q5).
- **O que conta como verdade:** o grafo de dependências entre unidades. Este plano agrupa as unidades em três Bolts para planejamento e demonstração.
- **Prazo:** 05/10/2026.

## B1: esqueleto ponta a ponta (walking skeleton)

- **Unidade:** U1 `walking-skeleton`.
- **O que é o esqueleto:** a versão mínima que percorre o sistema inteiro, construída antes das funcionalidades reais para provar que as peças se conectam (`team.md`).
- **Definição de pronto:**
  - `docker compose up -d --build` sobe todos os containers saudáveis a partir de um clone limpo, gerando os segredos em `./.local/secrets`;
  - `scripts/smoke-test.sh` passa: token no Keycloak, produto criado, cadastro com `Idempotency-Key`, emissão pela fila com o produto validado pelo cache (chave presente no Redis) e `/overview` mostrando `ISSUED` em até 60 s;
  - `./mvnw verify` verde no CI, com cobertura ≥ 80% de linhas por módulo, Spotless, SpotBugs com FindSecBugs e ArchUnit; job do smoke test verde;
  - varredura de segredos limpa; `.gitignore` com `.env`, `.local/` e `target/`;
  - README com setup e diagrama inicial, Postman com o fluxo feliz, OpenAPI do que foi exposto;
  - aprovação humana do checkpoint do esqueleto.
- **Hipótese de confiança:** as decisões de arquitetura se conectam de verdade. Isso inclui:
  - outbox nos dois serviços que publicam;
  - `issuance_processing`;
  - cache com `validatedAt`;
  - JWT com client credentials entre serviços;
  - propagação de correlationId pela fila;
  - o ambiente com um comando.

  Se algo disso não fechar, é aqui que descobrimos, antes de construir em cima.
- **Demonstração:** clone limpo → um comando → smoke test verde, mostrando o cartão emitido na consulta consolidada e o `correlationId` nos logs dos dois serviços.

## B2: garantias de emissão e cadastro

- **Unidades:** U2 `card-issuance`, depois U3 `cardholder-registration`.
- **Definição de pronto:**
  - U2: TC2 a TC7 e TC-PAN verdes, cada um escrito antes e visto falhando pelo motivo certo; classes de falha, backoff e DLQ; status e histórico do cartão; métricas da emissão; validação das chaves do PAN.
  - U3: TC1, TC9, TC10 e TC-IDEM verdes; replay, 409 e 422 de idempotência; resultado idempotente com DLQ explícita; reconciliação com suspensão; status e histórico do portador e da solicitação; validação da chave de fingerprint.
  - Cada unidade atualiza README, Postman, OpenAPI e ADRs com o que entrega. Os ADRs de cache, retry/DLQ, outbox e idempotência são escritos completos.
  - Checkpoint verificado e aprovado ao fim de cada unidade.
- **Hipótese de confiança:** as garantias de negócio se sustentam sob falha e concorrência:
  - nenhuma solicitação aceita se perde entre banco e mensageria;
  - cada solicitação tem no máximo um desfecho terminal, nunca reavaliado, e fica rastreável e recuperável com alerta enquanto não o tiver;
  - não existem cartões duplicados;
  - nenhuma emissão para produto cancelado além de 5 minutos.
- **Demonstração:** execução dos testes críticos, mais o smoke test com a SQS parada durante o cadastro e retomada em seguida.

## B3: catálogo completo, consulta consolidada e entrega

- **Unidades:** U4 `product-catalog`, depois U5 `consolidated-view`, depois U6 `delivery-docs`.
- **Definição de pronto:**
  - U4: listagem paginada, atualização com `bin-immutable` e cancelamento idempotente com histórico.
  - U5: os cinco casos da consulta consolidada distinguíveis por campos estruturados (TC8), com `requestedAt`.
  - U6: README revisado contra a lista obrigatória, incluindo limitações conhecidas, débitos e a seção de controles de segurança e privacidade; ADRs conferidos; OpenAPI dos três serviços conferido; Postman revisado, com a requisição de replay.
  - Cortes de profundidade aplicados, se houver, registrados como débito.
  - Tag `v1.0.0` em `main`.
- **Hipótese de confiança:** o gateway consegue integrar só com os contratos e a documentação, e a consulta consolidada nunca informa algo errado quando uma dependência falha.
- **Demonstração:** a collection do Postman, executada contra o ambiente local, percorre o fluxo feliz, os erros das regras críticas, o replay e a consulta consolidada com o card-service parado.

## Resumo

| Bolt | Unidades | Esqueleto? | Hipótese de confiança |
|---|---|---|---|
| B1 | U1 | Sim | A arquitetura se conecta ponta a ponta com um comando |
| B2 | U2, U3 | Não | As garantias de integridade se sustentam sob falha e concorrência |
| B3 | U4, U5, U6 | Não | O gateway integra só com contratos e documentação |
