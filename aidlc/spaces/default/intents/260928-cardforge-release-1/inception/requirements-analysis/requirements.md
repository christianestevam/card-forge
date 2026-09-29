# Requisitos: CardForge Release 1.0

**Fontes:**
- `intent-statement.md` (Captura de Intenção);
- `scope-document.md` e `intent-backlog.md` (Definição de Escopo);
- `team-practices.md` (Descoberta de Práticas);
- `product-brief.md` e `engineering-standards.md`;
- as regras de `project.md`;
- `requirements-analysis-questions.md` (Q1 a Q8).

**Profundidade:** Standard. **Tipo:** produto novo (greenfield), multisserviço, domínio de complexidade alta (concorrência, mensageria, dados de pagamento).

## Análise da intenção

A RPE precisa de uma plataforma própria em que o cadastro do portador e a emissão do cartão aconteçam separadamente, para que uma adesão nunca dependa da emissão estar disponível. A garantia de negócio (`intent-statement.md`) é que nenhuma solicitação aceita se perde: cada solicitação tem no máximo um desfecho terminal, que nunca é reavaliado, e enquanto não o tiver permanece rastreável e recuperável, com alerta.

O sucesso da release é medido por:
- zero cartões duplicados;
- zero emissões para produto inexistente ou cancelado além da janela de 5 minutos;
- cadastro aceito mesmo com o catálogo, a mensageria ou o cache indisponíveis;
- comportamento sob falha de cada dependência provado por testes automatizados.

A release é entregue até 05/10/2026, com a sequência e a política de cortes definidas em `scope-document.md`.

## Identificação das regras de negócio

Os IDs seguem o formato `BR{grupo}.{seq}`, com o ID do brief entre parênteses (Q1). Grupos: Produto → 1, Portador → 2, Cadastro → 3, Emissão → 4, Cartão → 5, Consulta consolidada → 6.

| ID | Brief | Regra |
|---|---|---|
| BR1.1 | BR-P1 | `bin` com exatamente 8 dígitos numéricos, único no catálogo e imutável |
| BR1.2 | BR-P2 | Produto: `ACTIVE` → `CANCELED`; `CANCELED` é terminal |
| BR1.3 | BR-P3 | Cancelar o produto impede novas emissões; cartões já emitidos continuam válidos |
| BR2.1 | BR-H1 | CPF obrigatório, dígitos verificadores válidos, sequências repetidas rejeitadas, armazenado só com dígitos |
| BR2.2 | BR-H2 | CPF único em toda a base; portador cancelado não pode ser recadastrado na R1 |
| BR2.3 | BR-H3 | Idade mínima de 18 anos; data de nascimento no futuro ou há mais de 120 anos é inválida |
| BR2.4 | BR-H4 | Nome completo com 3 a 120 caracteres, com nome e sobrenome |
| BR2.5 | BR-H5 | Portador: `ACTIVE` ↔ `BLOCKED`; `ACTIVE` ou `BLOCKED` → `CANCELED` (terminal); nasce `ACTIVE` |
| BR2.6 | BR-H6 | O cadastro informa o produto desejado (`productId`) |
| BR3.1 | BR-R1 | `Idempotency-Key` obrigatório; mesma chave e mesmo cliente devolvem o mesmo recibo por 24 h |
| BR3.2 | BR-R2 | Mesma chave com payload diferente é rejeitada; repetição com a original em andamento recebe conflito |
| BR3.3 | BR-R3 | Produto comprovadamente inexistente ou cancelado no cadastro é rejeitado e nada é criado |
| BR3.4 | BR-R4 | Produto não confirmável por falha técnica: cadastro aceito, validação na emissão |
| BR3.5 | BR-R5 | Indisponibilidade do catálogo ou da mensageria não impede nem perde um cadastro |
| BR4.1 | BR-I1 | Emissão só com observação `ACTIVE` do produto de no máximo 5 minutos; o card-service decide |
| BR4.2 | BR-I2 | Cada solicitação tem no máximo um desfecho terminal, nunca reavaliado |
| BR4.3 | BR-I3 | No máximo um cartão não cancelado por portador e produto |
| BR4.4 | BR-I4 | Falhas de negócio encerram como `FAILED` sem retentativa; falhas técnicas retentadas por ~2 h, depois retidas para recuperação, com alerta |
| BR4.5 | BR-I5 | O resultado da emissão fica visível na consulta do portador |
| BR5.1 | BR-C1 | PAN de 16 dígitos: `bin` + 7 dígitos aleatórios + dígito de Luhn; único |
| BR5.2 | BR-C2 | Validade de 5 anos a partir da emissão (mês e ano) |
| BR5.3 | BR-C3 | Cartão nasce `ACTIVE`; `ACTIVE` ↔ `BLOCKED`; `ACTIVE` ou `BLOCKED` → `CANCELED` (terminal) |
| BR5.4 | BR-C4 | CVV não é gerado nem armazenado |
| BR5.5 | BR-C5 | APIs exibem apenas `panLastFour` |
| BR6.1 | BR-V1 | A consulta consolidada retorna portador, situação da emissão, cartão (quando emitido) e produto |
| BR6.2 | BR-V2 | A resposta separa estado de negócio de completude da informação (quatro casos) |
| BR6.3 | BR-V3 | Indisponibilidade de uma dependência não derruba a consulta inteira |

## Requisitos funcionais

Todos os endpoints exigem OAuth2 (FR9) e respondem erros em `application/problem+json` (FR10).

### FR1. Catálogo de produtos (`product-service`)

- **FR1.1:** O sistema deve criar um produto com `name`, `description` e `bin`, no status `ACTIVE`, respondendo 201 com `Location`. Regras: BR1.1.
  - *Aceite:* dado um `bin` válido e inédito, quando o produto é criado, então retorna 201 e o produto nasce `ACTIVE`.
  - *Erros:* `bin` fora do formato gera 400; `bin` já existente gera 409, garantido por constraint no banco.
- **FR1.2:** O sistema deve consultar um produto por ID.
  - *Erro:* ID inexistente gera 404.
- **FR1.3:** O sistema deve listar produtos com paginação: padrão de 20 itens e máximo de 100 (Q2).
  - *Erro:* `size` acima de 100 gera 400.
- **FR1.4:** O sistema deve atualizar só `name` e `description` de um produto `ACTIVE`. O `bin` é imutável (BR1.1).
  - *Erros:* produto `CANCELED` gera 409 (Q4); tentativa de alterar `bin` gera 400.
- **FR1.5:** O sistema deve cancelar um produto `ACTIVE` e registrar a transição no histórico (FR7.3). Regras: BR1.2, BR1.3.
  - *Erro:* cancelar um produto já `CANCELED` gera 409.
- **FR1.6:** O sistema não deve oferecer exclusão física de produtos.

### FR2. Cadastro do portador (`cardholder-service`)

- **FR2.1:** O `POST /api/v1/cardholders` deve exigir o header `Idempotency-Key` no formato UUID (qualquer versão), com ou sem aspas (Q8).
  - *Erros:* header ausente ou malformado gera 400.
- **FR2.2:** O sistema deve validar CPF, data de nascimento, nome e `productId`. Regras: BR2.1, BR2.3, BR2.4, BR2.6.
  - *Erro:* violação de regra gera 422, com a lista de campos.
- **FR2.3:** O sistema deve garantir CPF único em toda a base, inclusive contra portadores cancelados, com constraint no banco. Regra: BR2.2.
  - *Erro:* CPF duplicado gera 409.
- **FR2.4:** O sistema deve consultar o catálogo antes e fora da transação de escrita, com orçamento curto e sem retentativa.
  - Produto comprovadamente inexistente ou `CANCELED` gera 422 e nada é criado (BR3.3).
  - Timeout, 5xx ou conexão recusada fazem o cadastro seguir e a validação ficar para a emissão (BR3.4).
  - 401, 403 e erro de contrato na chamada ao catálogo geram alerta de configuração e nunca são tratados como produto inexistente.
- **FR2.5:** O sistema deve gravar, na mesma transação, a chave de idempotência, o portador (`ACTIVE`), a solicitação de emissão (`PENDING`) e o evento de outbox. Responde 202 com o recibo de aceite (`cardholderId`, `issuanceRequestId`) e `Location`, só depois do commit. Regras: BR3.5, BR2.5.
- **FR2.6:** O sistema deve tratar a repetição da mesma chave, pelo mesmo `client_id`, por 24 h após o aceite. Regras: BR3.1, BR3.2.
  - Mesmo payload (comparado por fingerprint com HMAC): replay do recibo original, 202 com o mesmo corpo e `Idempotent-Replayed: true`.
  - Payload diferente gera 422.
  - Requisição original ainda em andamento além do tempo limite de lock gera 409.
- **FR2.7:** O sistema deve remover periodicamente as chaves de idempotência expiradas.
- **FR2.8:** O sistema deve consultar um portador por ID, com o CPF mascarado no formato `***.456.789-**` (Q5) e sem expor a data de nascimento além do necessário.
  - *Erro:* ID inexistente gera 404.

### FR3. Publicação de eventos (`cardholder-service` e `card-service`)

- **FR3.1:** Cada serviço que publica deve fazê-lo apenas por outbox. Um worker agendado seleciona lotes de até 10 eventos com `FOR UPDATE SKIP LOCKED`, envia por `SendMessageBatch` com timeout curto mantendo o lock e marca como enviados apenas os aceitos individualmente.
- **FR3.2:** As mensagens devem usar o envelope `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, contexto de tracing e `payload`, nas filas `card-issuance-requested` e `card-issuance-completed`, cada uma com DLQ e redrive policy.
  - Solicitação: `issuanceRequestId`, `cardholderId`, `productId`.
  - Resultado: `issuanceRequestId`, `status`, `failureReason`, `cardId`.
  - Sem CPF, data de nascimento ou PAN.
- **FR3.3:** O sistema deve expor métricas de pendências do outbox e da idade do evento mais antigo.

### FR4. Emissão (`card-service`)

- **FR4.1:** O consumidor deve ser idempotente. Para uma solicitação que já tem resultado em `issuance_processing`, ele nunca a reavalia: recoloca o resultado persistido no outbox e só então confirma a mensagem. Regra: BR4.2.
  - *Aceite:* dada uma solicitação `FAILED` reentregue, quando processada de novo, então o desfecho continua `FAILED` e o mesmo resultado é republicado.
- **FR4.2:** O sistema deve autorizar a emissão apenas com uma observação `ACTIVE` do produto de no máximo 5 minutos. Regras: BR4.1, BR1.3.
  - O registro do cache só é usado se o seu `validatedAt` tiver até 5 minutos; senão o catálogo é consultado, com uma tentativa por processamento.
  - Ler o cache nunca renova `validatedAt`.
  - Catálogo respondendo `ACTIVE` grava um novo `validatedAt`; `CANCELED` ou 404 removem qualquer registro `ACTIVE`, que não pode ser restaurado por uma resposta mais antiga.
  - Redis indisponível: o catálogo é consultado com timeout e a degradação é registrada.
  - Redis e catálogo indisponíveis, sem registro elegível: falha técnica.
- **FR4.3:** O sistema deve encerrar como `FAILED`, com o motivo `PRODUCT_NOT_FOUND`, `PRODUCT_CANCELED` ou `NON_CANCELED_CARD_ALREADY_EXISTS`, gravando o resultado e o outbox na mesma transação e sem retentativa. Regras: BR4.4, BR4.3.
- **FR4.4:** O sistema deve garantir no máximo um cartão não cancelado por portador e produto, com índice único parcial, e um cartão por solicitação. Regras: BR4.3, BR4.2.
  - *Aceite:* dadas duas emissões concorrentes para o mesmo portador e produto, então apenas uma resulta em cartão.
- **FR4.5:** O sistema deve gerar o cartão segundo BR5.1 a BR5.4:
  - PAN com `SecureRandom`, cifrado com AES-256-GCM e versão da chave;
  - unicidade por `pan_hmac`, com `ON CONFLICT` e até 20 tentativas;
  - esgotar as tentativas é falha técnica com alerta;
  - validade de 5 anos (mês e ano), status `ACTIVE`, sem CVV.
- **FR4.6:** O sistema deve gravar cartão, resultado `ISSUED` e outbox na mesma transação. Em falha técnica, faz rollback sem gravar resultado.
- **FR4.7:** O sistema deve classificar as falhas de processamento em quatro grupos:
  - negócio: grava `FAILED` e confirma a mensagem;
  - transitória: não confirma e aplica backoff por `ChangeMessageVisibility` (primeira em 30 s, dobrando, jitter de ±20%, teto rígido de 5 min depois do jitter);
  - mensagem inválida ou versão não suportada: DLQ e alerta;
  - configuração: alerta, sem mascarar como indisponibilidade.
- **FR4.8:** A emissão não depende do status do portador: um portador `BLOCKED` ou `CANCELED` depois do cadastro não impede a emissão pendente (Q3). É uma limitação conhecida da R1, registrada no README, a tratar junto com a cascata de status na R1.1.
- **FR4.9:** O sistema deve expor métricas do uso de cada BIN, com alerta quando a ocupação chegar a 70%.

### FR5. Aplicação do resultado (`cardholder-service`)

- **FR5.1:** O consumidor de resultados deve ser idempotente por estado. Regras: BR4.2, BR4.5.
  - Se a solicitação está `PENDING`, aplica o resultado.
  - Se chega o mesmo resultado terminal de novo, nada muda.
  - Se chega um resultado terminal contraditório, não altera nada e gera alerta.

### FR6. Reconciliação (`cardholder-service`)

- **FR6.1:** Um job agendado deve republicar solicitações `PENDING` com mais de 3 h. Parâmetros (Q7), todos configuráveis:
  - execução a cada 5 min;
  - lotes de até 50;
  - intervalo mínimo de 30 min entre tentativas da mesma solicitação;
  - contador de reconciliações e instante da última tentativa por solicitação.

  Regra: BR4.4.
- **FR6.2:** Depois de 3 reconciliações sem desfecho, o sistema deve suspender a republicação automática daquela solicitação, marcá-la para intervenção e alertar.
- **FR6.3:** A republicação de uma solicitação já decidida no `card-service` deve levar à republicação do resultado persistido (FR4.1), sem emitir outro cartão.
  - *Aceite:* dado um resultado perdido, quando a reconciliação republica a solicitação, então o resultado original é reaplicado.

### FR7. Status e histórico

- **FR7.1:** O sistema deve permitir mudar o status do portador conforme BR2.5.
  - *Erro:* transição inválida gera 409.
- **FR7.2:** O sistema deve permitir mudar o status do cartão conforme BR5.3.
  - *Erro:* transição inválida gera 409.
- **FR7.3:** Toda transição de status de produto, portador e cartão deve ser registrada na mesma transação da mudança, com entidade, transição, instante e ator, sem dados pessoais. O ator é o `client_id` do token mais o header opcional `X-Actor-Id` (até 100 caracteres, sem dados pessoais) (Q6).
- **FR7.4:** O sistema deve permitir consultar um cartão por ID e listar os cartões de um portador com paginação (FR1.3), exibindo apenas `panLastFour` (BR5.5).
- **FR7.5:** O sistema não deve oferecer exclusão física de portadores nem de cartões.

### FR8. Consulta consolidada (`cardholder-service`)

- **FR8.1:** O sistema deve retornar o portador, a situação da emissão, o cartão (quando emitido) e o produto (BR6.1). A resposta separa o estado de negócio da completude da informação (BR6.2):
  - `PENDING` sem cartão: ausência esperada, resposta completa;
  - `FAILED` sem cartão: desfecho de negócio, com o motivo, resposta completa;
  - `ISSUED` com o card-service indisponível: a parte do cartão aparece como indisponível, sinalizada;
  - produto vindo de observação antiga: dado disponível, sinalizado como desatualizado, com o instante da observação.
- **FR8.2:** A falha de uma dependência não deve derrubar a consulta inteira (BR6.3).

### FR9. Segurança de acesso

- **FR9.1:** Todo endpoint deve exigir JWT, validando emissor, audiência e escopo: `products:read|write`, `cardholders:read|write`, `cards:read|write`.
  - *Erros:* sem token, ou com emissor ou audiência errados, gera 401; escopo insuficiente gera 403.
- **FR9.2:** As chamadas entre serviços devem usar client credentials.

### FR10. Contratos de API

- **FR10.1:** Recursos no plural e versionados (`/api/v1/...`), JSON em camelCase, erros em `ProblemDetail` com tratamento global e os códigos definidos em `engineering-standards.md`. Entidades JPA nunca são expostas.
- **FR10.2:** Cada serviço deve publicar o seu contrato OpenAPI.

### FR11. Ambiente, verificação e documentação

- **FR11.1:** O ambiente completo (PostgreSQL com um database por serviço, Redis, LocalStack, Keycloak e os três serviços) deve subir com `docker compose up -d --build`. Requisitos:
  - healthchecks em todos os containers, com dependências por `service_healthy`;
  - segredos gerados localmente em `./.local/secrets` (`team-practices.md`).
- **FR11.2:** O `scripts/smoke-test.sh` deve executar o fluxo ponta a ponta do esqueleto e falhar em qualquer desvio.
- **FR11.3:** A collection do Postman deve cobrir os endpoints, com token e `Idempotency-Key`, na profundidade da política de cortes.
- **FR11.4:** O README deve trazer:
  - setup e diagrama Mermaid;
  - decisões técnicas;
  - o comportamento sob falha de cada dependência;
  - a estratégia de cache;
  - como o sistema impede cartão para produto inexistente ou cancelado;
  - as limitações conhecidas (incluindo a de FR4.8);
  - os débitos dos cortes aplicados.
- **FR11.5:** Os ADRs devem seguir o formato curto, com cache, retry/DLQ, outbox e idempotência completos.

## Requisitos não funcionais

| ID | Requisito | Critério | Verificação nesta release |
|---|---|---|---|
| NFR1 | Latência do cadastro | p95 < 300 ms; o cadastro não espera a emissão | Meta de projeto, não medida (sem teste de carga) |
| NFR2 | Tempo de emissão | p95 < 5 s e p99 < 60 s do cadastro ao cartão, com dependências saudáveis | Meta de projeto, não medida |
| NFR3 | Latência das consultas | p95 < 200 ms para cartão e produto | Meta de projeto, não medida |
| NFR4 | Volume e escala | ~5 mil cadastros/dia, picos de 50/s; serviços sem estado, escaláveis horizontalmente até 100/s | Por design: nenhum estado em memória que impeça várias instâncias |
| NFR5 | Disponibilidade | 99,9% por serviço | Meta de produção; depende da infraestrutura de produção e não é verificável nesta release |
| NFR6 | Integridade | Nenhuma solicitação aceita se perde por falha entre banco e mensageria; cada solicitação tem no máximo um desfecho terminal, nunca reavaliado; enquanto não o tiver, permanece rastreável e recuperável, com alerta | Testes críticos 1, 2, 3, 4, 9 e 10 |
| NFR7 | Cancelamento de produto | Bloqueia novas emissões em até 5 minutos | Testes críticos 6 e 7 |
| NFR8 | Retomada após falha | Com a dependência de volta, as emissões retidas retomam em poucos minutos (teto do backoff: 5 min) | Teste crítico 6 e teste de backoff |
| NFR9 | Proteção de dados | Controles de aplicação alinhados a PCI-DSS e LGPD, sem afirmar conformidade integral: PAN cifrado (AES-256-GCM com versão da chave) e HMAC com chave separada; só `panLastFour`; CPF mascarado; nada sensível em logs ou mensagens; histórico auditável; chaves distintas, fora do repositório, validadas na inicialização | Testes de não vazamento em logs e mensagens; teste de inicialização sem chave |
| NFR10 | Resiliência | Toda chamada remota (HTTP, Redis, SQS, banco) com timeout configurado; nenhuma chamada remota dentro de transação de escrita; circuit breaker por dependência | Testes com WireMock (timeout, 5xx, conexão recusada, 401/403, contrato inválido) |
| NFR11 | Observabilidade | Liveness sem dependências externas, readiness com o banco; métricas mínimas da política de cortes; logs JSON com `traceId` e `correlationId`; contexto W3C e `correlationId` propagados em HTTP e atributos SQS; Actuator só com `health`, `info` e `prometheus` | Testes de integração e smoke test |
| NFR12 | Qualidade | `./mvnw verify` verde localmente e no CI, dependendo só de Docker; cobertura ≥ 80% de linhas por módulo (unitários + integração); os dez testes críticos, mais colisão de PAN e concorrência na `Idempotency-Key`; cada regra `BR*` rastreável a pelo menos um teste | CI (`team-practices.md`) |

## Restrições

- **Prazo e capacidade:** entrega até 05/10/2026, por uma pessoa em dedicação parcial; só a profundidade dos itens da política de cortes pode ser reduzida (`scope-document.md`).
- **Stack:** decidida em `project.md` (Tech Stack e Decided): Java 21, Spring Boot 3.5.x, Maven multimódulo, PostgreSQL, Redis, SQS, Keycloak local.
- **Integrações:** o único cliente é o gateway de onboarding; a autorização por recurso é dele.
- **Ambiente:** só local, em Docker Compose; a infraestrutura AWS de produção fica fora.
- **Práticas:** as de `team-practices.md` (trunk em `main`, esqueleto primeiro, testes críticos escritos antes, CI com o esqueleto) e as regras `ALWAYS`/`NEVER` de `project.md`.

## Premissas

| ID | Premissa | Justificativa |
|---|---|---|
| A1 | O gateway envia `Idempotency-Key` como UUID e, quando tiver, `X-Actor-Id` sem dados pessoais | Q6, Q8; validar com o time do gateway pelos contratos OpenAPI |
| A2 | A infraestrutura de produção futura oferece serviços compatíveis (SQS Standard com DLQ, Redis, PostgreSQL, IdP OAuth2) | brief §8; `raid-log.md` A2 |
| A3 | Docker disponível na máquina de desenvolvimento e no CI | `raid-log.md` A1 |

## Fora do escopo

- Tudo o que está na seção 9 do brief, inclusive a cascata de status entre portador e cartões.
- Infraestrutura AWS de produção.
- Conformidade integral com PCI-DSS ou LGPD.
- Teste de carga, interface gráfica, dashboards de métricas e servidores de observabilidade no Compose.
- Detalhes em `scope-document.md`.

## Questões em aberto para as próximas etapas

- **Consulta consolidada:** a forma exata da resposta, com os indicadores de completude e de dado desatualizado, fica para o Desenho de Contratos.
- **Endpoints de status:** o formato (ex.: `PATCH` com o status alvo ou ações nomeadas) também fica para o Desenho de Contratos.
- **Idade mínima da reconciliação:** 3 h está acima do orçamento nominal de retry (~2 h mais jitter). Confirmar a margem no Desenho Funcional contra o `maxReceiveCount` configurado.
