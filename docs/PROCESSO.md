# Como o CardForge foi construído

Este documento conta como a solução foi planejada e entregue: as ferramentas de IA usadas, a estratégia, por que o AI-DLC foi seguido até o fim da Inception e não na construção, os identificadores dos artefatos (BR, FR, TC, U1 a U6) e os desvios aprovados. Para avaliar ou rodar a solução, comece pelo [README](../README.md). Os prompts principais estão em [`PROMPTS.md`](PROMPTS.md).

## Linha do tempo

| Quando | Etapa | Ferramentas |
|---|---|---|
| Antes de 28/09 | Análise do enunciado, levantamento de dúvidas, escolha do método, estudo do AI-DLC e redação do contexto inicial (product brief, padrões de engenharia e regras do projeto) | Claude (chat) |
| Antes de 28/09 | Três rodadas de revisão de arquitetura sobre as premissas, antes de qualquer código | ChatGPT (revisor) e Claude (triagem) |
| 28/09 00h → 29/09 01h | AI-DLC 2.10, profile MVP: Ideação (intenção, viabilidade, escopo) e Inception completa (práticas, requisitos, histórias, domínio, unidades, contratos e plano de entrega) | Claude Code + AI-DLC |
| 29/09 01h → 16h | Construção acelerada em quatro blocos (B1 a B4), usando os artefatos aprovados como especificação | Claude Code |
| 29/09 | Revisão técnica pré-entrega contra o enunciado, corrigida no B4 | GPT Codex (OpenAI) e Claude Code |

## Ferramentas de IA

| Ferramenta | Papel |
|---|---|
| **Claude Code** (Anthropic) com **AI-DLC 2.10.0** (AWS, open source) | Agente executor. Na Ideação e na Inception, conduziu o workflow do AI-DLC com os agentes especializados e dois revisores (arquitetura e produto), com o preset de modelos `thorough` (revisão com esforço máximo). Na construção, implementou os blocos com o plano apresentado e aprovado antes de cada bloco. |
| **Claude** (claude.ai) | Par de planejamento: análise do enunciado, estudo do AI-DLC e do estado atual do desenvolvimento com IA, redação do contexto inicial, apoio às respostas de cada gate, triagem dos achados das revisões e redação dos prompts de construção. |
| **ChatGPT** | Revisor independente de arquitetura: três rodadas sobre as premissas e decisões, antes da implementação. |
| **GPT Codex** (OpenAI) | Revisão técnica pré-entrega do código contra o enunciado (achados R1 a R7 e melhorias), tratada no B4. |

Nenhuma saída de IA entrou sem decisão humana: as revisões de uma ferramenta foram analisadas com outra, e a decisão final, registrada, foi sempre minha.

## Estratégia

1. **Especificação antes do código.** Antes do primeiro prompt ao agente, o contexto foi escrito e versionado: [`product-brief.md`](../aidlc/spaces/default/knowledge/aidlc-shared/product-brief.md) (negócio, regras numeradas, requisitos não funcionais e escopo), [`engineering-standards.md`](../aidlc/spaces/default/knowledge/aidlc-shared/engineering-standards.md) (convenções técnicas) e [`project.md`](../aidlc/spaces/default/memory/project.md) (regras `ALWAYS`/`NEVER` e decisões já tomadas). É engenharia de contexto: o agente trabalha dentro de limites explícitos, e não de suposições.
2. **Premissas explícitas.** O enunciado não define volumes, SLAs nem várias regras de negócio. Em vez de perguntar ao avaliador, defini premissas com base no contexto de varejo e meios de pagamento (picos de adesão no checkout, BIN de 8 dígitos, controles de PCI-DSS e LGPD, idade mínima de 18 anos) e as registrei no brief.
3. **Contexto de produção para os agentes.** O brief apresenta o CardForge como uma release que vai para produção, e não como um exercício, para calibrar o rigor dos agentes. Para equilibrar, ele declara as restrições reais de entrega: prazo curto, uma pessoa e YAGNI (cada componente precisa se justificar por um requisito).
4. **Revisão cruzada antes de codar.** As premissas passaram por três rodadas de revisão com outra IA. Elas mudaram o desenho: a janela de dado desatualizado na emissão (que permitia emitir para produto já cancelado) foi substituída pela regra dos 5 minutos; o resultado da emissão passou a ser persistido (`issuance_processing`), inclusive as recusas; a unicidade foi levada para índices parciais no banco; e o retry ganhou um orçamento calculado.
5. **Humano em cada gate.** Cada estágio do AI-DLC terminou com a minha aprovação. Correções relevantes viraram regras do projeto (learning loop), e contradições entre respostas foram resolvidas antes de avançar.
6. **Risco primeiro.** Primeiro um esqueleto ponta a ponta verificado por smoke test; depois as garantias mais arriscadas (emissão, idempotência e falhas); por último as APIs simples e a documentação.
7. **Testes que provam comportamento.** Os testes críticos cobrem falhas e concorrência. Nos de maior valor, a proteção foi desligada de propósito para confirmar que o teste falha pelo motivo certo (prova por mutação).

## Por que o AI-DLC não foi seguido até o fim

O profile MVP do AI-DLC tem 23 estágios. Só a Ideação e a Inception levaram cerca de um dia de gates. Na construção, cada uma das seis unidades passaria por Functional Design, NFR Requirements, NFR Design, Infrastructure Design e Code Generation: perto de 30 gates, cada um com revisão adversarial, registro de aprendizados e as reconfirmações exigidas pela Guard Policy `strict`. Esse ritmo não cabia no prazo de entrega.

A decisão foi **encerrar o workflow ao fim da Inception**, com o desenho completo e aprovado, e **construir com o Claude Code diretamente**, usando os artefatos aprovados (requisitos, domínio, contratos, ADRs e plano de unidades) como especificação. Os hooks do AI-DLC foram desativados apenas localmente para isso.

**O que foi mantido do método:** plano antes do código em cada bloco, com aprovação; um branch por bloco, com merge em `main` só com o CI verde e uma tag por bloco; testes escritos antes nas garantias críticas; provas por mutação; e cortes aprovados explicitamente e documentados (D1 a D12).

**O que se perdeu:** os artefatos de desenho por unidade da fase de construção e a trilha de auditoria do AI-DLC nessa fase. A trilha da Ideação e da Inception está completa em `aidlc/`.

**Lição:** para um prazo de dias, o AI-DLC rende mais onde a incerteza é maior, na Ideação e na Inception. Na construção, um profile mais enxuto (por exemplo `classic` ou profundidade mínima), o preset de modelos `balanced` e a Guard Policy `relaxed` para quem trabalha sozinho teriam mantido o workflow dentro do prazo.

## Artefatos do AI-DLC

O AI-DLC (AI-Driven Development Life Cycle) é um fluxo em fases em que a IA propõe, pergunta e produz, e um humano aprova cada etapa. Nesta entrega, ele cobriu a Ideação e a Inception: intenção, viabilidade, escopo, práticas, requisitos (`BR*`, `FR*`, `NFR*`), histórias, desenho de domínio, unidades de trabalho, contratos e plano de entrega. Cada estágio tem o arquivo de perguntas com as minhas respostas, o artefato gerado e, quando houve, a revisão.

Os artefatos ficam em [`aidlc/spaces/default/intents/260928-cardforge-release-1/`](../aidlc/spaces/default/intents/260928-cardforge-release-1/). Os principais:

| Artefato | Conteúdo |
|---|---|
| `inception/requirements-analysis/requirements.md` | Regras de negócio (BR1.1 a BR6.3), requisitos funcionais (FR1 a FR11) e não funcionais (NFR1 a NFR12) |
| `inception/domain-design/components.md` e `decisions.md` | Componentes, entidades e ADRs do desenho de domínio |
| `inception/units-generation/unit-of-work.md` | Unidades U1 (esqueleto) a U6 (documentação) |
| `inception/contract-design/contract-summary.md` | Contratos C1 a C6 (REST, eventos e convenções de erro) |
| `inception/delivery-planning/bolt-plan.md` | Plano original de Bolts |

As regras permanentes do projeto (`ALWAYS`/`NEVER`, stack, decisões) estão em [`aidlc/spaces/default/memory/project.md`](../aidlc/spaces/default/memory/project.md) e `team.md`. O diretório `.claude/` (configuração do AI-DLC para o Claude Code, cerca de 300 arquivos do framework) não é versionado: ele é gerado com `aidlc config --harness claude`.

## Blocos entregues

| Bloco | Tag | Conteúdo |
|---|---|---|
| B1 | `v1.0.0` | Monorepo, `cardforge-platform`, ambiente Compose completo, fluxo ponta a ponta (produto → cadastro → SQS → emissão com cache → consulta consolidada), smoke test, CI e testes de falha (catálogo 5xx/timeout, produto cancelado, mensagem duplicada, SQS fora no cadastro) |
| B2 | `v1.1.0` | Lápide do cache (TC7), degradação da consulta consolidada, transições de status de cartão e portador, testes críticos TC2 a TC6 e TC-PAN, listagem de cartões, métrica de ocupação de BIN, ADRs |
| B3 | `v1.2.0` | Cancelamento, listagem e atualização de produto (`bin-immutable`), caso `STALE` da consulta consolidada (TC8), OpenAPI com os tipos de erro, relógio com precisão de microssegundos |
| B4 | — | Correções da revisão técnica pré-entrega (ver abaixo), README reorganizado para a avaliação e este documento |

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

Uma revisão independente do commit `4ef799c` apontou defeitos e melhorias. O B4 tratou todos, cada defeito com teste de regressão. Em R1, R2 e R3, o teste foi escrito antes e visto falhando pela asserção.

| Achado | Tema | Correção | Teste |
|---|---|---|---|
| R1 | Resposta `ACTIVE` concorrente ignorava uma lápide `CANCELED` já gravada | Se o cache recusa a gravação, a decisão segue a observação vencedora | `IssuanceIT.activeResponseLosingToAKnownCancellationDoesNotIssue` |
| R2 | CPF completo nos logs do Hibernate e do PostgreSQL em cadastro duplicado | Inserção com `ON CONFLICT (cpf) DO NOTHING`: o banco não gera erro | `RegistrationIT.duplicateCpfNeverLeaksTheCpfToLogs` |
| R3 | Consulta do cartão sem os detalhes do produto | Seção `product` no `GET /cards/{id}`, do cache ou do catálogo | `IssuanceIT.cardQuery…`, `cardOfCanceledProductRemainsQueryable` |
| R4 | Janela de 5 minutos verificada antes da transação de emissão | `Eligible` carrega o `validatedAt`; a idade é conferida dentro da transação | `IssuanceIT.observationThatExpiresBeforeTheIssuingTransactionIsNotUsed` |
| R5 | Resposta inválida do catálogo virando erro inesperado ou fato de negócio | Status remoto só `ACTIVE`/`CANCELED` e BIN com 8 dígitos; o resto é erro de contrato | `IssuanceIT.catalogResponseOutOfContractNeverDecides`, `RegistrationIT.catalogWithNullStatusIsAContractErrorNotAServerError` |
| R6 | Mensagem com corpo `null` escapando da DLQ imediata | `EventReader` rejeita envelope nulo | `EventReaderTest`, `…nullMessageBodyGoesToDeadLetterQueue`, `…nullResultBodyGoesToDeadLetterQueue` |
| R7 | Overflow na paginação | Offset em `long`; página além do limite responde 400 | `…RejectsPagesBeyondTheSupportedOffset` (produtos e cartões) |
| Melhoria 1 | Erros de banco no consumidor fora do backoff | `DataAccessException` e `TransactionException` seguem o backoff | `IssuanceRequestedListenerTest` |
| Melhoria 2 | Métrica de decisão antes do commit | Log e métrica registrados só depois do commit | — |
| Melhoria 3 | Smoke test sem timeout por chamada | `--connect-timeout 5 --max-time 15` em todos os `curl` | Smoke test |
| Melhoria 4 | Portas expostas em todas as interfaces | Portas do Compose só em `127.0.0.1` | Compose |
| Melhoria 5 | Diferença entre falha transitória e perda da fila | Documentada no README | — |
| Melhoria 7 | Schema pouco expressivo do `PATCH` | `UpdateProductRequest` documentado no OpenAPI, com exemplos | `ProductApiIT.openApiDocumentsProblemResponsesAndBusinessErrors` |

A melhoria 6 (trocar o `Optional<Optional<String>>` do `PATCH` por um comando explícito) não foi feita: a semântica está isolada num único ponto.
