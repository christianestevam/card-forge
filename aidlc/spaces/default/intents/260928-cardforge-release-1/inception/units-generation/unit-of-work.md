# Unidades de Trabalho: CardForge Release 1.0

Base: `components.md` e `decisions.md` (Desenho de Domínio), `requirements.md`, `stories.md` e `units-generation-questions.md` (Q1 a Q6 e o plano aprovado).

A U1 é o esqueleto ponta a ponta (`team.md` e `project.md`, Walking Skeleton): passa por todas as etapas por unidade, inclusive Code Generation, antes das demais. Este documento define as unidades; a ordem de construção das demais fica para a Delivery Planning.

## Unidades

| Unit ID | Diretório | Nome | Tipo | Implantação | Complexidade |
|---|---|---|---|---|---|
| U1 | u1-walking-skeleton | walking-skeleton | service | Três containers e a infraestrutura local (Compose) | XL |
| U2 | u2-card-issuance | card-issuance | service | Standalone: container card-service, com `cardforge-platform` embutido | L |
| U3 | u3-cardholder-registration | cardholder-registration | service | Standalone: container cardholder-service, com `cardforge-platform` embutido | L |
| U4 | u4-product-catalog | product-catalog | service | Standalone: container product-service, com `cardforge-platform` embutido (só correlationId e ProblemDetail) | S |
| U5 | u5-consolidated-view | consolidated-view | service | Capacidade implantada dentro do container cardholder-service, não um serviço próprio | M |
| U6 | u6-delivery-docs | delivery-docs | packaging | Artefatos versionados: README, ADRs, OpenAPI publicado, collection do Postman | S |

## U1: walking-skeleton

- **Descrição:** a menor fatia integrada que funciona de ponta a ponta. Ela prova que token, produto, cadastro, emissão pela fila com o cache e consulta consolidada se conectam. É verificada pelo `scripts/smoke-test.sh` contra o ambiente de `docker compose up -d --build`.
- **Responsabilidades:**
  - **Módulo `cardforge-platform`:** `OutboxRelay`, envelope de evento, propagação de correlationId e contexto W3C, e handler base de ProblemDetail. É testado no próprio módulo com Testcontainers, e o ArchUnit impede tipos de domínio nele (ADR-004).
    - Embutido nos três serviços (Q6).
    - O outbox é ativado por auto-configuração condicional, só no cardholder-service e no card-service.
    - O product-service usa apenas a propagação de correlationId e o handler base de ProblemDetail.
  - **Infraestrutura local:**
    - Compose com healthchecks e `service_healthy`;
    - PostgreSQL com um database por serviço, Redis e LocalStack (filas, DLQs e redrive policies);
    - Keycloak com realm versionado como template;
    - container de inicialização que gera os segredos em `./.local/secrets` e o environment do Postman;
    - `.gitignore` com `.env`, `.local/` e `target/`.
  - **CI:** `./mvnw verify` (unitários, integração, cobertura combinada ≥ 80% por módulo, Spotless, SpotBugs com FindSecBugs, ArchUnit) e o job do smoke test. Trivy e Dependabot entram logo depois, sem bloquear o checkpoint do esqueleto.
  - **Caminhos finos nos três serviços,** já corretos segundo as regras NEVER:
    - criar e consultar produto;
    - cadastro com aquisição da chave e recibo;
    - outbox nos dois serviços que publicam;
    - emissão com `issuance_processing`, cache e PAN protegido;
    - aplicação `PENDING` → `ISSUED`;
    - consulta consolidada com o cartão emitido;
    - consulta de cartão por ID.
  - **Segurança base:** Resource Server com JWT nos três serviços e client credentials entre eles.
  - **Observabilidade base:** Actuator restrito a `health`, `info` e `prometheus`; liveness e readiness; logs JSON com `traceId` e `correlationId`.
  - **Documentação da unidade:** README com setup e o diagrama inicial, Postman com o fluxo feliz e OpenAPI do que foi exposto (Q1, Q5).
- **Limites:** não implementa replay, 409 e 422 de idempotência, classes de falha, backoff, lápide do cache, reconciliação, listagens, transições de status nem os demais estados da consulta consolidada. Isso fica nas unidades seguintes, que estendem os mesmos componentes.
- **Notas:** como as regras NEVER valem desde o primeiro commit, o esqueleto não é uma fatia "fina" em esforço. É a maior unidade (XL, `stories.md`).

## U2: card-issuance

- **Descrição:** completa o card-service. É a decisão única da emissão com todas as garantias de integridade e falha.
- **Responsabilidades:**
  - **Componentes:** `IssuanceProcessor`, `ProductEligibility`, `CardLifecycle`, `PanGenerator` e `PanProtector`.
  - **Emissão:** idempotência por `issuance_processing`, republicação do resultado, redecisão após violação de unicidade, colisão de PAN com até 20 tentativas.
  - **Cache e elegibilidade:** regra dos 5 minutos, leitura que não renova `validatedAt`, proteção contra resposta antiga, Redis indisponível.
  - **Classes de falha:** backoff por `ChangeMessageVisibility`, DLQ para mensagens inválidas e fim do orçamento de retry.
  - **Cartão:** status e histórico do cartão, listagem por portador, `panLastFour`.
  - **Proteção de chaves:** validação das chaves do PAN na inicialização.
  - **Métricas da emissão:** emissões por status e motivo, outbox, profundidade das filas, cache e ocupação do BIN com alerta.
  - **Testes críticos:** TC2 a TC7 e TC-PAN.
  - **Documentação:** atualiza README, Postman, OpenAPI e ADRs com o que entrega (Q5).

## U3: cardholder-registration

- **Descrição:** completa o cardholder-service no lado do cadastro, do resultado e da recuperação.
- **Responsabilidades:**
  - **Componentes:** `IdempotencyGuard`, `CardholderRegistry`, `IssuanceRequestTracker` e `IssuanceReconciler`.
  - **Cadastro:** validações completas, classificação das respostas do catálogo (incluindo 202 com alerta para 401, 403 ou contrato inválido), gravação atômica e observação do produto.
  - **Idempotência:** replay, 409 em andamento, 422 de payload diferente, expiração e limpeza, escopo por `client_id`.
  - **Resultado:** aplicação idempotente por estado; contraditório, desconhecido ou inválido vai para a DLQ de forma explícita e imediata.
  - **Reconciliação:** 3 h, 5 min, lotes de 50, 30 min entre tentativas, suspensão após 3.
  - **Status e histórico:** do portador e da solicitação; `X-Actor-Id`.
  - **Proteção de chaves:** validação da chave de fingerprint na inicialização.
  - **Métricas e alertas:** do cadastro e da reconciliação.
  - **Testes críticos:** TC1, TC9, TC10 e TC-IDEM.
  - **Documentação:** atualiza README (incluindo o procedimento de DLQ e reconciliação), Postman, OpenAPI e ADRs (Q5).

## U4: product-catalog

- **Descrição:** completa o product-service.
- **Responsabilidades:**
  - **Componente:** `ProductCatalog`.
  - **Operações:** listagem paginada, atualização descritiva, cancelamento idempotente, `DELETE` inexistente.
  - **Status e histórico:** do produto.
  - **Documentação:** atualiza README, Postman e OpenAPI (Q5).

## U5: consolidated-view

- **Descrição:** a consulta consolidada com os cinco casos de completude.
- **Responsabilidades:**
  - **Componente:** `ConsolidatedViewComposer`.
  - **Origens dos dados:**
    - catálogo com fallback para a observação própria, sinalizada como desatualizada (ADR-002);
    - detalhes do cartão pelo card-service, com a parte indisponível sinalizada (ADR-003).
  - **Casos:** os cinco casos, distinguíveis por campos estruturados, sempre com 200.
  - **Teste crítico:** TC8.
  - **Documentação:** atualiza README, Postman e OpenAPI (Q5).
- **Notas:**
  - U5 é uma capacidade implantada dentro do container do cardholder-service, e não um serviço próprio. O tipo `service` foi mantido porque ela expõe endpoint público próprio e tem as mesmas preocupações de design funcional e de NFR de um serviço (latência, timeouts e degradação das duas chamadas remotas).
  - Usa endpoints entregues por U2 (cartão por ID), U3 (portador e solicitação) e U4 (produto).

## U6: delivery-docs

- **Descrição:** consolida e revisa a documentação que cada unidade foi atualizando. Não acumula conteúdo novo (Q1, Q5).
- **Responsabilidades:**
  - revisão final do README contra a lista obrigatória de `project.md` (Mandated), incluindo limitações conhecidas, débitos dos cortes aplicados e a seção de controles de segurança e privacidade;
  - conferência dos ADRs (formato curto; cache, retry/DLQ, outbox e idempotência completos; Spring Boot 3.5 com plano de migração);
  - conferência do OpenAPI dos três serviços (erros com `type` distinto) e da collection do Postman (replay de `Idempotency-Key`).
- **Notas:** é o lugar onde os cortes de profundidade pré-aprovados (runbooks e Postman) são decididos e registrados como débito, se o prazo apertar.
