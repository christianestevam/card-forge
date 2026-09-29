# Como o CardForge foi construído

Este documento reúne a trilha interna do processo: como a solução foi planejada e entregue, os identificadores usados nos artefatos (BR, FR, TC, U1 a U6) e os desvios aprovados em relação às regras do projeto. Para avaliar ou rodar a solução, comece pelo [README](../README.md).

## Método

A solução foi construída com o AI-DLC (AI-Driven Development Life Cycle), um fluxo em fases com aprovação humana em cada etapa:

- **Ideação e Inception:** intenção, escopo, requisitos (`BR*`, `FR*`, `NFR*`), histórias, desenho de domínio, unidades de trabalho, contratos e plano de entrega.
- **Construção:** em blocos (Bolts), cada um num branch curto, com merge em `main` só com o CI verde e uma tag por bloco.

Os artefatos ficam em [`aidlc/spaces/default/intents/260928-cardforge-release-1/`](../aidlc/spaces/default/intents/260928-cardforge-release-1/). Os principais:

| Artefato | Conteúdo |
|---|---|
| `inception/requirements-analysis/requirements.md` | Regras de negócio (BR1.1 a BR6.3), requisitos funcionais (FR1 a FR11) e não funcionais (NFR1 a NFR12) |
| `inception/domain-design/components.md` e `decisions.md` | Componentes, entidades e ADRs do desenho de domínio |
| `inception/units-generation/unit-of-work.md` | Unidades U1 (esqueleto) a U6 (documentação) |
| `inception/contract-design/contract-summary.md` | Contratos C1 a C6 (REST, eventos e convenções de erro) |
| `inception/delivery-planning/bolt-plan.md` | Plano original de Bolts |

As regras permanentes do projeto (`ALWAYS`/`NEVER`, stack, decisões) estão em [`aidlc/spaces/default/memory/project.md`](../aidlc/spaces/default/memory/project.md) e `team.md`. O diretório `.claude/` contém a configuração do AI-DLC para o Claude Code.

## Blocos entregues

| Bloco | Tag | Conteúdo |
|---|---|---|
| B1 | `v1.0.0` | Monorepo, `cardforge-platform`, ambiente Compose completo, fluxo ponta a ponta (produto → cadastro → SQS → emissão com cache → consulta consolidada), smoke test, CI e testes de falha (catálogo 5xx/timeout, produto cancelado, mensagem duplicada, SQS fora no cadastro) |
| B2 | `v1.1.0` | Lápide do cache (TC7), degradação da consulta consolidada, transições de status de cartão e portador, testes críticos TC2 a TC6 e TC-PAN, listagem de cartões, métrica de ocupação de BIN, ADRs |
| B3 | `v1.2.0` | Cancelamento, listagem e atualização de produto (`bin-immutable`), caso `STALE` da consulta consolidada (TC8), OpenAPI com os tipos de erro, relógio com precisão de microssegundos |
| B4 | — | Correções da revisão técnica pré-entrega (ver abaixo) |

O trabalho de outro agente, feito em paralelo no mesmo diretório durante o B1, foi descartado do histórico da entrega com um `revert` explícito (commit `d632e3a`), para manter uma única linha de decisões.

## Testes críticos e provas por mutação

Cada garantia crítica tem um teste que prova o comportamento, não apenas a execução do código:

| ID | Garantia | Teste |
|---|---|---|
| TC1 | SQS fora no cadastro; publicação pelo outbox depois da recuperação | `RegistrationIT.sqsUnavailableKeepsEventInOutboxUntilItRecovers` |
| TC2 | Reentrega depois do commit, sem novo efeito | `IssuanceIT.redeliveryAfterCommitRepublishesWithoutNewEffect` |
| TC3 | Mensagens duplicadas simultâneas geram um único cartão | `IssuanceIT.duplicateDeliveryIssuesSingleCard` |
| TC4 | Recusa de negócio reentregue sem mudar o desfecho | `IssuanceIT.redeliveredBusinessRefusalKeepsItsOutcome` |
| TC5 | Duas solicitações concorrentes para o mesmo portador e produto: só uma emite | `IssuanceIT.concurrentRequestsForSameCardholderAndProductIssueOnlyOneCard` |
| TC6 | Cache vencido com catálogo fora: nenhuma emissão, retentativa | `IssuanceIT.staleCacheWithCatalogDownDoesNotIssueAndRetries` |
| TC7 | Cancelamento conhecido impede o uso de `ACTIVE` antigo | `IssuanceIT.knownCancellationRefusesIssuanceWithoutCatalog`, `RedisProductCacheIT` |
| TC8 | Consulta consolidada distingue os cinco casos | `RegistrationIT.consolidatedViewDistinguishesTheFiveCases` |
| TC-PAN | Colisão forçada de PAN | `IssuanceIT.panCollisionIsResolvedWithANewCandidate` |
| NFR7 | Emissão para em até 5 minutos depois do cancelamento | `IssuanceIT.issuanceStopsOnceTheLastActiveObservationIsOlderThanFiveMinutes` |

TC9 e TC10 (reconciliação automática) não foram implementados; ver D12.

**Provas por mutação:** em TC1, TC3, TC-PAN e na recusa sem retry, a proteção foi desligada temporariamente, o teste foi visto falhando pelo motivo certo e o código foi restaurado. O registro está no commit `test: record mutation proofs for the four highest-value tests`. A lápide (TC7) e as correções do B4 (R1, R2, R3) tiveram os testes escritos antes da implementação e vistos falhando pela asserção.

## Desvios aprovados em relação às regras do projeto

Por restrição de prazo, a construção da R1 aplicou os desvios abaixo, aprovados explicitamente pelo responsável. A nota no topo de `project.md` estabelece que eles prevalecem sobre as regras nesta construção. O README descreve cada um em linguagem de produto (seção "Débitos e desvios conscientes"); aqui fica a regra afetada.

| # | Desvio | Regra ou decisão afetada |
|---|---|---|
| D1 | Credenciais fixas de desenvolvimento no realm, no Compose e no Postman | NEVER versionar credenciais; NEVER valor padrão para segredos (exceção: `test`/`test` do LocalStack) |
| D2 | Só a chave HMAC do PAN é gerada e validada | ALWAYS impedir a inicialização com chave de cifragem, HMAC ou fingerprint ausente, malformada ou repetida |
| D3 | PAN guardado só como HMAC e últimos 4 dígitos, sem AES-256-GCM | FR4.5 e NFR9 |
| D4 | Sem `Idempotency-Key` no cadastro | DECIDED: Idempotency-Key obrigatório; teste de concorrência na mesma chave |
| D5 | Sem histórico de transições de status | ALWAYS registrar o histórico com ator e instante (FR7.3) |
| D6 | Cobertura só com relatório, sem piso | `team.md`: piso de 80% por módulo |
| D7 | Spotless só formata, sem `spotless:check` | `team.md`: `spotless:check` bloqueia o build |
| D8 | Sem SpotBugs/FindSecBugs | `team.md`: SpotBugs bloqueia o merge |
| D9 | CI só com `verify`; Compose verificado manualmente no clone limpo | `team.md`: job de smoke test, Trivy e Dependabot |
| D10 | Smoke test reduzido | Definição de pronto do B1 |
| D11 | `traceparent` não atravessa a fila | ALWAYS propagar correlationId e contexto de tracing |
| D12 | Sem reconciliação automática | FR6.1 a FR6.3; TC9 e TC10 |

**Contrato acima do requisito:** o contrato C1 superou o FR1.5. Cancelar um produto já cancelado responde 200 sem mudança (e não 409), por coerência com a decisão de status idempotente das Histórias (Q12).

## Revisão técnica pré-entrega (B4)

Uma revisão independente do commit `4ef799c` apontou defeitos, que o B4 trata com teste de regressão para cada um:

| Achado | Tema |
|---|---|
| R1 | Resposta `ACTIVE` concorrente ignorava uma lápide `CANCELED` já gravada |
| R2 | CPF completo nos logs do Hibernate e do PostgreSQL em cadastro duplicado |
| R3 | Consulta do cartão sem os detalhes do produto |
| R4 | Janela de 5 minutos verificada antes da transação de emissão |
| R5 | Resposta inválida do catálogo virando erro inesperado ou fato de negócio |
| R6 | Mensagem com corpo `null` escapando da DLQ imediata |
| R7 | Overflow na paginação de cartões |

O estado de cada um está no README (seções "Garantias" e "Limitações conhecidas").
