# Backlog da Intenção: CardForge Release 1.0

Proto-unidades priorizadas por MoSCoW, com a sequência definida na Q5. Base: `intent-statement.md`, `feasibility-assessment.md`, `constraint-register.md` e `scope-definition-questions.md`.

**Regra de corte:** nenhum item Must é cortado. Só a profundidade marcada na coluna "Corte permitido" pode sair, na ordem da política de cortes do `scope-document.md`.

## Backlog priorizado

| ID | Proto-unidade | Prioridade | Seq. | Regras e testes cobertos | Corte permitido |
|---|---|---|---|---|---|
| IB-01 | Esqueleto ponta a ponta: ambiente com um comando, token no Keycloak, criação de produto, cadastro com `Idempotency-Key`, emissão assíncrona com validação do produto pelo cache, consulta consolidada com o cartão, `scripts/smoke-test.sh` | Must | 1 | Walking Skeleton de `project.md` | Nenhum |
| IB-02 | Emissão: consumo da solicitação; regra dos 5 minutos com cache; geração do PAN (BIN, conta aleatória, Luhn, unicidade, até 20 tentativas); resultado terminal persistido; outbox do resultado; retry com backoff; DLQ com redrive policy; consumo idempotente | Must | 2 | BR-I1 a BR-I4, BR-C1 a BR-C5; testes críticos 2 a 7; colisão de PAN | Nenhum |
| IB-03 | Cadastro: `Idempotency-Key` (replay, 409, 422); validações de CPF, idade e nome; rejeição imediata de produto inexistente ou cancelado; aceite sob falha do catálogo ou da mensageria; outbox da solicitação; consumidor de resultados idempotente por estado | Must | 3 | BR-H1 a BR-H6, BR-R1 a BR-R5, BR-I5; teste crítico 1; concorrência na `Idempotency-Key` | Nenhum |
| IB-04 | Reconciliação: republicação de solicitações sem desfecho mais antigas que o orçamento de retry, em lotes limitados, com contador, suspensão e alerta após N tentativas | Must | 3 | BR-I4; testes críticos 9 e 10 | Nenhum |
| IB-05 | Catálogo completo: listagem paginada, atualização descritiva, cancelamento (criar e consultar já vêm do IB-01) | Must | 4 | BR-P1 a BR-P3 | Nenhum |
| IB-06 | Status e histórico: transições de portador e cartão com histórico (ator e instante); consulta de cartão por ID e listagem por portador | Must | 4 | BR-H5, BR-C3, BR-C5 | Nenhum |
| IB-07 | Consulta consolidada completa: quatro estados de completude, dependência indisponível sinalizada, dado antigo sinalizado com o instante da observação | Must | 4 | BR-V1 a BR-V3; teste crítico 8 | Nenhum |
| IB-08 | Segurança transversal: OAuth2 com emissor, audiência e escopos; PAN cifrado com versão da chave e HMAC; CPF mascarado; nada sensível em logs ou mensagens | Must | Transversal (a partir de 1) | brief §6; C-R1 a C-R5 | Nenhum |
| IB-09 | Contratos de API: tratamento global de exceções com ProblemDetail e códigos HTTP semânticos; OpenAPI por serviço | Must | 4 | engineering-standards (APIs REST) | Nenhum |
| IB-10 | Observabilidade: propagação de correlationId e contexto W3C em HTTP e SQS; health e readiness; logs estruturados; métricas | Must (métricas mínimas) | 5 | brief §7; `project.md` Mandated | Corte 1: métricas além do mínimo |
| IB-11 | Documentação: README completo; ADRs curtos, com cache, retry/DLQ, outbox e idempotência completos; runbooks de DLQ e reconciliação | Must | 5 | `project.md` Mandated e Way of Working | Corte 2: runbooks só como procedimento enxuto no README |
| IB-12 | Collection do Postman sincronizada, com token e `Idempotency-Key` | Must | 5 | `project.md` Mandated | Corte 3: Postman essencial |
| IB-13 | CI com `./mvnw verify` e cobertura mínima de 80% de linhas por módulo de serviço (excluindo a classe main e as de configuração) | Must | A definir (ver premissas) | `org.md` Testing Posture; `project.md` Testing Posture | Nenhum |

## Could (além do mínimo, só se sobrar tempo)

| ID | Item | Observação |
|---|---|---|
| IB-C1 | Métricas completas: tempo de emissão, colisões de PAN, estado detalhado dos circuit breakers | O que o corte 1 retira |
| IB-C2 | Runbooks operacionais em documentos separados | O que o corte 2 retira |
| IB-C3 | Postman com todos os códigos de erro de cada endpoint | O que o corte 3 retira |

## Won't (fora desta release)

Seção 9 do brief; infraestrutura AWS de produção; conformidade integral com PCI-DSS ou LGPD; teste de carga; interface gráfica; dashboards de métricas; servidores de observabilidade no Compose (detalhes em `scope-document.md`).

## Dependências entre proto-unidades

- IB-02, IB-03 e IB-04 dependem de IB-01, que estabelece o fluxo e o ambiente.
- IB-04 depende de IB-03, porque republica solicitações do cadastro.
- IB-03 consome o resultado publicado por IB-02.
- IB-07 depende de IB-02, IB-03 e IB-06.
- IB-08 atravessa todas as proto-unidades.
- IB-10, IB-11 e IB-12 vêm por último na sequência. As partes mínimas obrigatórias (correlationId, README, Postman) acompanham os incrementos para não ficarem para o fim sem margem.

## Assumptions & Open Questions

- Posição do CI (IB-13) na sequência: não foi definida nas respostas. Sugestão: junto do esqueleto (seq. 1). Confirmar na Delivery Planning.
