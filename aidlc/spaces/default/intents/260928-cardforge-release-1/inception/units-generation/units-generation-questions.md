# Geração de Unidades: perguntas

Base: `components.md` e `decisions.md` (Desenho de Domínio), `requirements.md`, `stories.md` (incluindo a composição do esqueleto em US0.1) e `team-practices.md`.

**O que já está decidido:**
- três serviços implantáveis separadamente, mais o módulo técnico `cardforge-platform` (ADR-004);
- esqueleto primeiro: a primeira unidade precisa ser a fatia ponta a ponta verificada pelo smoke test (Walking Skeleton em `team.md`/`project.md`).

Esta etapa define só o que depende de quê. A ordem de construção e o caminho crítico ficam para a Delivery Planning.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Como dividir o trabalho em unidades?

A. Esqueleto mais unidades por capacidade:
   - **U1 esqueleto**: `cardforge-platform`, Compose, realm do Keycloak, init do LocalStack e dos segredos, CI com `verify` e smoke test, e os caminhos finos dos três serviços (US0.1 a US0.3);
   - **U2 emissão**: card-service completo;
   - **U3 cadastro**: cardholder-service, com resultado e reconciliação;
   - **U4 catálogo**: product-service completo;
   - **U5 consulta consolidada**: cardholder-service;
   - **U6 documentação de entrega**: README, ADRs, contratos publicados e Postman.

   Seis unidades no total.
B. Uma unidade por serviço (platform, product, cardholder, card), sem unidade de esqueleto separada. O esqueleto passaria por três unidades, o que conflita com "a primeira unidade é a fatia ponta a ponta"
C. Not yet defined
X. Other (please specify)

[Answer]: X. A, com dois ajustes: (1) README, collection do Postman, OpenAPI e ADRs são atualizados em cada unidade com o que ela entrega; a U6 apenas consolida e revisa, sem conteúdo acumulado; (2) as alterações de status com histórico e as métricas ficam na unidade dona da entidade ou da garantia (cartão na U2, portador e solicitação na U3, produto na U4). **Mode:** guided

## Q2. Onde ficam a documentação, o Postman e a publicação dos contratos?

Contexto: a Definição de Escopo pôs observabilidade e documentação no fim da sequência, mas disse que as partes mínimas obrigatórias (correlationId, README, Postman) acompanham os incrementos.

A. Cada unidade entrega a sua parte mínima (seção do README, requisições no Postman, OpenAPI do que expõe); a U6 consolida e fecha o que falta (README completo, ADRs, seção de controles, runbooks enxutos, revisão do Postman)
B. Tudo concentrado na U6, no fim
C. Not yet defined
X. Other (please specify)

[Answer]: B. Tudo concentrado na U6, no fim **Mode:** guided

## Q3. Métricas e alertas: em qual unidade?

A. Cada unidade implementa as métricas e alertas dos seus componentes (ex.: a U2 implementa emissões, cache e BIN); a U1 traz a base (Actuator restrito, correlationId, logs JSON)
B. Uma unidade só de observabilidade no fim
C. Not yet defined
X. Other (please specify)

[Answer]: A. Cada unidade implementa as métricas e alertas dos seus componentes; a U1 traz a base (Actuator restrito, correlationId, logs JSON) **Mode:** guided

## Q4. Modelo de implantação

A. Três containers independentes (product-service, cardholder-service, card-service); `cardforge-platform` embutido como biblioteca em cardholder-service e card-service
B. Outro (especifique em X)
X. Other (please specify)

[Answer]: B. Outro (especificação pendente) **Mode:** guided

## Q5. (Acompanhamento) Documentação: por unidade ou só na U6?

Contexto: na Q1 você disse que README, Postman, OpenAPI e ADRs são atualizados em cada unidade e que a U6 apenas consolida e revisa. Na Q2 escolheu "tudo concentrado na U6, no fim". As duas respostas se contradizem.

A. Vale a Q1: cada unidade atualiza README, Postman, OpenAPI e ADRs com o que entrega; a U6 só consolida e revisa
B. Vale a Q2: tudo é escrito na U6, no fim
X. Other (please specify)

[Answer]: A. Vale a Q1: cada unidade atualiza README, Postman, OpenAPI e ADRs com o que entrega; a U6 só consolida e revisa (substitui a Q2) **Mode:** guided

## Q6. (Acompanhamento) Qual modelo de implantação?

Contexto: na Q4 você escolheu "Outro", sem especificar.

A. Três containers independentes, com `cardforge-platform` embutido como biblioteca em cardholder-service e card-service (a opção A original)
B. Três containers, com `cardforge-platform` embutido nos três serviços (inclusive o product-service, que hoje não publica eventos)
X. Other (please specify)

[Answer]: B. Três containers, com `cardforge-platform` embutido nos três serviços; o product-service usa apenas a propagação de correlationId e o handler base de ProblemDetail; o outbox é ativado só no cardholder-service e no card-service, por auto-configuração condicional (substitui a Q4; corrigido no gate) **Mode:** gate feedback

## Consolidated Summary Confirmation

Resumo das respostas e do plano de decomposição:

- Unidades (Q1): seis, esqueleto primeiro:
  - U1 `walking-skeleton` (service): `cardforge-platform`, Compose, realm do Keycloak, init do LocalStack e dos segredos, CI com `verify` e smoke test, caminhos finos dos três serviços;
  - U2 `card-issuance` (service): card-service completo, incluindo status e histórico do cartão e as métricas da emissão;
  - U3 `cardholder-registration` (service): cardholder-service com cadastro, idempotência, resultado, reconciliação, status e histórico do portador e da solicitação, e as métricas correspondentes;
  - U4 `product-catalog` (service): product-service completo, com status e histórico do produto;
  - U5 `consolidated-view` (service): consulta consolidada; é uma capacidade implantada dentro do container do cardholder-service, não um serviço próprio (R-01 aceita);
  - U6 `delivery-docs` (packaging): consolidação e revisão de README, ADRs, OpenAPI e Postman.
- Status, histórico e métricas (Q1, Q3): na unidade dona da entidade ou da garantia; a U1 traz a base de observabilidade.
- Documentação (Q1, Q5): cada unidade atualiza README, Postman, OpenAPI e ADRs com o que entrega; a U6 só consolida e revisa.
- Implantação (Q6): três containers; `cardforge-platform` embutido nos três serviços; o product-service usa só correlationId e o handler base de ProblemDetail; o outbox é ativado só no cardholder-service e no card-service, por auto-configuração condicional.
- ADR-004 (Desenho de Domínio): atualizado para refletir a Q6 corrigida (platform nos três serviços, outbox condicional).
- Ordem de construção (encaminhada à Delivery Planning, não definida aqui): risco primeiro, U1 → U2 → U3 → U4 → U5 → U6, em sequência.
- Dependências (só topologia): U2, U3 e U4 dependem de U1; U5 depende de U2, U3 e U4; U6 depende de U2 a U5. U2, U3 e U4 podem ser feitas em paralelo.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
