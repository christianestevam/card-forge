# Resumo de Contratos: CardForge Release 1.0

Base:
- `unit-of-work.md` e `unit-of-work-dependency.md` (pontos de integração entre U1 e U6);
- `components.md` (formato das entidades);
- `requirements.md` (FR1 a FR11, NFR);
- `stories.md` (critérios de aceite);
- `engineering-standards.md`;
- `contract-design-questions.md` (Q1 a Q9).

Os contratos abaixo são a fonte que o OpenAPI de cada serviço deve publicar (FR10.2). Os campos JSON seguem camelCase e os identificadores de código ficam em inglês.

## Contratos

| # | Provedor | Consumidor | Mecanismo | Dono |
|---|---|---|---|---|
| C1 | product-service (U4, caminho fino na U1) | External: gateway de onboarding; cardholder-service (U3, U5); card-service (U2) | REST/HTTP síncrono, JWT | U4 |
| C2 | cardholder-service (U3, U5) | External: gateway de onboarding | REST/HTTP síncrono, JWT | U3 (U5 dono do `/overview`) |
| C3 | card-service (U2) | External: gateway de onboarding; cardholder-service (U5) | REST/HTTP síncrono, JWT | U2 |
| C4 | cardholder-service (U3) | card-service (U2) | Evento SQS `card-issuance-requested` | U3 |
| C5 | card-service (U2) | cardholder-service (U3) | Evento SQS `card-issuance-completed` | U2 |
| C6 | `cardforge-platform` (U1) | Os três serviços | Esquema compartilhado: `ProblemDetail` e envelope de evento | U1 |

As chamadas entre serviços usam os mesmos endpoints públicos, com token de client credentials e o escopo de leitura correspondente. Não há endpoints internos (Q2).

## C6: Convenções compartilhadas (erros, headers e envelope)

**Status HTTP** (Q5, Q9; FR10.1):

| Status | Quando |
|---|---|
| 400 | JSON malformado, tipos incompatíveis, header obrigatório ausente ou inválido (`Idempotency-Key` fora de UUID, `X-Actor-Id` com mais de 100 caracteres), parâmetro de consulta inválido (`size` > 100) |
| 401, 403 | Token ausente ou inválido; escopo insuficiente |
| 404 | Recurso inexistente |
| 405 | `DELETE` em produto, portador ou cartão |
| 409 | Conflito de unicidade, requisição idempotente em andamento, transição inválida, produto cancelado não editável |
| 422 | Toda validação de campo do corpo, regra de negócio e chave de idempotência com payload diferente |
| 503 | Dependência indisponível sem degradação possível |

**Tipos de erro** (Q7). O `type` é `https://cardforge.rpe.com.br/problems/<causa>`, estável e documentado no OpenAPI com a ação esperada do cliente. Não precisa ser resolvível.

| Causa (`type`) | Status | Serviço | Ação esperada do cliente |
|---|---|---|---|
| `malformed-request` | 400 | Todos | Corrigir o JSON, os tipos ou os parâmetros |
| `invalid-header` | 400 | Todos | Corrigir `Idempotency-Key` ou `X-Actor-Id` |
| `unauthorized` | 401 | Todos | Renovar o token (`WWW-Authenticate` presente) |
| `forbidden` | 403 | Todos | Corrigir os escopos do cliente |
| `resource-not-found` | 404 | Todos | Verificar o ID |
| `validation-failed` | 422 | Todos | Corrigir os campos listados em `invalidFields` |
| `bin-already-registered` | 409 | product | Usar outro BIN |
| `bin-immutable` | 422 | product | Remover `bin` da atualização (nunca ignorado em silêncio) |
| `product-canceled-read-only` | 409 | product | Desistir: produto cancelado não é editável |
| `invalid-status-transition` | 409 | Todos | Desistir: transição não permitida a partir do status atual |
| `cpf-already-registered` | 409 | cardholder | Desistir e informar o parceiro; não repetir |
| `idempotency-request-in-progress` | 409 | cardholder | Repetir depois, com a mesma chave, respeitando `Retry-After` |
| `idempotency-key-payload-mismatch` | 422 | cardholder | Gerar uma nova chave (erro do cliente) |
| `product-not-found` | 422 | cardholder | Informar que o produto não existe |
| `product-canceled` | 422 | cardholder | Informar que o produto foi descontinuado |
| `dependency-unavailable` | 503 | Todos | Repetir depois |

Nenhum `ProblemDetail` contém CPF, data de nascimento, PAN ou o payload original (AC8.1.4). 401 e 403 também saem em `ProblemDetail` (AC7.1.1, AC7.1.2), declarados em todos os endpoints pelas respostas reutilizáveis `Unauthorized` e `Forbidden`.

**Headers:**

| Header | Direção | Regra |
|---|---|---|
| `Authorization: Bearer <JWT>` | Requisição | Obrigatório em todo endpoint; emissor, audiência e escopo validados |
| `Idempotency-Key` | Requisição | Obrigatório só no `POST /api/v1/cardholders`; UUID de qualquer versão, com ou sem aspas |
| `X-Actor-Id` | Requisição | Opcional, até 100 caracteres, sem dados pessoais; vai para o histórico de transições |
| `X-Correlation-Id` | Requisição e resposta | Opcional na requisição (gerado se ausente); sempre ecoado na resposta |
| `traceparent` / `tracestate` | Requisição | Contexto W3C propagado |
| `Idempotent-Replayed: true` | Resposta | Só no replay do cadastro |
| `Retry-After` | Resposta | No 409 `idempotency-request-in-progress` (Q6) |
| `WWW-Authenticate` | Resposta | Em todo 401 (`Bearer`, com `error` e `error_description` conforme RFC 6750) |

```yaml
# C6 – esquemas compartilhados (cardforge-platform)
ProblemDetail:
  type: object
  required: [type, title, status]
  properties:
    type: { type: string, format: uri, example: "https://cardforge.rpe.com.br/problems/validation-failed" }
    title: { type: string }
    status: { type: integer }
    detail: { type: string, description: "Nunca contém CPF, data de nascimento, PAN ou o payload original" }
    instance: { type: string }
    correlationId: { type: string }
    invalidFields:
      type: array
      description: "Presente em validation-failed; lista todas as violações da requisição"
      items:
        type: object
        required: [field, rule]
        properties:
          field: { type: string, example: "cpf" }
          rule: { type: string, example: "CPF_CHECK_DIGITS" }
          message: { type: string }
EventEnvelope:
  type: object
  required: [eventId, eventType, eventVersion, occurredAt, correlationId, payload]
  properties:
    eventId: { type: string, format: uuid, description: "Identidade da linha de outbox; muda a cada publicação" }
    eventType: { type: string }
    eventVersion: { type: integer, minimum: 1 }
    occurredAt: { type: string, format: date-time }
    correlationId: { type: string }
    payload: { type: object }
  # traceparent e tracestate viajam como atributos da mensagem SQS, não no corpo
PageMetadata:
  type: object
  required: [page, size, totalElements, totalPages]
  properties:
    page: { type: integer, minimum: 0 }
    size: { type: integer, minimum: 1, maximum: 100, default: 20 }
    totalElements: { type: integer }
    totalPages: { type: integer }
```

## C1: product-service

Escopos: `products:read`, `products:write`.

```yaml
openapi: 3.1.0
info: { title: CardForge product-service, version: "1.0" }
paths:
  /api/v1/products:
    post:
      summary: Criar produto (nasce ACTIVE)
      security: [{ oauth2: [products:write] }]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: "#/components/schemas/CreateProductRequest" } } }
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "201": { description: Criado, headers: { Location: { schema: { type: string } } }, content: { application/json: { schema: { $ref: "#/components/schemas/Product" } } } }
        "400": { $ref: "#/components/responses/Problem" }
        "409": { description: "bin-already-registered", $ref: "#/components/responses/Problem" }
        "422": { description: "validation-failed (bin fora de 8 dígitos numéricos, nome ausente)", $ref: "#/components/responses/Problem" }
    get:
      summary: Listar produtos
      security: [{ oauth2: [products:read] }]
      parameters:
        - { name: page, in: query, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, schema: { type: integer, minimum: 1, maximum: 100, default: 20 }, description: "size > 100 gera 400" }
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/ProductPage" } } } }
        "400": { $ref: "#/components/responses/Problem" }
  /api/v1/products/{productId}:
    parameters: [{ name: productId, in: path, required: true, schema: { type: string, format: uuid } }]
    get:
      summary: Consultar produto (também usado por cardholder-service e card-service)
      security: [{ oauth2: [products:read] }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Product" } } } }
        "404": { $ref: "#/components/responses/Problem" }
    patch:
      summary: Atualizar nome e descrição de produto ACTIVE
      security: [{ oauth2: [products:write] }]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: "#/components/schemas/UpdateProductRequest" } } }
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Product" } } } }
        "404": { $ref: "#/components/responses/Problem" }
        "409": { description: "product-canceled-read-only", $ref: "#/components/responses/Problem" }
        "422": { description: "validation-failed; bin-immutable quando o corpo contém bin", $ref: "#/components/responses/Problem" }
  /api/v1/products/{productId}/cancel:
    parameters: [{ name: productId, in: path, required: true, schema: { type: string, format: uuid } }]
    post:
      summary: Cancelar produto (ACTIVE -> CANCELED); idempotente se já CANCELED
      security: [{ oauth2: [products:write] }]
      parameters: [{ name: X-Actor-Id, in: header, schema: { type: string, maxLength: 100 } }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { description: "Estado atual (também quando já estava CANCELED, sem nova entrada no histórico)", content: { application/json: { schema: { $ref: "#/components/schemas/Product" } } } }
        "400": { $ref: "#/components/responses/Problem" }
        "404": { $ref: "#/components/responses/Problem" }
components:
  schemas:
    CreateProductRequest:
      type: object
      required: [name, bin]
      properties:
        name: { type: string, minLength: 1, maxLength: 120 }
        description: { type: string, maxLength: 500 }
        bin: { type: string, pattern: "^[0-9]{8}$" }
    UpdateProductRequest:
      type: object
      properties:
        name: { type: string, minLength: 1, maxLength: 120 }
        description: { type: string, maxLength: 500 }
        bin:
          type: string
          readOnly: true
          description: "Somente leitura: a presença do campo no corpo é detectada e rejeitada com 422 bin-immutable, nunca ignorada"
    Product:
      type: object
      required: [id, name, bin, status, createdAt, updatedAt]
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        description: { type: string }
        bin: { type: string }
        status: { type: string, enum: [ACTIVE, CANCELED] }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    ProductPage:
      type: object
      properties:
        content: { type: array, items: { $ref: "#/components/schemas/Product" } }
        page: { $ref: "cardforge-platform#/PageMetadata" }
  responses:
    Problem: { description: "application/problem+json", content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } } }
    Unauthorized:
      description: "401 unauthorized (application/problem+json)"
      headers:
        WWW-Authenticate: { schema: { type: string }, description: "Bearer, com error e error_description conforme RFC 6750" }
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
    Forbidden:
      description: "403 forbidden (application/problem+json)"
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
```

## C2: cardholder-service

Escopos: `cardholders:read`, `cardholders:write`.

```yaml
openapi: 3.1.0
info: { title: CardForge cardholder-service, version: "1.0" }
paths:
  /api/v1/cardholders:
    post:
      summary: Cadastrar portador e solicitar emissão
      description: >
        202 significa "aceito e rastreável", não "cartão emitido"; o desfecho só é conhecido pela
        consulta consolidada (sem notificação ativa na R1). Catálogo com timeout, 5xx, conexão
        recusada, 401, 403 ou contrato inválido: aceita e adia a validação para a emissão.
        Limitação conhecida: se o recibo for perdido e a repetição ocorrer depois de 24 h, a resposta
        é 409 cpf-already-registered e não há busca por CPF na R1. Status do portador não afeta a
        emissão pendente na R1 (FR4.8).
      security: [{ oauth2: [cardholders:write] }]
      parameters:
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, format: uuid }, description: "UUID de qualquer versão, com ou sem aspas; só um cadastro aceito consome a chave; replay por 24 h, por client_id" }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: "#/components/schemas/RegisterCardholderRequest" } } }
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "202":
          description: Aceito (ou replay, com Idempotent-Replayed true)
          headers:
            Location: { schema: { type: string }, description: "/api/v1/cardholders/{cardholderId}/overview" }
            Idempotent-Replayed: { schema: { type: boolean } }
          content: { application/json: { schema: { $ref: "#/components/schemas/RegistrationReceipt" } } }
        "400": { description: "malformed-request, invalid-header", $ref: "#/components/responses/Problem" }
        "409": { description: "cpf-already-registered; idempotency-request-in-progress (com Retry-After)", headers: { Retry-After: { schema: { type: integer } } }, $ref: "#/components/responses/Problem" }
        "422": { description: "validation-failed; product-not-found; product-canceled; idempotency-key-payload-mismatch", $ref: "#/components/responses/Problem" }
  /api/v1/cardholders/{cardholderId}:
    parameters: [{ name: cardholderId, in: path, required: true, schema: { type: string, format: uuid } }]
    get:
      summary: Consultar portador (CPF mascarado, sem data de nascimento)
      security: [{ oauth2: [cardholders:read] }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Cardholder" } } } }
        "404": { $ref: "#/components/responses/Problem" }
  /api/v1/cardholders/{cardholderId}/block:
    post: { $ref: "#/components/pathItems/StatusAction" }
  /api/v1/cardholders/{cardholderId}/unblock:
    post: { $ref: "#/components/pathItems/StatusAction" }
  /api/v1/cardholders/{cardholderId}/cancel:
    post: { $ref: "#/components/pathItems/StatusAction" }
  /api/v1/cardholders/{cardholderId}/overview:
    parameters: [{ name: cardholderId, in: path, required: true, schema: { type: string, format: uuid } }]
    get:
      summary: Consulta consolidada (U5)
      description: "Sempre 200 quando o portador existe; os cinco casos são distinguíveis pelos campos de disponibilidade"
      security: [{ oauth2: [cardholders:read] }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/CardholderOverview" } } } }
        "404": { $ref: "#/components/responses/Problem" }
components:
  pathItems:
    StatusAction:
      summary: "Transição de status do portador; pedido para o status atual responde 200 sem nova entrada no histórico; a partir de CANCELED gera 409 invalid-status-transition"
      security: [{ oauth2: [cardholders:write] }]
      parameters: [{ name: X-Actor-Id, in: header, schema: { type: string, maxLength: 100 } }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Cardholder" } } } }
        "400": { $ref: "#/components/responses/Problem" }
        "404": { $ref: "#/components/responses/Problem" }
        "409": { $ref: "#/components/responses/Problem" }
  schemas:
    RegisterCardholderRequest:
      type: object
      required: [cpf, fullName, birthDate, productId]
      properties:
        cpf: { type: string, description: "11 dígitos ou no formato 000.000.000-00; outro formato ou dígito verificador inválido gera 422" }
        fullName: { type: string, minLength: 3, maxLength: 120, description: "Nome e sobrenome" }
        birthDate: { type: string, format: date, description: "18 a 120 anos na data do cadastro" }
        productId: { type: string, format: uuid }
    RegistrationReceipt:
      type: object
      required: [cardholderId, issuanceRequestId]
      properties:
        cardholderId: { type: string, format: uuid }
        issuanceRequestId: { type: string, format: uuid }
    Cardholder:
      type: object
      required: [id, maskedCpf, fullName, productId, status, createdAt, updatedAt]
      properties:
        id: { type: string, format: uuid }
        maskedCpf: { type: string, example: "***.456.789-**" }
        fullName: { type: string }
        productId: { type: string, format: uuid }
        status: { type: string, enum: [ACTIVE, BLOCKED, CANCELED] }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    CardholderOverview:
      type: object
      required: [cardholder, issuance, card, product]
      properties:
        cardholder: { $ref: "#/components/schemas/Cardholder" }
        issuance:
          type: object
          required: [issuanceRequestId, status, requestedAt]
          properties:
            issuanceRequestId: { type: string, format: uuid }
            status: { type: string, enum: [PENDING, ISSUED, FAILED] }
            failureReason: { type: string, enum: [PRODUCT_NOT_FOUND, PRODUCT_CANCELED, NON_CANCELED_CARD_ALREADY_EXISTS] }
            cardId: { type: string, format: uuid }
            requestedAt: { type: string, format: date-time }
            decidedAt: { type: string, format: date-time }
        card:
          type: object
          required: [availability]
          properties:
            availability: { type: string, enum: [NOT_APPLICABLE, AVAILABLE, UNAVAILABLE], description: "NOT_APPLICABLE para PENDING e FAILED (ausência esperada, card-service não é chamado)" }
            data: { $ref: "#/components/schemas/CardView" }
        product:
          type: object
          required: [availability]
          properties:
            availability: { type: string, enum: [CURRENT, STALE, UNAVAILABLE], description: "STALE = observação que não veio do catálogo nesta requisição; UNAVAILABLE = sem observação utilizável" }
            observedAt: { type: string, format: date-time }
            data:
              type: object
              properties:
                id: { type: string, format: uuid }
                name: { type: string }
                bin: { type: string }
                status: { type: string, enum: [ACTIVE, CANCELED] }
    CardView:
      type: object
      properties:
        id: { type: string, format: uuid }
        panLastFour: { type: string, pattern: "^[0-9]{4}$" }
        expirationDate: { type: string, pattern: "^[0-9]{4}-[0-9]{2}$" }
        status: { type: string, enum: [ACTIVE, BLOCKED, CANCELED] }
  responses:
    Problem: { description: "application/problem+json", content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } } }
    Unauthorized:
      description: "401 unauthorized (application/problem+json)"
      headers:
        WWW-Authenticate: { schema: { type: string }, description: "Bearer, com error e error_description conforme RFC 6750" }
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
    Forbidden:
      description: "403 forbidden (application/problem+json)"
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
```

**Os cinco casos da consulta consolidada** (BR6.2 ampliada pela Q13 das Histórias). A combinação de campos é única em cada linha (AC4.2.5):

| Caso | `issuance.status` | `card.availability` | `product.availability` |
|---|---|---|---|
| Pendente | `PENDING` | `NOT_APPLICABLE` | `CURRENT` ou `STALE` |
| Falha de negócio | `FAILED` (com `failureReason`) | `NOT_APPLICABLE` | `CURRENT` ou `STALE` |
| Emitido, card-service fora | `ISSUED` (com `cardId`) | `UNAVAILABLE` | `CURRENT` ou `STALE` |
| Produto desatualizado | qualquer | qualquer | `STALE` (com `observedAt`) |
| Produto sem observação | qualquer | qualquer | `UNAVAILABLE` |

## C3: card-service

Escopos: `cards:read`, `cards:write`.

```yaml
openapi: 3.1.0
info: { title: CardForge card-service, version: "1.0" }
paths:
  /api/v1/cards/{cardId}:
    parameters: [{ name: cardId, in: path, required: true, schema: { type: string, format: uuid } }]
    get:
      summary: Consultar cartão (só panLastFour; também usado pela consulta consolidada)
      security: [{ oauth2: [cards:read] }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Card" } } } }
        "404": { $ref: "#/components/responses/Problem" }
  /api/v1/cards:
    get:
      summary: Listar cartões de um portador
      security: [{ oauth2: [cards:read] }]
      parameters:
        - { name: cardholderId, in: query, required: true, schema: { type: string, format: uuid } }
        - { name: page, in: query, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/CardPage" } } } }
        "400": { $ref: "#/components/responses/Problem" }
  /api/v1/cards/{cardId}/block:
    post: { $ref: "#/components/pathItems/StatusAction" }
  /api/v1/cards/{cardId}/unblock:
    post: { $ref: "#/components/pathItems/StatusAction" }
  /api/v1/cards/{cardId}/cancel:
    post: { $ref: "#/components/pathItems/StatusAction" }
components:
  pathItems:
    StatusAction:
      summary: "Transição de status do cartão; pedido para o status atual responde 200 sem nova entrada no histórico; a partir de CANCELED gera 409 invalid-status-transition"
      security: [{ oauth2: [cards:write] }]
      parameters: [{ name: X-Actor-Id, in: header, schema: { type: string, maxLength: 100 } }]
      responses:
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Card" } } } }
        "400": { $ref: "#/components/responses/Problem" }
        "404": { $ref: "#/components/responses/Problem" }
        "409": { $ref: "#/components/responses/Problem" }
  schemas:
    Card:
      type: object
      required: [id, cardholderId, productId, issuanceRequestId, panLastFour, expirationDate, status, createdAt, updatedAt]
      properties:
        id: { type: string, format: uuid }
        cardholderId: { type: string, format: uuid }
        productId: { type: string, format: uuid }
        issuanceRequestId: { type: string, format: uuid }
        panLastFour: { type: string, pattern: "^[0-9]{4}$" }
        expirationDate: { type: string, pattern: "^[0-9]{4}-[0-9]{2}$", description: "Mês e ano (YearMonth)" }
        status: { type: string, enum: [ACTIVE, BLOCKED, CANCELED] }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    CardPage:
      type: object
      properties:
        content: { type: array, items: { $ref: "#/components/schemas/Card" } }
        page: { $ref: "cardforge-platform#/PageMetadata" }
  responses:
    Problem: { description: "application/problem+json", content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } } }
    Unauthorized:
      description: "401 unauthorized (application/problem+json)"
      headers:
        WWW-Authenticate: { schema: { type: string }, description: "Bearer, com error e error_description conforme RFC 6750" }
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
    Forbidden:
      description: "403 forbidden (application/problem+json)"
      content: { application/problem+json: { schema: { $ref: "cardforge-platform#/ProblemDetail" } } }
```

## C4 e C5: eventos de emissão

```yaml
asyncapi: 3.0.0
info: { title: CardForge issuance events, version: "1.0" }
channels:
  cardIssuanceRequested:
    address: card-issuance-requested
    description: "SQS Standard; DLQ card-issuance-requested-dlq com redrive policy (maxReceiveCount ~29); retenção da DLQ maior que a da fila"
    messages:
      issuanceRequested: { $ref: "#/components/messages/IssuanceRequested" }
  cardIssuanceCompleted:
    address: card-issuance-completed
    description: "SQS Standard; DLQ card-issuance-completed-dlq com redrive policy; retenção da DLQ maior que a da fila"
    messages:
      issuanceCompleted: { $ref: "#/components/messages/IssuanceCompleted" }
operations:
  publishIssuanceRequested:
    action: send
    channel: { $ref: "#/channels/cardIssuanceRequested" }
    description: "cardholder-service via outbox (cadastro e reconciliação); entrega pelo menos uma vez"
  consumeIssuanceRequested:
    action: receive
    channel: { $ref: "#/channels/cardIssuanceRequested" }
    description: >
      card-service; idempotente por issuanceRequestId; confirma só após o resultado persistido;
      solicitação já decidida republica o resultado sem reavaliar; falha transitória: sem confirmação e
      backoff por ChangeMessageVisibility; mensagem inválida ou eventVersion desconhecida: DLQ + alerta.
  publishIssuanceCompleted:
    action: send
    channel: { $ref: "#/channels/cardIssuanceCompleted" }
    description: "card-service via outbox; cada republicação tem novo eventId e o mesmo issuanceRequestId"
  consumeIssuanceCompleted:
    action: receive
    channel: { $ref: "#/channels/cardIssuanceCompleted" }
    description: >
      cardholder-service; idempotente por estado; confirma só após persistir; resultado contraditório,
      issuanceRequestId desconhecido, mensagem inválida ou eventVersion desconhecida: alerta e envio
      explícito e imediato para a DLQ, preservando a mensagem original.
components:
  messages:
    IssuanceRequested:
      name: IssuanceRequested
      headers: { $ref: "#/components/schemas/MessageAttributes" }
      payload:
        allOf:
          - { $ref: "cardforge-platform#/EventEnvelope" }
          - type: object
            properties:
              eventType: { const: IssuanceRequested }
              eventVersion: { const: 1 }
              payload:
                type: object
                required: [issuanceRequestId, cardholderId, productId]
                properties:
                  issuanceRequestId: { type: string, format: uuid }
                  cardholderId: { type: string, format: uuid }
                  productId: { type: string, format: uuid }
    IssuanceCompleted:
      name: IssuanceCompleted
      headers: { $ref: "#/components/schemas/MessageAttributes" }
      payload:
        allOf:
          - { $ref: "cardforge-platform#/EventEnvelope" }
          - type: object
            properties:
              eventType: { const: IssuanceCompleted }
              eventVersion: { const: 1 }
              payload:
                type: object
                required: [issuanceRequestId, status]
                properties:
                  issuanceRequestId: { type: string, format: uuid }
                  status: { type: string, enum: [ISSUED, FAILED] }
                  failureReason: { type: string, enum: [PRODUCT_NOT_FOUND, PRODUCT_CANCELED, NON_CANCELED_CARD_ALREADY_EXISTS], description: "Só em FAILED" }
                  cardId: { type: string, format: uuid, description: "Só em ISSUED" }
  schemas:
    MessageAttributes:
      type: object
      description: "Atributos SQS; nunca contêm CPF, data de nascimento ou PAN"
      properties:
        correlationId: { type: string }
        traceparent: { type: string }
        tracestate: { type: string }
```

## Regras de propriedade e de mudança (Q8)

- Convenção de publicação (Q10): o OpenAPI publicado por cada serviço é gerado pelo springdoc a partir do código. `ProblemDetail`, `EventEnvelope` e `PageMetadata` são classes do `cardforge-platform`, então cada serviço expõe esses schemas no próprio OpenAPI a partir da mesma fonte, sem arquivo YAML compartilhado. As referências `cardforge-platform#/...` deste documento indicam essa fonte; o `contract-summary.md` é a referência de desenho que o código deve seguir, e o AsyncAPI de C4 e C5 é mantido junto ao código dos serviços donos.
- **Donos dos contratos:**
  - C1: U4;
  - C2: U3, e U5 para `/overview`;
  - C3 e C5: U2;
  - C4: U3;
  - C6: U1 (`cardforge-platform`).

  O dono publica o OpenAPI ou o AsyncAPI e mantém os testes de contrato.
- **REST:**
  - a versão fica no caminho (`/v1`);
  - mudanças aditivas (campo novo opcional, `type` novo de erro, endpoint novo) são permitidas na mesma versão, e os clientes ignoram campos desconhecidos;
  - uma mudança incompatível exige `/v2` em paralelo até a migração do consumidor.
- **Eventos:**
  - o consumidor aceita apenas `eventVersion` conhecido; versão desconhecida vai para a DLQ com alerta;
  - o produtor só publica uma versão nova depois que o consumidor a suporta;
  - campos novos opcionais podem ser adicionados sem mudar a versão.
- **Mudança incompatível:** exige ADR curto e atualização da collection do Postman e do OpenAPI na mesma unidade.

## Correções em artefatos anteriores

A decisão das Q5 e Q9 muda estes critérios. Code Generation e Build and Test devem usar a versão abaixo.

| Artefato | Antes | Agora |
|---|---|---|
| `requirements.md` FR1.1 / `stories.md` AC1.1.2 | BIN fora do formato gera 400 | 422 `validation-failed` |
| `requirements.md` FR1.4 / `stories.md` AC1.3.2 | Campo `bin` na atualização gera 400 | 422 `bin-immutable`, nunca ignorado |
| `stories.md` AC2.4.3 | Data de nascimento conforme o Desenho de Contratos | Omitida em todas as respostas (Q4) |

## Questões em aberto

| Contrato | Questão | Bloqueia |
|---|---|---|
| C2, C3, C1 | Timeouts e orçamento de latência de cada chamada síncrona entre serviços (a consulta consolidada faz até duas chamadas em série) | U5 (NFR Design) |
| C4, C5 | Visibility timeout inicial, retenção das filas e das DLQs | U2, U3 (NFR Design) |
| C2 | Valor de `Retry-After` e do `lock_timeout` da chave de idempotência | U3 (Desenho Funcional) |
| C6 | Audiência (`aud`) de cada serviço no realm do Keycloak | U1 (Infrastructure Design) |
