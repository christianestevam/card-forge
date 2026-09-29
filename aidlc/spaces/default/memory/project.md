# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> CardForge: plataforma de emissão de cartões da RPE, Release 1.0, destinada
> à produção. Referências obrigatórias em `aidlc/spaces/default/knowledge/aidlc-shared/`:
> `product-brief.md` (negócio, regras, NFRs, escopo) e
> `engineering-standards.md` (convenções técnicas).

## Way of Working

- Release com prazo curto, desenvolvida por uma pessoa. Cada componente se justifica por um requisito do product brief; na dúvida, não construir.
- Sugestões novas são classificadas (requisito, decisão aprovada, opção em avaliação, fora da release) e não viram obrigação sem aprovação em gate.
- Todo o código é escrito em inglês: módulos, pacotes, classes, métodos, variáveis, tabelas, colunas, endpoints, campos JSON, eventos, filas, métricas e logs, seguindo a linguagem ubíqua do product brief.
- Documentação (README, ADRs e artefatos do AI-DLC) em português do Brasil, citando os termos de código em inglês.
- Monorepo com um módulo Maven por serviço (`product-service`, `cardholder-service`, `card-service`) e `docker-compose.yml` na raiz.
- Commits pequenos em Conventional Commits, em inglês, associados à Unit of Work revisada.
- ADR em `docs/adr/` apenas para decisões relevantes, controversas ou difíceis de reverter. As decisões da seção Decided viram ADRs com as alternativas consideradas.

## Walking Skeleton

- O primeiro incremento integrado sobe o ambiente completo com `docker compose up -d --build` e prova o fluxo ponta a ponta: token no Keycloak, criação de produto, cadastro de portador com `Idempotency-Key`, emissão assíncrona via SQS com validação do produto (usando o cache Redis) e consulta consolidada mostrando o cartão emitido.
- Verificação do skeleton: `scripts/smoke-test.sh`, versionado, que executa esse fluxo contra o ambiente em Docker e falha em qualquer desvio.

## Testing Posture

- Testes unitários (JUnit 5, Mockito, AssertJ) para agregados, transições, validações (CPF, idade, Luhn, BIN) e casos de uso.
- Testes de integração com Testcontainers (PostgreSQL, Redis, LocalStack) e WireMock.
- Os dez testes críticos abaixo são obrigatórios e cada um prova um comportamento, não apenas a execução do código:
  1. Cadastro com SQS indisponível e publicação pelo outbox após a recuperação.
  2. Falha após o commit e antes do ACK: a reentrega não gera efeito duplicado.
  3. Mensagens duplicadas da mesma solicitação processadas simultaneamente geram um único cartão.
  4. Recusa de negócio persistida e reentregue sem mudar o desfecho.
  5. Duas emissões concorrentes para o mesmo portador e produto: apenas uma tem sucesso.
  6. Cache vencido com catálogo indisponível: nenhuma emissão; a mensagem volta para retentativa.
  7. Cancelamento conhecido impede o uso de qualquer registro `ACTIVE` antigo.
  8. Consulta consolidada distingue ausência esperada, desfecho de negócio, indisponibilidade e dado desatualizado.
  9. Solicitação antiga recuperada pela reconciliação sem emitir outro cartão.
  10. Resultado perdido: a reconciliação faz o resultado persistido ser republicado e aplicado.
- Também obrigatórios: colisão forçada de PAN (cartão, resultado e outbox persistidos atomicamente após a colisão) e concorrência na mesma `Idempotency-Key` (replay, 409 por timeout de lock e 422 por payload diferente).
- Cada regra BR-* do product brief é rastreável a pelo menos um teste.
- `./mvnw verify` passa localmente e no CI, dependendo apenas de Docker.

## Guard Policy

Mode: strict

## Deployment

- Entrega: serviços e ambiente local em Docker Compose (PostgreSQL com um database por serviço, Redis, LocalStack, Keycloak e os três serviços), com um único comando.
- LocalStack inicializado por script que cria filas, DLQs e redrive policies. Keycloak com realm importado de arquivo versionado (clients, escopos, audiência).
- Healthcheck em todos os containers; serviços dependem da infraestrutura com `condition: service_healthy`.
- Infraestrutura AWS de produção fora desta iniciativa.

## Code Style

- Seguir `engineering-standards.md`: hexagonal enxuta, domínio sem frameworks, SOLID e Clean Code, sem abstrações cerimoniais.
- Pacote base `com.rpe.cardforge.<service>` (ex.: `com.rpe.cardforge.card.domain`).
- DTOs como Java records com Bean Validation na borda; entidades JPA nunca expostas na API.
- `@RestControllerAdvice` com `ProblemDetail` e códigos HTTP do padrão.
- Enums em inglês: `ACTIVE`, `BLOCKED`, `CANCELED`; `PENDING`, `ISSUED`, `FAILED`.
- Spotless com google-java-format.

## Tech Stack

- Java 21 (LTS).
- Spring Boot 3.5.x, conforme a diretriz da plataforma. A linha 3.5 está fora do suporte OSS desde junho de 2026; o risco e o plano de migração para 4.x ficam em ADR.
- Maven multi-módulo com Maven Wrapper.
- PostgreSQL + Spring Data JPA + Flyway; Redis (Lettuce) com adaptador de cache próprio; SQS via Spring Cloud AWS.
- Resilience4j; Spring Security OAuth2 Resource Server e Client; Keycloak como IdP local.
- springdoc-openapi; Actuator; Micrometer (Prometheus e Tracing).
- JUnit 5, Mockito, AssertJ, Testcontainers, WireMock, JaCoCo.
- Docker multi-stage, Docker Compose, GitHub Actions.
- Collection do Postman em `postman/`.

## Decided

- DECIDED: Nomenclatura de código inteiramente em inglês, conforme a linguagem ubíqua do product brief (Stage pre-inception, 2026-09-27)
- DECIDED: Java 21 + Spring Boot 3.5.x + Maven multi-módulo em monorepo; migração para Boot 4.x registrada como débito (Stage pre-inception, 2026-09-27)
- DECIDED: Keycloak como IdP local; o gateway de onboarding é o único cliente e responde pela autorização por recurso (Stage pre-inception, 2026-09-27)
- DECIDED: Hexagonal enxuta com um módulo Maven por serviço e entidades JPA separadas do domínio (Stage pre-inception, 2026-09-27)
- DECIDED: SQS Standard com DLQ por fila; filas card-issuance-requested e card-issuance-completed (Stage pre-inception, 2026-09-27)
- DECIDED: Transactional Outbox no cardholder-service e no card-service, com worker agendado interno, lote de até 10 via SendMessageBatch e lock mantido durante o envio (Stage pre-inception, 2026-09-27)
- DECIDED: Resultado terminal da emissão persistido em issuance_processing; solicitação decidida nunca é reavaliada e, se reentregue, tem o resultado republicado (Stage pre-inception, 2026-09-27)
- DECIDED: Consumidor de resultados idempotente por estado, sinalizando resultados contraditórios (Stage pre-inception, 2026-09-27)
- DECIDED: Emissão autorizada por observação ACTIVE de no máximo 5 minutos; cache com registro único e validatedAt não renovado na leitura; dado antigo apenas em consultas, sinalizado (Stage pre-inception, 2026-09-27)
- DECIDED: Retry pela SQS com uma tentativa HTTP por processamento, backoff 30 s dobrando, jitter ±20%, teto rígido de 5 min após o jitter e maxReceiveCount ≈ 29 (orçamento nominal ~2 h) (Stage pre-inception, 2026-09-27)
- DECIDED: Reconciliação por republicação da solicitação, com idade mínima acima do orçamento de retry, lotes limitados e suspensão após N tentativas (Stage pre-inception, 2026-09-27)
- DECIDED: Idempotency-Key obrigatório no cadastro, por client_id, com recibo de aceite estável, replay por 24 h, 422 para payload diferente e 409 para requisição em andamento (Stage pre-inception, 2026-09-27)
- DECIDED: Unicidade por índices parciais; CPF globalmente único na R1; um cartão não cancelado por portador e produto (Stage pre-inception, 2026-09-27)
- DECIDED: PAN com conta aleatória (SecureRandom), ON CONFLICT no pan_hmac, até 20 tentativas e alerta de ocupação do BIN em 70% (Stage pre-inception, 2026-09-27)

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

- NEVER emitir um cartão sem uma observação do produto como ACTIVE feita há no máximo 5 minutos (affirmed 2026-09-27)
- NEVER reavaliar uma solicitação de emissão que já tem desfecho terminal (affirmed 2026-09-27)
- NEVER classificar erros técnicos, de autenticação ou de contrato como PRODUCT_NOT_FOUND ou outra falha de negócio (affirmed 2026-09-27)
- NEVER retentar falhas de regra de negócio (affirmed 2026-09-27)
- NEVER fazer chamada remota dentro de uma transação de escrita (affirmed 2026-09-27)
- NEVER fazer chamada remota (HTTP, Redis, SQS, banco) sem timeout configurado (affirmed 2026-09-27)
- NEVER publicar no SQS diretamente a partir da transação de negócio; usar o outbox (affirmed 2026-09-27)
- NEVER confirmar uma mensagem SQS antes de o resultado do processamento estar persistido (affirmed 2026-09-27)
- NEVER expor o PAN além de panLastFour, nem registrar em log PAN, CVV, CPF, data de nascimento ou tokens (affirmed 2026-09-27)
- NEVER registrar em log records ou DTOs que contenham dados pessoais (affirmed 2026-09-27)
- NEVER incluir CPF, data de nascimento ou PAN em mensagens entre serviços (affirmed 2026-09-27)
- NEVER versionar credenciais, segredos ou chaves (affirmed 2026-09-27)
- NEVER usar H2 ou banco em memória em testes de integração (affirmed 2026-09-27)
- NEVER apagar fisicamente produtos, portadores ou cartões (affirmed 2026-09-27)

- NEVER fazer merge em main com o CI vermelho ou com o piso de cobertura rebaixado (affirmed 2026-09-28)

- NEVER configurar retentativa automática de testes com falha (affirmed 2026-09-28)

- NEVER usar a mesma chave para cifragem do PAN, HMAC do PAN e fingerprint de idempotência (affirmed 2026-09-28)

- NEVER definir valor padrão para chaves ou segredos em configuração, Dockerfile ou Compose; única exceção: as credenciais fictícias test/test do LocalStack (affirmed 2026-09-28)

- NEVER importar Spring, JPA, AWS SDK ou Jackson em pacotes de domínio (affirmed 2026-09-28)

- NEVER expor no Actuator endpoints além de health, info e prometheus (affirmed 2026-09-28)

- NEVER usar imagens Docker com tag latest ou sem versão fixada (affirmed 2026-09-28)

## Mandated

- ALWAYS proteger invariantes de unicidade com constraints no banco, não apenas com verificação prévia (affirmed 2026-09-27)
- ALWAYS gravar dado de negócio, resultado e outbox na mesma transação (affirmed 2026-09-27)
- ALWAYS tornar consumidores de mensagens idempotentes (affirmed 2026-09-27)
- ALWAYS registrar o histórico de transições de status com ator e instante (affirmed 2026-09-27)
- ALWAYS injetar Clock em regras dependentes de tempo (affirmed 2026-09-27)
- ALWAYS propagar correlationId e contexto de tracing em HTTP e mensagens SQS (affirmed 2026-09-27)
- ALWAYS documentar no README: setup, diagrama Mermaid, decisões técnicas, comportamento sob falha de cada dependência, estratégia de cache e como o sistema impede cartão para produto inexistente ou cancelado (affirmed 2026-09-27)
- ALWAYS manter a collection do Postman sincronizada com os endpoints, incluindo token e Idempotency-Key (affirmed 2026-09-27)

- ALWAYS impedir a inicialização quando uma chave de cifragem do PAN, HMAC do PAN ou fingerprint de idempotência estiver ausente, malformada ou igual a outra dessas chaves (affirmed 2026-09-28)

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- ALWAYS descrever a garantia de desfecho como: cada solicitação tem no máximo um desfecho terminal, que nunca é reavaliado; enquanto não o tiver, permanece rastreável e recuperável, com alerta. NEVER prometer que toda solicitação chega a um desfecho. (learned 2026-09-28) <!-- cid:260928-cardforge-release-1:intent-capture:420f692c2fde011575817829d3b312efe4beda49ec77286fc263d55b866c1f5f -->
- NEVER registrar conformidade integral com PCI-DSS ou LGPD como requisito desta release; ALWAYS tratá-las como controles de aplicação adotados (PAN cifrado, exibição apenas dos 4 últimos dígitos, nada sensível em logs ou mensagens, histórico auditável), sem afirmar conformidade integral. (learned 2026-09-28) <!-- cid:260928-cardforge-release-1:feasibility:e885ea78e73e635fb7e5c813b99f328d34628dcdc05f018983919e0fea6c4ae2 -->
- Itens do núcleo (Q1) que também têm corte de profundidade (Postman, README com runbooks) continuam obrigatórios; o corte só reduz o detalhamento. (learned 2026-09-28) <!-- cid:260928-cardforge-release-1:scope-definition:948b460a9fff4bab37baff135b3f7af16e758369729f62d664ec1adb9a6af219 -->
- ALWAYS escrever ADRs no formato curto, com Contexto, Decisão, Consequências e Alternativas Rejeitadas em uma ou duas linhas cada; exceção: os ADRs de cache, retry/DLQ, outbox e idempotência ficam completos. (learned 2026-09-28) <!-- cid:260928-cardforge-release-1:scope-definition:8e364a11214dc64152475ccbaaa9e00b081ea121737f7c63ab9c570d58d5bbfc -->
- ALWAYS responder 422 com a lista de campos inválidos para toda validação de valor no corpo da requisição, em todos os serviços; 400 apenas para JSON malformado, tipos incompatíveis e headers inválidos. NEVER ignorar em silêncio a tentativa de alterar um atributo imutável: responder 422 com type próprio. (learned 2026-09-29) <!-- cid:260928-cardforge-release-1:contract-design:e68dfdba970025cb99fd8ca889325759a156b07bbe0528a7092b1250aae12357 -->
