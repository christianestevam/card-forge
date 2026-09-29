# Histórias de Usuário: CardForge Release 1.0

Base: `requirements.md` (FR1 a FR11, NFR1 a NFR12, regras `BR{grupo}.{seq}`), `personas.md`, `team-practices.md` e `user-stories-questions.md` (Q1 a Q13). Esta versão integra as contribuições do designer, do desenvolvedor e do engenheiro de qualidade, que estão em `contributions/`.

## Convenções

- **Agrupamento:** por fluxo de negócio, como no backlog (Q2).
- **Comportamento sob falha:** aparece como critério de aceite da história de negócio correspondente (Q3).
- **Testes críticos:** a sigla `TC` identifica os testes críticos obrigatórios de `project.md`, `TC1` a `TC10`.
  - `TC-PAN`: colisão forçada de PAN.
  - `TC-IDEM`: concorrência na mesma `Idempotency-Key`.

  Cada critério `TC*` declara a asserção que prova o comportamento. Assim, o teste escrito antes da implementação falha pelo motivo certo (`team.md`).
- **"Gera alerta":** significa duas coisas observáveis.
  - Um contador Micrometer específico do tipo de alerta é incrementado e aparece em `/actuator/prometheus`.
  - É emitido um log estruturado com um código de evento estável, sem dados pessoais.

  Os nomes ficam para o NFR Design.
- **Tempo controlado:** todo critério com janela de tempo é verificado com `Clock` injetado, testando os dois lados da fronteira. "No máximo 5 minutos" é inclusivo: uma idade de `validatedAt` ≤ 5 min é elegível.
- **Testes de emissão independentes:** as histórias dos grupos 3, 4 e 6 são demonstráveis sem o cadastro. A solicitação é injetada em `card-issuance-requested` e o resultado em `card-issuance-completed` (LocalStack). O catálogo e o card-service são simulados com WireMock.
- **Erros:**
  - Todo erro sai em `application/problem+json`, inclusive 401 e 403.
  - Causas diferentes que usam o mesmo status HTTP têm `type` distintos (US8.1).
  - Nenhum erro devolve CPF, data de nascimento ou PAN.
- **Prioridade:** todas as histórias são Must. Os cortes pré-aprovados reduzem só a profundidade de US6.2, US6.3 e US8.3 (`scope-document.md`).
- **Autenticação:** todos os endpoints exigem token válido (US7.1). Por isso esse requisito não se repete nos critérios de cada história.

## Mapa das histórias

| Grupo | Histórias | Persona | Sequência (backlog) |
|---|---|---|---|
| 0. Esqueleto e habilitação | US0.1 a US0.3 | Gateway, Operador | 1 |
| 1. Catálogo | US1.1 a US1.4 | Analista de catálogo | 1 (criar e consultar) e 4 |
| 2. Cadastro | US2.1 a US2.4 | Gateway | 1 e 3 |
| 3. Emissão | US3.1 a US3.6 | Gateway, Analista, Operador | 1 e 2 |
| 4. Resultado e consulta | US4.1 e US4.2 | Gateway | 3 e 4 |
| 5. Status e histórico | US5.1 a US5.3 | Gateway, Segurança | 4 |
| 6. Operação e recuperação | US6.1 a US6.3 | Operador | 2 e 3 |
| 7. Proteção de dados e acesso | US7.1 e US7.2 | Gateway, Segurança | Transversal |
| 8. Entrega e documentação | US8.1 a US8.4 | Gateway, Operador, Segurança | 5 |

---

## Grupo 0. Esqueleto e habilitação

### US0.1: Fluxo ponta a ponta demonstrável

**Como** gateway de onboarding, **quero** que exista, desde o primeiro incremento, um fluxo completo de token, produto, cadastro, emissão e consulta, **para** ter desde o início um fluxo real e estável contra o qual integrar e testar.

- **AC0.1.1:**
  - Dado o ambiente subido com `docker compose up -d --build`,
  - quando `scripts/smoke-test.sh` é executado,
  - então ele obtém um token no Keycloak, cria um produto, cadastra um portador com `Idempotency-Key`, consulta a visão consolidada em intervalos até ela mostrar `ISSUED` (limite de 60 s) e confirma o cartão emitido.
- **AC0.1.2:**
  - Dado o fluxo do smoke test,
  - quando a emissão é processada,
  - então a chave `cardforge:product:v1:{id}` existe no Redis com o `validatedAt` da observação. Isso prova que a validação passou pelo cache.
- **AC0.1.3:**
  - Dado qualquer passo com resultado diferente do esperado, ou o limite de 60 s estourado,
  - quando o smoke test roda,
  - então ele termina com código de saída diferente de zero e indica o passo que falhou.

**Composição:** o esqueleto já segue todas as regras NEVER: outbox nos dois serviços, `issuance_processing`, confirmação só depois do resultado persistido e validação de 5 minutos. Por isso ele contém versões finas, já corretas, destas histórias:

| História | O que entra no esqueleto | O que fica para a própria história |
|---|---|---|
| US1.1 e US1.2 | Criar e consultar por ID | Listagem, atualização e cancelamento |
| US2.1 | Caminho feliz com aquisição da chave e recibo | Replay, 409 e 422 (US2.3); validações completas |
| Outbox | Worker nos dois serviços | Métricas; TC1 |
| US3.1 | Emissão com `issuance_processing` e cache | Colisão de PAN, lápide, classes de falha (US3.2 a US3.5) |
| US4.1 | `PENDING` → `ISSUED` | Resultado repetido ou contraditório |
| US4.2 | Consulta com cartão emitido | Os demais estados (TC8) |
| US5.2 | Consulta de cartão por ID | Listagem e transições |
| US7.1 | JWT nos três serviços e client credentials | Testes de 401 e 403 por escopo |

Prioridade: Must. Tamanho relativo: GG. Requisitos: FR11.1, FR11.2.

### US0.2: Ambiente local com um comando e sem segredos versionados

**Como** operador de sustentação, **quero** subir todo o ambiente com um único comando e sem nenhum segredo no repositório, **para** reproduzir o sistema em qualquer máquina com segurança.

- **AC0.2.1:**
  - Dado um clone limpo do repositório,
  - quando `docker compose up -d --build` é executado pela primeira vez,
  - então as chaves e senhas locais são geradas em `./.local/secrets` (ignorado pelo git), junto com o realm do Keycloak renderizado e o environment do Postman, e todos os containers ficam saudáveis.
- **AC0.2.2:**
  - Dado o repositório,
  - quando a varredura de segredos roda,
  - então nenhum segredo é encontrado, e `.env`, `.local/` e `target/` estão no `.gitignore`. A única exceção são as credenciais fictícias `test`/`test` do LocalStack.
- **AC0.2.3:**
  - Dado um serviço que depende da infraestrutura,
  - quando o ambiente sobe,
  - então ele só inicia depois de a dependência ficar saudável (`service_healthy`).

Prioridade: Must. Tamanho relativo: M. Requisitos: FR11.1, NFR9.

### US0.3: CI como portão de qualidade

**Como** operador de sustentação, **quero** que cada mudança passe por um CI que roda os testes, a cobertura, a análise estática e o smoke test, **para** operar apenas versões que passaram pelos testes críticos e pelo smoke test.

- **AC0.3.1:**
  - Dado um push num branch de trabalho,
  - quando o CI roda,
  - então executa `./mvnw verify` (unitários `*Test`, integração `*IT`, cobertura combinada ≥ 80% de linhas por módulo, `spotless:check`, SpotBugs com FindSecBugs e o teste ArchUnit) e, em job separado, o smoke test contra o Compose.
- **AC0.3.2:**
  - Dado um teste falhando ou a cobertura abaixo do piso,
  - quando o CI roda,
  - então o resultado é vermelho e não há retentativa automática do teste.
- **AC0.3.3:**
  - Dado o CI,
  - quando o Trivy encontra um segredo, ou uma vulnerabilidade CRITICAL com correção disponível sem exceção válida registrada,
  - então o resultado é vermelho.

O `verify` e o job de smoke test nascem com o esqueleto. O Trivy e o Dependabot podem entrar logo depois, sem bloquear o checkpoint do esqueleto (`team.md`).

Prioridade: Must. Tamanho relativo: G. Requisitos: NFR12, FR11.1.

---

## Grupo 1. Catálogo

### US1.1: Criar produto

**Como** analista de catálogo, **quero** criar um produto com nome, descrição e BIN, **para** disponibilizá-lo para emissão.

- **AC1.1.1:**
  - Dado um BIN com 8 dígitos numéricos ainda não usado,
  - quando o produto é criado,
  - então a resposta é 201 com `Location` e o produto nasce `ACTIVE` (BR1.1, BR1.2).
- **AC1.1.2:**
  - Dado um BIN com formato inválido (7 ou 9 dígitos, ou com letras),
  - quando o produto é criado,
  - então a resposta é 400, com o campo indicado.
- **AC1.1.3:**
  - Dado um BIN já cadastrado, inclusive em duas criações concorrentes,
  - quando o produto é criado,
  - então exatamente um é criado e o outro recebe 409, com a unicidade garantida pelo banco (BR1.1).

Prioridade: Must. Tamanho relativo: P. Requisitos: FR1.1.

### US1.2: Consultar e listar produtos

**Como** analista de catálogo, **quero** consultar um produto e listar o catálogo com paginação, **para** conferir o que está disponível.

- **AC1.2.1:**
  - Dado um produto existente,
  - quando consultado por ID,
  - então os seus dados e o status são retornados.
- **AC1.2.2:**
  - Dado um ID inexistente,
  - quando consultado,
  - então a resposta é 404.
- **AC1.2.3:**
  - Dados 25 produtos,
  - quando a listagem é feita sem parâmetros,
  - então retorna exatamente 20.
- **AC1.2.4:**
  - Dado `size=100`,
  - quando a listagem é feita,
  - então é aceita; com `size=101`, a resposta é 400.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR1.2, FR1.3.

### US1.3: Atualizar dados descritivos

**Como** analista de catálogo, **quero** corrigir o nome e a descrição de um produto ativo, **para** manter o catálogo correto sem afetar o BIN.

- **AC1.3.1:**
  - Dado um produto `ACTIVE`,
  - quando o nome e a descrição são atualizados,
  - então a mudança é salva e o BIN permanece igual (BR1.1).
- **AC1.3.2:**
  - Dado um corpo de atualização contendo o campo `bin`, com qualquer valor,
  - quando enviado,
  - então a resposta é 400.
- **AC1.3.3:**
  - Dado um produto `CANCELED`,
  - quando é atualizado,
  - então a resposta é 409 e nada muda.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR1.4.

### US1.4: Cancelar produto

**Como** analista de catálogo, **quero** cancelar um produto, **para** impedir novas emissões sem afetar os cartões já emitidos.

- **AC1.4.1:**
  - Dado um produto `ACTIVE`,
  - quando é cancelado,
  - então passa a `CANCELED` e a transição é registrada no histórico com ator e instante (BR1.2).
- **AC1.4.2:**
  - Dado um produto `CANCELED`,
  - quando o cancelamento é pedido de novo,
  - então a resposta é idempotente: 200 com o estado atual e nenhuma nova entrada no histórico (Q12).
- **AC1.4.3:**
  - Dado um cartão emitido de um produto depois cancelado,
  - quando o cartão é consultado ou tem o status alterado no card-service,
  - então as operações funcionam normalmente (BR1.3).
- **AC1.4.4:**
  - Dado o recurso de produto,
  - quando se envia `DELETE`,
  - então a resposta é 405 e o OpenAPI não declara `DELETE`.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR1.5, FR1.6.

---

## Grupo 2. Cadastro

### US2.1: Cadastrar portador com o produto desejado

**Como** gateway de onboarding, **quero** cadastrar um portador informando o produto desejado e receber imediatamente um recibo de aceite, **para** não deixar o consumidor esperando pela emissão.

- **AC2.1.1:**
  - Dados válidos (CPF válido e inédito, maior de 18 anos, nome com nome e sobrenome, produto `ACTIVE`),
  - quando o cadastro é enviado com `Idempotency-Key`,
  - então a resposta é 202 com `cardholderId`, `issuanceRequestId` e `Location`; o portador nasce `ACTIVE` e a solicitação `PENDING` (BR2.5, BR2.6, BR3.5).
  - O 202 significa "aceito e rastreável", não "cartão emitido". O desfecho aparece depois na consulta consolidada (US4.2).
- **AC2.1.2:**
  - Dado um CPF com dígitos inválidos ou sequência repetida,
  - quando enviado,
  - então a resposta é 422, com o campo indicado (BR2.1).
- **AC2.1.3:**
  - Dada uma pessoa que completa 18 anos hoje, ou com exatamente 120 anos,
  - quando o cadastro é enviado,
  - então é aceito.
  - Um dia a menos que 18 anos, uma data no futuro ou mais de 120 anos geram 422 (BR2.3).
- **AC2.1.4:**
  - Dado um nome com 3 ou 120 caracteres, com nome e sobrenome,
  - quando o cadastro é enviado,
  - então é aceito.
  - Um nome com 2 ou 121 caracteres, ou com uma única palavra, gera 422 (BR2.4).
- **AC2.1.5:**
  - Dado um CPF já cadastrado, inclusive de portador cancelado,
  - quando o cadastro é enviado,
  - então a resposta é 409, com o `type` de "CPF já cadastrado", garantida por constraint (BR2.2).
- **AC2.1.6:**
  - Dado o catálogo respondendo que o produto não existe ou está `CANCELED`,
  - quando o cadastro é enviado,
  - então a resposta é 422 e nada é criado (BR3.3).
- **AC2.1.7:**
  - Dado um CPF enviado com pontuação válida,
  - quando o cadastro é aceito,
  - então o CPF é armazenado só com dígitos, e o mesmo CPF com e sem pontuação gera 409 (BR2.1, BR2.2).
- **AC2.1.8:**
  - Dados dois cadastros simultâneos com o mesmo CPF e chaves diferentes,
  - quando processados,
  - então exatamente um recebe 202 e o outro 409 (BR2.2).
- **AC2.1.9:**
  - Dado `productId` ausente ou malformado,
  - quando o cadastro é enviado,
  - então a resposta é 422, com o campo indicado, e o catálogo não é consultado (BR2.6).
- **AC2.1.10:**
  - Dada uma falha ao gravar qualquer um dos quatro registros (chave, portador, solicitação, outbox),
  - quando o cadastro é processado,
  - então nenhum deles fica persistido, a resposta não é 202, e uma nova tentativa com a mesma chave é processada normalmente (BR3.5).
- **AC2.1.11:**
  - Dada uma requisição com várias regras violadas,
  - quando enviada,
  - então todas as violações voltam juntas na lista de campos.

Prioridade: Must. Tamanho relativo: M. Requisitos: FR2.2, FR2.3, FR2.4, FR2.5.

### US2.2: Cadastro aceito com dependências fora do ar

**Como** gateway de onboarding, **quero** que o cadastro seja aceito mesmo quando o catálogo ou a mensageria estão indisponíveis, **para** não perder adesões em picos ou incidentes.

- **AC2.2.1:**
  - Dado o catálogo respondendo com timeout, 5xx ou conexão recusada,
  - quando o cadastro é enviado,
  - então a resposta é 202 e a validação fica para a emissão (BR3.4).
  - O desfecho, inclusive um `FAILED` com `PRODUCT_NOT_FOUND` ou `PRODUCT_CANCELED`, aparece depois na consulta consolidada.
- **AC2.2.2 (TC1):**
  - Dada a SQS indisponível,
  - quando o cadastro é enviado,
  - então a resposta é 202; portador, solicitação `PENDING` e evento de outbox ficam persistidos, com o evento não marcado como enviado.
  - Com a SQS de volta, sem ação manual, sai exatamente uma mensagem com o `issuanceRequestId` em `card-issuance-requested` e o evento é marcado como enviado (BR3.5).
- **AC2.2.3:**
  - Dado o catálogo respondendo 401, 403 ou com contrato inválido,
  - quando o cadastro é enviado,
  - então a resposta é 202 (Q6), é gerado um alerta de configuração, a validação fica para a emissão e a falha nunca é tratada como produto inexistente.
- **AC2.2.4:**
  - Dado o catálogo lento além do orçamento,
  - quando o cadastro é enviado,
  - então a resposta 202 sai dentro do orçamento e o catálogo recebe exatamente uma chamada, sem retentativa.
- **AC2.2.5:**
  - Dado um lote de 10 eventos no `SendMessageBatch` com 1 entrada recusada,
  - quando o worker do outbox roda,
  - então só as 9 aceitas são marcadas como enviadas e a recusada é reenviada no ciclo seguinte.
- **AC2.2.6:**
  - Dados dois workers sobre o mesmo outbox,
  - quando rodam ao mesmo tempo,
  - então nenhum evento é enviado pelos dois (`SKIP LOCKED`).

Prioridade: Must. Tamanho relativo: M. Requisitos: FR2.4, FR3.1, NFR6.

### US2.3: Repetir o cadastro com segurança

**Como** gateway de onboarding, **quero** repetir um cadastro com a mesma `Idempotency-Key` e receber o mesmo recibo, **para** recuperar respostas perdidas sem criar portadores duplicados.

- **AC2.3.1:**
  - Dado um cadastro aceito,
  - quando o mesmo `client_id` repete a mesma chave com o mesmo payload em até 24 h,
  - então a resposta é 202 com o mesmo corpo e `Idempotent-Replayed: true` (BR3.1).
  - O replay é devolvido mesmo que o produto tenha sido cancelado depois do aceite.
- **AC2.3.2:**
  - Dada a mesma chave com payload diferente, depois do aceite da original,
  - quando o cadastro é enviado,
  - então a resposta é 422, sem revelar o payload original (BR3.2).
- **AC2.3.3a (TC-IDEM):**
  - Dadas duas requisições simultâneas com a mesma chave e o mesmo payload,
  - quando processadas,
  - então existem exatamente 1 portador, 1 solicitação e 1 evento de outbox; uma resposta é o aceite e a outra é o replay ou 409.
- **AC2.3.3b (TC-IDEM):**
  - Dada a transação original mantida aberta além do `lock_timeout`,
  - quando chega a repetição com a mesma chave,
  - então a resposta é 409, com o `type` de "requisição em andamento", distinto do 409 de CPF duplicado.
- **AC2.3.3c (TC-IDEM):**
  - Dada a original em andamento,
  - quando chega a mesma chave com payload diferente,
  - então a resposta é 409 (Q8).
- **AC2.3.4:**
  - Dado o header ausente ou fora do formato UUID (com ou sem aspas),
  - quando o cadastro é enviado,
  - então a resposta é 400.
- **AC2.3.5:**
  - Dada uma chave aceita há mais de 24 h,
  - quando o mesmo cliente a reenvia, antes ou depois da limpeza,
  - então a requisição é tratada como nova; com o mesmo CPF, recebe 409 de CPF duplicado, não o recibo.
- **AC2.3.6:**
  - Dada a mesma chave usada por dois `client_id` diferentes,
  - quando enviadas,
  - então as requisições são tratadas de forma independente (BR3.1).
- **AC2.3.7:**
  - Dado um cadastro recusado (400, 409 ou 422),
  - quando o cliente reenvia a mesma chave com o payload corrigido,
  - então a requisição é processada como nova: só um cadastro aceito consome a chave (Q7).

Prioridade: Must. Tamanho relativo: G. Requisitos: FR2.1, FR2.6, FR2.7.

### US2.4: Consultar portador

**Como** gateway de onboarding, **quero** consultar um portador, **para** exibir os dados ao parceiro sem expor dados pessoais além do necessário.

- **AC2.4.1:**
  - Dado um portador existente,
  - quando consultado,
  - então o CPF vem mascarado no formato `***.456.789-**`.
- **AC2.4.2:**
  - Dado um ID inexistente,
  - quando consultado,
  - então a resposta é 404.
- **AC2.4.3:**
  - Dada a consulta de portador,
  - quando retornada,
  - então a data de nascimento segue a política definida no Desenho de Contratos (omitida ou reduzida) e nunca aparece completa.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR2.8.

---

## Grupo 3. Emissão

### US3.1: Emitir o cartão a partir da solicitação

**Como** gateway de onboarding, **quero** que a solicitação aceita vire um cartão válido sem nenhuma ação adicional, **para** que o consumidor receba o cartão do produto escolhido.

- **AC3.1.1:**
  - Dada uma solicitação sem resultado em `issuance_processing` e um produto `ACTIVE` observado há no máximo 5 minutos,
  - quando processada,
  - então é gerado um cartão `ACTIVE` com:
    - PAN de 16 dígitos: BIN do produto, 7 dígitos aleatórios e dígito de Luhn;
    - validade de 5 anos, calculada pelo `Clock` injetado (mês e ano);
    - nenhum CVV.
  - O resultado `ISSUED` e o outbox são gravados na mesma transação, e a consulta consolidada mostra `ISSUED` (BR4.1, BR5.1 a BR5.4).
- **AC3.1.2 (TC-PAN):**
  - Dado o gerador forçado a repetir um PAN existente,
  - quando o cartão é gerado,
  - então existem exatamente 1 cartão novo com PAN diferente do colidido, 1 resultado `ISSUED` e 1 evento de outbox, sem linha parcial da tentativa que colidiu.
- **AC3.1.3:**
  - Dadas 20 colisões seguidas,
  - quando o cartão é gerado,
  - então a emissão é tratada como falha técnica (classe transitória), gera alerta e nenhum resultado é gravado.

Prioridade: Must. Tamanho relativo: M. Requisitos: FR4.2, FR4.5, FR4.6.

### US3.2: Nunca emitir para produto inexistente ou cancelado

**Como** analista de catálogo, **quero** que nenhum cartão novo seja emitido para produto inexistente ou cancelado além da janela de 5 minutos, **para** que o cancelamento de um produto tenha efeito garantido.

- **AC3.2.1:**
  - Dado o catálogo respondendo 404 ou `CANCELED`,
  - quando a solicitação é processada,
  - então o desfecho é `FAILED`, com `PRODUCT_NOT_FOUND` ou `PRODUCT_CANCELED`, sem retentativa, e aparece na consulta consolidada (BR4.4, BR1.3).
- **AC3.2.2 (TC7):**
  - Dado que o catálogo respondeu `CANCELED` em T1, e que uma resposta `ACTIVE` observada em T0 < T1 chega depois,
  - quando essa resposta tenta gravar o cache,
  - então o registro `ACTIVE` não é restaurado, e uma solicitação processada em seguida termina `FAILED` com `PRODUCT_CANCELED`, sem cartão (BR1.2, BR1.3).
- **AC3.2.3 (TC6):**
  - Dado um registro com `validatedAt` de mais de 5 min e o catálogo indisponível,
  - quando a solicitação é processada,
  - então não existe cartão, nem linha em `issuance_processing`, nem evento de outbox; a mensagem não é confirmada e a visibilidade é ajustada pelo backoff (BR4.1).
- **AC3.2.4:**
  - Dado o Redis indisponível e o catálogo saudável,
  - quando a solicitação é processada,
  - então o catálogo é consultado com timeout, a degradação gera alerta e a emissão segue.
  - Uma falha ao gravar no Redis não falha a emissão.
- **AC3.2.5 (NFR7):**
  - Dada uma observação `ACTIVE` gravada em T e o produto cancelado depois de T,
  - quando uma solicitação é processada em T + 5 min + 1 s,
  - então o catálogo é consultado e o desfecho é `FAILED` com `PRODUCT_CANCELED`.
  - Processada em T + 5 min, a observação ainda é elegível: é a janela aceita por BR4.1.
- **AC3.2.6:**
  - Dado um registro `ACTIVE` com `validatedAt` = T, lido várias vezes entre T e T + 5 min,
  - quando uma solicitação é processada em T + 5 min + 1 s,
  - então o catálogo é consultado, porque a leitura nunca renova `validatedAt` (BR4.1).
- **AC3.2.7:**
  - Dados o Redis e o catálogo indisponíveis, sem registro elegível,
  - quando a solicitação é processada,
  - então nenhum cartão é emitido e a mensagem volta para retentativa.
- **AC3.2.8:**
  - Dado o catálogo respondendo `ACTIVE`,
  - quando consultado,
  - então o cache passa a ter um registro com `validatedAt` igual ao instante da observação.

Prioridade: Must. Tamanho relativo: G. Requisitos: FR4.2, FR4.3, NFR7.

### US3.3: Um cartão não cancelado por portador e produto

**Como** gateway de onboarding, **quero** que um portador nunca tenha dois cartões ativos ou bloqueados do mesmo produto, **para** evitar duplicidade de instrumentos de pagamento.

- **AC3.3.1:**
  - Dado um portador que já tem cartão não cancelado do produto,
  - quando uma nova solicitação é processada,
  - então o desfecho é `FAILED` com `NON_CANCELED_CARD_ALREADY_EXISTS`, visível na consulta consolidada (BR4.3).
- **AC3.3.2 (TC5):**
  - Dadas duas solicitações distintas para o mesmo portador e produto, processadas em paralelo,
  - quando concluídas,
  - então existe exatamente 1 cartão não cancelado, e a outra solicitação termina `FAILED` com `NON_CANCELED_CARD_ALREADY_EXISTS` (falha de negócio, sem retentativa).
- **AC3.3.3:**
  - Dado um cartão `CANCELED` do portador e produto,
  - quando outro cartão do mesmo par é gravado,
  - então o índice parcial permite (BR4.3).
  - Na R1 não existe fluxo de API para pedir um segundo cartão ao mesmo portador; o critério é verificado no nível do repositório.

Prioridade: Must. Tamanho relativo: M. Requisitos: FR4.3, FR4.4.

### US3.4: Reentrega sem efeito duplicado

**Como** gateway de onboarding, **quero** que mensagens repetidas da mesma solicitação nunca gerem outro cartão nem mudem um desfecho, **para** informar ao parceiro um desfecho que não muda depois de comunicado.

- **AC3.4.1 (TC2):**
  - Dada uma falha forçada entre o commit e a confirmação da mensagem,
  - quando a mensagem é reentregue,
  - então existem exatamente 1 cartão e 1 registro em `issuance_processing`, o resultado persistido é recolocado no outbox com o mesmo `cardId`, e a mensagem é confirmada.
- **AC3.4.2 (TC3):**
  - Dadas duas cópias da mesma mensagem processadas em paralelo,
  - quando concluídas,
  - então existem exatamente 1 cartão e 1 desfecho terminal, nenhum `cardId` diferente é publicado, e as duas mensagens são confirmadas.
  - A cópia perdedora republica o resultado da vencedora e nunca grava `FAILED`.
- **AC3.4.3 (TC4):**
  - Dada uma solicitação `FAILED` com `PRODUCT_CANCELED`,
  - quando é reentregue depois de o catálogo passar a responder `ACTIVE`,
  - então o desfecho continua `FAILED` com o mesmo motivo, o catálogo não é consultado e nenhum cartão é criado (BR4.2).
- **AC3.4.4:**
  - Dado o banco falhando no commit do resultado,
  - quando o processamento termina,
  - então a mensagem não é confirmada e volta a ficar visível.

Prioridade: Must. Tamanho relativo: G. Requisitos: FR4.1, NFR6.

### US3.5: Retentar falhas técnicas sem mascarar erros

**Como** operador de sustentação, **quero** que falhas técnicas sejam retentadas automaticamente com espaçamento crescente, e que mensagens inválidas e erros de configuração sejam separados delas, **para** que o sistema se recupere sozinho sem esconder problemas.

- **AC3.5.1:**
  - Dada a n-ésima falha transitória de uma mensagem,
  - quando a visibilidade é ajustada,
  - então o valor fica entre 0,8 × 30 × 2^(n−1) s e 1,2 × 30 × 2^(n−1) s, nunca passa de 300 s depois do jitter, e o catálogo recebe exatamente uma chamada por recebimento.
- **AC3.5.2:**
  - Dada uma mensagem inválida ou com versão não suportada,
  - quando recebida,
  - então vai para a DLQ e gera alerta.
- **AC3.5.3:**
  - Dado o catálogo respondendo 401, 403 ou com contrato inválido,
  - quando a solicitação é processada,
  - então gera alerta de configuração, nenhum resultado é gravado, e a falha nunca vira `PRODUCT_NOT_FOUND` nem outra falha de negócio.
  - O que acontece com a mensagem nesse caso fica para o Desenho Funcional.
- **AC3.5.4 (NFR8):**
  - Dada a dependência de volta,
  - quando a mensagem retida fica visível de novo (no máximo 300 s depois da última falha),
  - então ela é processada.
- **AC3.5.5:**
  - Dada uma falha técnica persistente até `maxReceiveCount`,
  - quando a última tentativa falha,
  - então a mensagem vai para a DLQ, a solicitação continua `PENDING` no cadastro (recuperável pela reconciliação) e é gerado um alerta (BR4.4).
- **AC3.5.6:**
  - Dado o catálogo respondendo depois do timeout configurado,
  - quando chamado,
  - então a chamada é abortada no timeout e tratada como falha técnica.
  - Com o circuit breaker aberto, as chamadas seguintes falham imediatamente, também como falha técnica.
  - Durante a chamada, nenhuma transação de escrita está aberta.
- **AC3.5.7:**
  - Dada uma indisponibilidade ao obter o token de client credentials,
  - quando o serviço chama o catálogo,
  - então a falha é transitória; `invalid_client` é falha de configuração.

Prioridade: Must. Tamanho relativo: M. Requisitos: FR4.7, NFR8, NFR10.

### US3.6: Enxergar a ocupação do BIN

**Como** analista de catálogo, **quero** saber quando a faixa de números de um BIN está se esgotando, **para** planejar um novo produto antes de a emissão falhar.

- **AC3.6.1:**
  - Dados os cartões emitidos de um produto,
  - quando a métrica de ocupação é calculada,
  - então ela reflete cartões ÷ 10^7, e gera alerta ao atingir 70%.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR4.9.

---

## Grupo 4. Resultado e consulta

### US4.1: Ver o desfecho da emissão no cadastro

**Como** gateway de onboarding, **quero** que o desfecho da emissão apareça no portador, **para** informar o parceiro.

- **AC4.1.1:**
  - Dada uma solicitação `PENDING`,
  - quando chega o resultado,
  - então a situação passa a `ISSUED` (com o `cardId`) ou `FAILED` (com o motivo) (BR4.5).
- **AC4.1.2:**
  - Dado o mesmo resultado terminal recebido de novo,
  - quando processado,
  - então nenhuma linha nova de histórico é criada, a situação e o `cardId` ficam inalterados e a mensagem é confirmada.
- **AC4.1.3:**
  - Dado um resultado terminal contraditório (ex.: `ISSUED` para uma solicitação já `FAILED`),
  - quando processado,
  - então nada muda, é gerado um alerta, e a mensagem é enviada de forma explícita e imediata para a DLQ.
  - A correção é manual, pelo runbook; o card-service é a fonte da verdade da decisão de emissão (Q10, BR4.2).
- **AC4.1.4:**
  - Dado um resultado para `issuanceRequestId` desconhecido, ou uma mensagem de resultado inválida ou com versão não suportada,
  - quando recebido,
  - então gera alerta e a mensagem original é enviada de forma explícita e imediata para a DLQ, sem esperar o `maxReceiveCount` (Q9).
- **AC4.1.5:**
  - Dada uma solicitação suspensa pela reconciliação,
  - quando chega o seu resultado,
  - então ele é aplicado normalmente e encerra a suspensão.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR5.1.

### US4.2: Consulta consolidada honesta sobre o que sabe

**Como** gateway de onboarding, **quero** uma consulta que traga portador, situação da emissão, cartão e produto, deixando claro o que está completo, indisponível ou desatualizado, **para** não informar algo errado ao parceiro.

- **AC4.2.1:**
  - Dada uma solicitação `PENDING`,
  - quando consultada,
  - então a resposta é completa, sem cartão, e indica ausência esperada; o card-service não é chamado.
- **AC4.2.2:**
  - Dada uma solicitação `FAILED`,
  - quando consultada,
  - então a resposta é completa, com o motivo e sem cartão; o card-service não é chamado.
- **AC4.2.3:**
  - Dada uma solicitação `ISSUED` e o card-service indisponível,
  - quando consultada,
  - então a resposta continua `ISSUED`, com o `cardId`, e só os detalhes do cartão aparecem sinalizados como indisponíveis (BR6.3).
- **AC4.2.4:**
  - Dado um produto vindo de observação antiga,
  - quando consultado,
  - então ele aparece sinalizado como desatualizado, com o instante da observação.
  - A origem do produto e o limiar de "antiga" são definidos no Desenho Funcional.
- **AC4.2.5 (TC8):**
  - Dados os cinco casos (os quatro de BR6.2 e o de AC4.2.6),
  - quando consultados,
  - então as respostas são 200 e distinguíveis por campos estruturados, não pelo status HTTP, pela simples ausência de um campo ou pelo texto de uma mensagem (BR6.1, BR6.2).
- **AC4.2.6:**
  - Dado o catálogo indisponível e nenhuma observação utilizável do produto,
  - quando consultado,
  - então a resposta é 200, com portador, situação e cartão, e a parte do produto sinalizada como indisponível (Q13, BR6.3).
  - Esse quinto caso amplia BR6.2.
- **AC4.2.7:**
  - Dado um portador inexistente,
  - quando consultado,
  - então a resposta é 404.

Prioridade: Must. Tamanho relativo: M/G. Requisitos: FR8.1, FR8.2.

---

## Grupo 5. Status e histórico

### US5.1: Bloquear, desbloquear e cancelar portador

**Como** gateway de onboarding, **quero** mudar o status de um portador, **para** refletir decisões do parceiro.

- **AC5.1.1:**
  - Dadas as transições válidas (`ACTIVE` → `BLOCKED`, `BLOCKED` → `ACTIVE`, `ACTIVE` → `CANCELED`, `BLOCKED` → `CANCELED`),
  - quando cada uma é pedida,
  - então o novo status é persistido e a linha correspondente entra no histórico (BR2.5).
- **AC5.1.2:**
  - Dada uma transição a partir de `CANCELED`,
  - quando pedida,
  - então a resposta é 409, com o `type` de transição inválida.
- **AC5.1.3:**
  - Dado um pedido para o status atual,
  - quando enviado,
  - então a resposta é idempotente: 200 com o estado atual e nenhuma entrada nova no histórico (Q12).
- **AC5.1.4:**
  - Dado um portador bloqueado ou cancelado com emissão pendente,
  - quando a emissão é processada,
  - então ela segue normalmente.
  - É uma limitação conhecida da R1, documentada no README e na descrição do endpoint no OpenAPI (FR4.8).

Prioridade: Must. Tamanho relativo: P. Requisitos: FR7.1, FR4.8.

### US5.2: Consultar e mudar status de cartões

**Como** gateway de onboarding, **quero** consultar cartões e mudar o seu status, **para** atender bloqueios e cancelamentos pedidos pelo parceiro.

- **AC5.2.1:**
  - Dado um cartão,
  - quando consultado por ID ou listado por portador (paginação: padrão 20, máximo 100),
  - então só `panLastFour` é exibido, nunca o PAN (BR5.5).
- **AC5.2.2:**
  - Dadas as quatro transições válidas de BR5.3,
  - quando pedidas,
  - então são aplicadas com linha no histórico.
  - A partir de `CANCELED`, a resposta é 409; para o status atual, é idempotente (Q12).

Prioridade: Must. Tamanho relativo: P. Requisitos: FR7.2, FR7.4.

### US5.3: Histórico auditável de transições

**Como** Segurança/Compliance, **quero** que toda transição de status de produto, portador e cartão tenha ator e instante registrados, sem dados pessoais, **para** auditar quem mudou o quê.

- **AC5.3.1:**
  - Dada qualquer transição,
  - quando aplicada,
  - então o histórico registra entidade, transição, instante e ator, na mesma transação da mudança.
- **AC5.3.2:**
  - Dado o header `X-Actor-Id` com até 100 caracteres,
  - quando a transição é aplicada,
  - então ele é registrado junto com o `client_id`; se o header estiver ausente, fica só o `client_id`.
- **AC5.3.3:**
  - Dado um `X-Actor-Id` com mais de 100 caracteres,
  - quando enviado,
  - então a resposta é 400, com o campo indicado (Q11).
- **AC5.3.4:**
  - Dado o histórico,
  - quando inspecionado,
  - então não contém CPF, data de nascimento nem PAN.
- **AC5.3.5:**
  - Dados os recursos de portador e cartão,
  - quando se envia `DELETE`,
  - então a resposta é 405 e o OpenAPI não declara `DELETE`.

Prioridade: Must. Tamanho relativo: P. Requisitos: FR7.3, FR7.5, NFR9.

---

## Grupo 6. Operação e recuperação

### US6.1: Recuperar solicitações sem desfecho

**Como** operador de sustentação, **quero** que solicitações sem desfecho há muito tempo sejam republicadas automaticamente, sem gerar outro cartão, **para** que nada aceito fique esquecido.

- **AC6.1.1 (TC9, solicitação nunca entregue):**
  - Dada uma solicitação `PENDING` com mais de 3 h que nunca chegou ao card-service,
  - quando a reconciliação roda,
  - então ela é republicada pelo outbox e emitida uma única vez.
- **AC6.1.2 (TC9, solicitação já decidida):**
  - Dada uma solicitação `PENDING` no cadastro, mas já decidida no card-service,
  - quando republicada,
  - então nenhum cartão novo é criado e o total de cartões fica inalterado.
- **AC6.1.3 (TC10):**
  - Dado o card-service com `ISSUED` persistido e o cadastro ainda `PENDING`, porque o resultado foi descartado,
  - quando a reconciliação republica,
  - então o cadastro passa a mostrar `ISSUED`, com o mesmo `cardId`, e o total de cartões continua 1.
- **AC6.1.4:**
  - Dado o `Clock` controlado,
  - quando a reconciliação roda,
  - então:
    - uma solicitação com 2 h 59 min não é republicada, e uma com 3 h 1 min é;
    - uma solicitação republicada há 29 min não é republicada de novo;
    - com 60 elegíveis, uma execução republica no máximo 50.
- **AC6.1.5:**
  - Dadas 3 reconciliações sem desfecho,
  - quando a próxima execução roda,
  - então a solicitação é suspensa da republicação automática, marcada para intervenção, e é gerado um alerta.
- **AC6.1.6:**
  - Dada uma solicitação `ISSUED` cujo cartão já foi cancelado,
  - quando é republicada (por reconciliação ou reentrega),
  - então nenhum cartão novo é emitido e o resultado original é republicado (BR4.2 prevalece sobre o índice parcial de BR4.3).
- **AC6.1.7:**
  - Dadas duas instâncias do cadastro,
  - quando a reconciliação roda nas duas,
  - então nenhuma solicitação é republicada duas vezes no mesmo ciclo (`SKIP LOCKED`).

Prioridade: Must. Tamanho relativo: M. Requisitos: FR6.1, FR6.2, FR6.3, NFR6.

### US6.2: Enxergar a saúde do fluxo

**Como** operador de sustentação, **quero** métricas e sinais de saúde do fluxo de emissão, **para** agir antes de o parceiro perceber.

- **AC6.2.1:**
  - Dados os serviços,
  - quando consultado `/actuator/prometheus`,
  - então existem ao menos:
    - emissões por status e motivo;
    - pendências e idade do outbox;
    - profundidade de filas e DLQs;
    - hit/miss do cache;
    - ocupação do BIN.
- **AC6.2.2:**
  - Dado um serviço,
  - quando consultado,
  - então a liveness não depende de dependências externas e a readiness depende apenas do banco.
- **AC6.2.3:**
  - Dada uma requisição HTTP de cadastro,
  - quando o fluxo termina,
  - então o mesmo `correlationId` e o mesmo trace aparecem:
    - no log do cadastro;
    - nos atributos da mensagem em `card-issuance-requested`;
    - no log do card-service;
    - nos atributos da mensagem em `card-issuance-completed`;
    - no log do consumidor de resultados.

Prioridade: Must (mínimos). Métricas além do mínimo são Could (corte 1). Tamanho relativo: M. Requisitos: FR3.3, FR4.9, NFR11.

### US6.3: Procedimentos de DLQ e reconciliação

**Como** operador de sustentação, **quero** um procedimento documentado para mensagens na DLQ e solicitações suspensas, **para** recuperar incidentes sem improviso.

- **AC6.3.1:**
  - Dado o README,
  - quando lido,
  - então descreve como inspecionar e reprocessar cada DLQ, como corrigir um resultado contraditório e como retomar uma solicitação suspensa (por comando ou SQL documentado, sem interface administrativa), sem risco de cartão duplicado.
  - Verificado por lista de conferência na revisão.

Prioridade: Must (procedimento enxuto no README). Documentos operacionais separados são Could (corte 2). Tamanho relativo: P. Requisitos: FR11.4.

---

## Grupo 7. Proteção de dados e acesso

### US7.1: Acesso só com token válido e escopo correto

**Como** gateway de onboarding, **quero** que toda API exija token OAuth2 com emissor, audiência e escopo corretos, **para** saber, pela resposta, se devo renovar o token (401) ou corrigir a configuração de escopos (403).

- **AC7.1.1:**
  - Dada uma requisição sem token, com token expirado, ou com emissor ou audiência errados,
  - quando enviada,
  - então a resposta é 401, em `ProblemDetail`, com o header `WWW-Authenticate`.
- **AC7.1.2:**
  - Dado um token sem o escopo exigido,
  - quando a requisição é enviada,
  - então a resposta é 403, em `ProblemDetail`.
  - Isso é verificado por um teste parametrizado sobre todos os endpoints de leitura e escrita.
- **AC7.1.3:**
  - Dada uma chamada entre serviços,
  - quando feita,
  - então usa client credentials.

Prioridade: Must. Tamanho relativo: M. Requisitos: FR9.1, FR9.2.

### US7.2: Dados de pagamento e pessoais protegidos

**Como** Segurança/Compliance, **quero** evidência de que o PAN é protegido e de que dados pessoais não vazam, **para** confiar nos controles de aplicação adotados, sem que a release afirme conformidade integral.

- **AC7.2.1:**
  - Dado um cartão emitido,
  - quando o banco é inspecionado,
  - então o PAN está cifrado (AES-256-GCM, com versão da chave) e a unicidade é feita por HMAC com chave separada.
- **AC7.2.2:**
  - Dados os fluxos de cadastro, emissão e consulta, inclusive nos caminhos de erro (422 de validação, 409 de CPF duplicado, falhas de dependência),
  - quando os logs e as mensagens SQS são capturados,
  - então não contêm o CPF, a data de nascimento nem o PAN usados.
  - Isso inclui a violação de unicidade do PostgreSQL, que traz o valor na mensagem, e o `toString()` de tipos com dados pessoais.
- **AC7.2.3:**
  - Dada uma chave ausente, malformada ou igual a outra das três,
  - quando o serviço inicia,
  - então a inicialização é impedida.
- **AC7.2.4:**
  - Dado o Actuator,
  - quando consultado,
  - então só `health`, `info` e `prometheus` estão expostos.
- **AC7.2.5:**
  - Dado o README,
  - quando lido,
  - então há uma seção de controles de segurança e privacidade adotados, com cada controle e o teste que o comprova, declarando que a release não afirma conformidade integral com PCI-DSS ou LGPD.
  - Verificado por lista de conferência na revisão.

Prioridade: Must. Tamanho relativo: M. Requisitos: NFR9, FR3.2.

---

## Grupo 8. Entrega e documentação

### US8.1: Contratos para o time do gateway

**Como** gateway de onboarding, **quero** um contrato OpenAPI por serviço e erros padronizados em `ProblemDetail`, **para** integrar sem depender de conversa.

- **AC8.1.1:**
  - Dado cada serviço,
  - quando o contrato é consultado,
  - então o OpenAPI descreve:
    - todos os endpoints;
    - os headers `Idempotency-Key` e `X-Actor-Id`;
    - os códigos de erro;
    - que o desfecho do cadastro só é conhecido pela consulta consolidada, sem notificação ativa na R1;
    - as limitações conhecidas (FR4.8 e a perda de recibo depois de 24 h).
- **AC8.1.2:**
  - Dado qualquer erro,
  - quando retornado,
  - então o corpo é `application/problem+json`, com a lista de campos em erros de validação.
- **AC8.1.3:**
  - Dadas duas causas de erro que usam o mesmo status HTTP,
  - quando retornadas,
  - então cada uma tem um `type` estável e distinto, documentado no OpenAPI com a ação esperada do cliente. No mínimo:
    - 409: CPF duplicado, requisição em andamento, BIN duplicado, transição inválida;
    - 422: regra de campo, produto inexistente ou cancelado, chave reutilizada.
- **AC8.1.4:**
  - Dado qualquer erro no cadastro ou na consulta de portador,
  - quando retornado,
  - então identifica o campo e a regra violada, mas nunca contém CPF, data de nascimento ou PAN.

Prioridade: Must. Tamanho relativo: P a M. Requisitos: FR10.1, FR10.2.

### US8.2: README que explica o sistema

**Como** operador de sustentação, **quero** um README com setup, diagrama, decisões, comportamento sob falha de cada dependência, estratégia de cache, a explicação de como o sistema impede cartão para produto inexistente ou cancelado, as limitações conhecidas e os débitos, **para** operar e evoluir o sistema.

- **AC8.2.1:**
  - Dado o README,
  - quando lido,
  - então contém cada um desses itens, e o diagrama Mermaid tem uma alternativa em texto.
  - Verificado por lista de conferência na revisão.

Prioridade: Must. Tamanho relativo: P a M. Requisitos: FR11.4.

### US8.3: Collection do Postman

**Como** gateway de onboarding, **quero** uma collection do Postman sincronizada com os endpoints, com o token e a `Idempotency-Key` já configurados, **para** testar a integração rapidamente.

- **AC8.3.1:**
  - Dado o environment gerado localmente,
  - quando a collection é importada,
  - então o fluxo feliz, o token, a `Idempotency-Key` e os erros das regras críticas funcionam sem ajuste manual.
  - Há uma requisição que reenvia a mesma chave e mostra o 202 com `Idempotent-Replayed: true`.

Prioridade: Must (essencial). A cobertura de todos os códigos de erro é Could (corte 3). Tamanho relativo: P. Requisitos: FR11.3.

### US8.4: Decisões registradas

**Como** Segurança/Compliance, **quero** as decisões relevantes registradas em ADRs, **para** entender as escolhas e os riscos aceitos.

- **AC8.4.1:**
  - Dadas as decisões relevantes da seção Decided de `project.md`,
  - quando os ADRs são consultados,
  - então:
    - cada uma tem um ADR curto (Contexto, Decisão, Consequências, Alternativas Rejeitadas);
    - os de cache, retry/DLQ, outbox e idempotência estão completos;
    - o do Spring Boot 3.5 traz o plano de migração.
  - Verificado por lista de conferência na revisão.

Prioridade: Must. Tamanho relativo: P a M. Requisitos: FR11.5.

---

## Dependências entre histórias

- **US0.1:** pré-requisito de todas as demais. Contém versões finas de US1.1, US1.2, US2.1, US3.1, US4.1, US4.2, US5.2 e US7.1. US0.2 e US0.3 nascem junto com ela.
- **US3.1 a US3.6:** dependem de US1.1 (produto e BIN). São testáveis sem US2.1, por injeção na fila.
- **US4.1:** depende de US3.1.
- **US4.2:** depende de US4.1, de US5.2 e da decisão sobre a origem do produto (Desenho Funcional).
- **US6.1:** depende de US2.1, US3.4 e US4.1 (o TC10 exige o resultado aplicado no cadastro).
- **US7.1 e US7.2:** atravessam todas as histórias.

## Notas de INVEST

- **Independent:** o fluxo é encadeado (cadastro → emissão → resultado). Cada história é testável isoladamente por injeção de mensagens nas filas e com WireMock (ver Convenções).
- **Small:** nem todas as histórias são pequenas.
  - US0.1 é GG: não pode ser menor sem violar regras NEVER.
  - US0.3, US2.3, US3.2 e US3.4 são G.
  - Os tamanhos relativos estão em cada história. É estimativa, não medição.

  Os cortes pré-aprovados só aliviam histórias pequenas (US6.2, US6.3, US8.3), então a Delivery Planning precisa distribuir esse esforço explicitamente.
- **Testable:**
  - todo critério tem condição e resultado observáveis;
  - os critérios `TC*` coincidem com os testes críticos obrigatórios;
  - os critérios de documentação (AC6.3.1, AC7.2.5, AC8.2.1, AC8.4.1) são verificados por lista de conferência e não contam como teste de regra BR.

## Questões encaminhadas para as próximas etapas

| Questão | Etapa | Origem |
|---|---|---|
| Origem do produto na consulta consolidada e limiar de "observação antiga" | Desenho Funcional (antes de Units Generation) | Desenvolvedor, qualidade |
| O que acontece com a mensagem na emissão em caso de falha de configuração (backoff ou DLQ) | Desenho Funcional | Desenvolvedor |
| Mecanismo de proteção contra resposta antiga no cache (lápide, e `validatedAt` gravado no início da consulta); se a lápide pode encerrar a emissão sem consultar o catálogo | Desenho Funcional | Desenvolvedor |
| Valor do `lock_timeout` da chave de idempotência | Desenho Funcional | Desenvolvedor |
| Se a transição de `IssuanceStatus` entra no histórico de transições | Desenho Funcional | Desenvolvedor |
| Coerência entre a suspensão da reconciliação e o orçamento de retry | Desenho Funcional | Desenvolvedor |
| Leitura das três chaves por cada serviço para validar "chave repetida" | Desenho Funcional | Qualidade |
| Política da data de nascimento na consulta de portador; formato de entrada do CPF | Desenho de Contratos | Designer, desenvolvedor |
| Destino do `Location` do cadastro; `Retry-After` no 409 em andamento; `requestedAt` na consulta consolidada; precedência 400 × 422 | Desenho de Contratos (opções em avaliação) | Designer |
| Ampliação de BR6.2 para cinco casos (Q13) | Registrar no README e no contrato | Q13 |
