# Desenho de Contratos: perguntas

Base: `unit-of-work.md` e `unit-of-work-dependency.md` (pontos de integração), `components.md`, `requirements.md`, `stories.md` (questões encaminhadas para o Desenho de Contratos) e `engineering-standards.md` (APIs REST, envelope de eventos).

**Já decidido, não é perguntado:**
- REST versionado em `/api/v1`, JSON camelCase, `ProblemDetail`;
- filas SQS com o envelope `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId` e trace;
- payloads mínimos das duas filas;
- `Idempotency-Key` UUID;
- paginação padrão 20, máximo 100;
- máscara do CPF;
- `X-Actor-Id`.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Formato dos endpoints de mudança de status (produto, portador, cartão)

A. Ações nomeadas: `POST /api/v1/cards/{id}/block`, `/unblock`, `/cancel` (e equivalentes); o pedido para o status atual responde 200 com o estado atual
B. `PATCH /api/v1/cards/{id}` com `{"status": "BLOCKED"}`; o pedido para o status atual responde 200
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q2. Como o cardholder-service (consulta consolidada) e o card-service consultam os outros serviços?

A. Pelos mesmos endpoints públicos (`GET /api/v1/products/{id}`, `GET /api/v1/cards/{id}`), com token de client credentials e o escopo de leitura correspondente; sem endpoints internos
B. Endpoints internos separados (ex.: `/internal/...`), com escopo próprio
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q3. Para onde aponta o `Location` do 202 do cadastro?

A. Para a consulta consolidada do portador (`/api/v1/cardholders/{id}/overview`), onde o desfecho aparece
B. Para o recurso do portador (`/api/v1/cardholders/{id}`)
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q4. O que a consulta de portador mostra da data de nascimento?

A. Não mostra (omitida em todas as respostas)
B. Só o ano de nascimento
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q5. Qual formato de CPF é aceito na entrada do cadastro?

A. Só dígitos ou com a pontuação padrão (`123.456.789-09`); qualquer outro formato é 422
B. Só dígitos (11)
C. Not yet defined
X. Other (please specify)

[Answer]:X — A, e a regra vale para toda validação de campo do corpo: 422 com a lista de campos inválidos; 400 fica para JSON malformado, tipos incompatíveis e headers inválidos.

## Q6. Extras de contrato propostos pelo designer (select all that apply)

A. `Retry-After` no 409 de "requisição em andamento"
B. `requestedAt` (instante do aceite) na consulta consolidada, para o gateway saber há quanto tempo a solicitação está pendente
C. None
X. Other (please specify)

[Answer]:A, B

## Q7. Identificador dos tipos de erro (`type` do ProblemDetail)

A. URI estável por causa, no formato `https://cardforge.rpe.com.br/problems/<causa-em-kebab-case>` (ex.: `.../cpf-already-registered`, `.../idempotency-request-in-progress`), documentada no OpenAPI; não precisa ser resolvível
B. URN, no formato `urn:cardforge:problem:<causa>`
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q8. Política de versões e mudanças incompatíveis

A. REST: a versão fica no caminho (`/v1`), mudanças aditivas são permitidas na mesma versão e os clientes ignoram campos desconhecidos; mudança incompatível exige `/v2` em paralelo. Eventos: o consumidor aceita `eventVersion` conhecido e manda versão desconhecida para a DLQ; o produtor só publica uma nova versão depois que o consumidor a suporta
B. Outra política (especifique em X)
X. Other (please specify)

[Answer]:A

## Q9. (Acompanhamento) A regra da Q5 também muda os casos do catálogo que hoje dão 400?

Contexto: pela Q5, toda validação de campo do corpo passa a ser 422, e 400 fica só para JSON malformado, tipos incompatíveis e headers inválidos. Mas os requisitos e as histórias aprovados dizem:
- BIN fora do formato (7 ou 9 dígitos, com letras) gera 400 (FR1.1, AC1.1.2);
- uma atualização de produto que contém o campo `bin` gera 400 (FR1.4, AC1.3.2).

A. Sim: os dois casos passam a ser 422; a regra da Q5 vale para todos os serviços, e o contrato corrige FR1.1/FR1.4 e AC1.1.2/AC1.3.2
B. Parcial: BIN fora do formato passa a 422; o campo `bin` na atualização continua 400 (campo não permitido é tratado como corpo malformado)
C. Não: o catálogo mantém 400 nesses dois casos; a regra da Q5 vale só para o cadastro
X. Other (please specify)

[Answer]: X — A, e o campo bin na atualização responde 422 com um type próprio (ex.: .../bin-immutable), porque viola a regra de imutabilidade do BIN; ele nunca é ignorado em silêncio. **Mode:** guided

## Q10. Correções pedidas depois da primeira revisão (R-01, R-02, R-03)

A. Aplicar as três correções como descritas
X. Other (please specify)

[Answer]: X — R-01: criar respostas reutilizáveis Unauthorized (401, com o header WWW-Authenticate declarado) e Forbidden (403), referenciar as duas em todos os endpoints dos três serviços e incluir WWW-Authenticate na tabela de headers. R-02: registrar a convenção: o OpenAPI publicado é gerado pelo springdoc a partir do código; ProblemDetail, EventEnvelope e PageMetadata são classes do cardforge-platform, então cada serviço expõe esses schemas no próprio OpenAPI a partir da mesma fonte, sem arquivo YAML compartilhado. O contract-summary.md é a referência de desenho que o código deve seguir. R-03: declarar bin em UpdateProductRequest como readOnly, com a nota: a presença do campo no corpo é detectada e rejeitada com 422 bin-immutable, nunca ignorada. **Mode:** feedback

## Consolidated Summary Confirmation

Resumo das respostas:

- Status (Q1): ações nomeadas (`POST .../{id}/block`, `/unblock`, `/cancel`, e `/cancel` no produto); pedido para o status atual responde 200 com o estado atual.
- Chamadas entre serviços (Q2): pelos endpoints públicos (`GET /api/v1/products/{id}`, `GET /api/v1/cards/{id}`), com client credentials e o escopo de leitura; sem endpoints internos.
- `Location` do cadastro (Q3): `/api/v1/cardholders/{id}/overview`.
- Data de nascimento (Q4): omitida em todas as respostas.
- Validação (Q5, Q9): CPF aceito só com dígitos ou na pontuação padrão; toda validação de campo do corpo, em todos os serviços, responde 422 com a lista de campos; 400 só para JSON malformado, tipos incompatíveis e headers inválidos; BIN fora do formato passa a 422 e o campo `bin` na atualização responde 422 com `type` próprio (`.../bin-immutable`), nunca ignorado. Corrige FR1.1, FR1.4, AC1.1.2 e AC1.3.2.
- Extras (Q6): `Retry-After` no 409 em andamento; `requestedAt` na consulta consolidada.
- Tipos de erro (Q7): URI `https://cardforge.rpe.com.br/problems/<causa>`, documentada no OpenAPI.
- Correções (Q10): respostas Unauthorized (401 com WWW-Authenticate) e Forbidden (403) em todos os endpoints e WWW-Authenticate na tabela de headers; convenção: OpenAPI publicado gerado pelo springdoc a partir do código, schemas compartilhados vindos das classes do cardforge-platform, sem YAML compartilhado, com o contract-summary.md como referência de desenho; bin em UpdateProductRequest como readOnly, detectado e rejeitado com 422 bin-immutable.
- Versões (Q8): `/v1` com mudanças aditivas e clientes tolerantes; `/v2` em paralelo para incompatíveis; eventos com `eventVersion`, versão desconhecida vai para a DLQ, produtor só publica versão nova depois que o consumidor a suporta.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
