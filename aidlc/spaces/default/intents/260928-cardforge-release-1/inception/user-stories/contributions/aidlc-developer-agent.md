**Collaborator:** aidlc-developer-agent

## Contribution

Revisão de implementabilidade e dimensionamento do rascunho (`personas.md`, `stories.md`) contra `requirements.md`, `engineering-standards.md`, `project.md` (Decided, Forbidden, Mandated) e `team.md`. Premissa de capacidade: uma pessoa em dedicação parcial, fora do horário de trabalho, até 05/10/2026 (`constraint-register.md` C-O1, C-O2). Tudo o que proponho abaixo tem origem indicada; nenhum item é requisito novo sem fonte.

### 1. Leitura geral

- O agrupamento por fluxo, os comportamentos sob falha como critérios e a ligação `TC*` estão corretos e facilitam escrever o teste crítico antes da implementação (`team.md`, Testing Posture).
- A maioria dos critérios é implementável como está. Os problemas estão concentrados em cinco pontos:
  1. critérios sem resultado verificável (AC2.2.3, AC3.2.5, AC3.5.4);
  2. um critério sem caminho na API da R1 (AC5.2.3);
  3. um critério não determinístico (AC2.3.3);
  4. comportamentos que o código precisa ter, mas nenhuma história diz qual é (seção 5);
  5. o tamanho real de US0.1, que não é uma fatia fina (seção 3).

### 2. Dimensionamento relativo

Tamanhos relativos (P, M, G, GG), estimativa minha e não medição. Referência: P cabe numa sessão curta com os testes; GG ocupa várias sessões.

| História | Tamanho | Motivo principal |
|---|---|---|
| US0.1 | GG | Três serviços, Compose, realm do Keycloak, init do LocalStack, outbox nos dois lados, cache, consulta consolidada e smoke test com espera assíncrona |
| US0.2 | M | Container de inicialização que gera segredos e renderiza o realm e o environment do Postman |
| US0.3 | G | Failsafe + JaCoCo com relatório combinado (unitário + integração) por módulo, piso de 80%, SpotBugs/FindSecBugs, ArchUnit, job de smoke test, Trivy |
| US1.1, US1.2 | P | CRUD simples; unicidade de BIN por índice |
| US1.3, US1.4 | P | Transição e histórico |
| US2.1 | M | Validações de CPF, idade e nome, consulta ao catálogo fora da transação, transação com chave + portador + solicitação + outbox |
| US2.2 | M | Classificação das respostas do catálogo; TC1 exige parar e retomar o LocalStack no teste |
| US2.3 | G | Aquisição da chave com `ON CONFLICT` e `lock_timeout`, fingerprint HMAC, replay, 409, 422, expiração e limpeza; TC-IDEM exige controlar a concorrência no teste |
| US2.4 | P | Máscara de CPF |
| US3.1 | M | PAN (BIN + conta + Luhn), AES-GCM, HMAC, `ON CONFLICT (pan_hmac)`, 20 tentativas, TC-PAN |
| US3.2 | G | Cache com `validatedAt`, lápide para `CANCELED`/404, proteção contra resposta antiga (TC7), TC6 |
| US3.3 | M | Índice parcial e desempate da violação de unicidade (TC5) |
| US3.4 | G | `issuance_processing`, republicação do resultado, TC2, TC3, TC4 |
| US3.5 | M | Backoff por `ChangeMessageVisibility`, quatro classes de falha, DLQ |
| US4.1 | P | Consumidor idempotente por estado |
| US4.2 | M/G | Composição com card-service e produto, quatro (cinco, ver 5.9) estados distinguíveis; a origem do produto está indefinida (seção 6) |
| US5.1, US5.2 | P cada | Transições no agregado e histórico |
| US5.3 | P | Tabela de histórico e `X-Actor-Id` |
| US6.1 | M | Job com `SKIP LOCKED`, contador, suspensão; TC9 e TC10 |
| US6.2 | M | Métricas mínimas, sendo a profundidade das filas um componente a mais (seção 6) |
| US6.3, US8.1 a US8.4 | P a M | Documentação e contrato; baratos se feitos junto de cada incremento |
| US7.1 | M | Resource Server nos três serviços e client credentials em dois |
| US7.2 | M | Validação das três chaves na inicialização; captura de logs e mensagens nos testes críticos |

Leitura: a soma é incompatível com folga no prazo; o núcleo inegociável (C-O4) concentra os tamanhos G. A política de cortes só alivia US6.2, US6.3 e US8.3, que são pequenas. O risco real de prazo está em US0.1, US0.3, US2.3, US3.2 e US3.4. Recomendo que a Delivery Planning trate isso explicitamente, em vez de a nota INVEST afirmar que as histórias são pequenas.

### 3. Composição do esqueleto (US0.1)

O esqueleto não pode usar atalhos, porque as regras NEVER valem desde o primeiro commit: publicar direto no SQS é proibido (outbox obrigatório), ACK antes do resultado persistido é proibido e emitir sem observação `ACTIVE` de até 5 minutos é proibido. Portanto US0.1 já implementa, de forma fina, partes reais de várias histórias. Proponho explicitar isso em US0.1 para evitar retrabalho e para que o tamanho fique visível:

| Entra no esqueleto (versão fina, já correta) | Fica para a história de origem |
|---|---|
| US1.1 criar e US1.2 consultar por ID | Listagem paginada, atualização, cancelamento |
| US2.1 caminho feliz com `Idempotency-Key` (aquisição da chave e recibo) | Replay, 409, 422, expiração, limpeza (US2.3); validações completas |
| Outbox + worker nos dois serviços (lote, `SKIP LOCKED`, `SendMessageBatch`) | Métricas do outbox; TC1 |
| US3.1 emissão com `issuance_processing` e cache (leitura, gravação com `validatedAt`) | Colisão de PAN, lápide, TC6/TC7, classes de falha, backoff (US3.2 a US3.5) |
| US4.1 aplicação do resultado `PENDING` → `ISSUED` | Resultado repetido e contraditório |
| US4.2 consulta consolidada com cartão emitido | Os demais estados de completude (TC8) |
| US5.2 consulta de cartão por ID (usada pela consolidada) | Listagem e transições |
| US7.1 JWT nos três serviços e client credentials | Testes de 401/403 por escopo |

Ajustes propostos nos critérios de US0.1:

- **AC0.1.1:** trocar "aguarda a emissão assíncrona" por "consulta a visão consolidada em intervalos até ela mostrar `ISSUED`, com limite de 60 s (NFR2, p99)". Sem limite, o smoke test pode ficar pendurado ou falhar de forma intermitente.
- **AC0.1.1:** "com a validação do produto pelo cache" não é observável de fora. Proposta: o smoke test confirma a existência da chave `cardforge:product:v1:{id}` no Redis, ou o incremento da métrica de miss/hit do cache. Sem isso, o critério não tem condição de falha.

### 4. Ajustes em critérios existentes

- **AC1.3.2 (alterar BIN):** definir o gatilho: "corpo de atualização contendo o campo `bin`, com qualquer valor, gera 400". Sem isso, o DTO pode simplesmente ignorar o campo e o teste passa sem provar nada.
- **AC1.4.4 e AC5.3.4 (sem exclusão):** "procura uma operação de exclusão" não é verificável. Proposta: "`DELETE` no recurso responde 405 e o OpenAPI não declara `DELETE`".
- **AC1.4.3:** verificável no card-service: um cartão de produto cancelado continua consultável e aceita transições de status. Indicar o serviço onde o teste roda.
- **AC2.1.5 (CPF duplicado, 409):** o gateway recebe dois tipos de 409 com semânticas opostas: CPF duplicado (não repetir) e requisição idempotente em andamento (repetir depois). Proposta: os dois `ProblemDetail` têm `type` distintos, e o critério diz isso (`engineering-standards.md`, códigos 409).
- **AC2.2.3 (catálogo com 401, 403 ou contrato inválido no cadastro):** como está, não tem resultado HTTP, então não tem critério de aprovação (`inception.md`: todo requisito precisa de critério de aprovação). Proposta provisória, a confirmar no Desenho Funcional (R-01): 202, validação adiada para a emissão, alerta de configuração e métrica com a classe `configuration`. Justificativa: BR3.4 e BR3.5 dizem que uma falha técnica que impede confirmar o produto não impede o cadastro, e um 503 bloquearia todas as adesões enquanto uma credencial estiver errada. Se o gate preferir 503, o critério precisa dizer isso; o que não pode é ficar sem resultado.
- **AC2.3.3 (TC-IDEM):** "recebe o replay, ou 409" é não determinístico e não prova o 409. Proposta de três critérios:
  - (a) duas requisições simultâneas com a mesma chave e o mesmo payload: exatamente um portador, uma solicitação e um evento de outbox; a outra resposta é o replay (202, mesmo corpo) ou 409;
  - (b) com a transação original mantida aberta além do `lock_timeout` (controle no teste), a repetição recebe 409 de forma determinística;
  - (c) mesma chave com payload diferente, depois do aceite da original: 422.
  O valor do `lock_timeout` precisa ser um parâmetro configurável, definido no Desenho Funcional.
- **AC3.1.1:** o card-service não conhece `PENDING`, que é estado do cardholder-service. Trocar "Dada uma solicitação `PENDING`" por "Dada uma solicitação sem resultado em `issuance_processing`". Acrescentar que a validade é calculada a partir do `Clock` injetado (`project.md` Mandated).
- **AC3.1.2 (TC-PAN):** forçar colisão exige uma porta para a fonte de números da conta, substituível no teste. Registrar como dependência de desenho (seção 6), não como detalhe da história.
- **AC3.2.2 (TC7):** "resposta mais antiga" precisa de uma ordem verificável. Remover o registro `ACTIVE` não basta: uma consulta ao catálogo que começou antes do cancelamento pode terminar depois e regravar `ACTIVE`. Proposta:
  - `CANCELED` e 404 gravam uma lápide no lugar do registro (continua sendo "um registro por produto"), e a lápide nunca é sobrescrita por `ACTIVE`;
  - `validatedAt` é o instante em que a consulta ao catálogo começou, não o instante da resposta;
  - teste determinístico: resposta `ACTIVE` atrasada no WireMock chega depois de uma resposta `CANCELED` e não altera o cache.
  Isso é compatível com `engineering-standards.md` ("remove qualquer registro `ACTIVE`, que nunca pode ser restaurado por uma resposta mais antiga") e com BR1.2 (`CANCELED` é terminal).
- **AC3.2.5 (NFR7):** "mede o tempo até a primeira emissão bloqueada" não é executável. Proposta com `Clock`: "Dada uma observação `ACTIVE` gravada em T e o produto cancelado depois de T, quando uma solicitação é processada em qualquer instante posterior a T + 5 min, então o catálogo é consultado e o desfecho é `FAILED` com `PRODUCT_CANCELED`".
- **AC3.4.1 (TC2):** indicar como a falha é provocada: o processamento é repetido com a mesma mensagem depois do commit (ou a confirmação falha por controle no teste). A republicação cria uma nova linha de outbox com novo `eventId`, com a mesma identidade de resultado (`issuanceRequestId`), conforme `engineering-standards.md` (Outbox).
- **AC3.4.2 (TC3):** acrescentar o desfecho do perdedor: "a mensagem perdedora termina republicando o resultado do vencedor e nunca grava `FAILED`". Ver 5.4: sem essa regra, o perdedor pode ser classificado como `NON_CANCELED_CARD_ALREADY_EXISTS` para a própria solicitação.
- **AC3.5.1:** deixar explícito que o expoente vem de `ApproximateReceiveCount` e que o teto de 300 s é aplicado depois do jitter; testável como função pura, mais um teste de integração que confirma a mudança de visibilidade.
- **AC3.5.3:** falta dizer o que acontece com a mensagem. Proposta: não confirma, aplica o mesmo backoff (assim a correção da configuração recupera sozinha, sem redrive manual), não grava resultado e registra alerta e métrica com a classe `configuration`, separada da classe `transient`. Hoje FR4.7 diz só "alerta, sem mascarar"; a disposição da mensagem precisa ser decidida aqui ou no Desenho Funcional.
- **AC3.5.4 (NFR8):** "poucos minutos" não é mensurável. Proposta: "para qualquer número de recebimentos, a próxima visibilidade calculada é de no máximo 300 s; com a dependência de volta, a mensagem é reprocessada em até 5 min".
- **AC4.1.3:** acrescentar "e a mensagem é confirmada". Sem isso, o resultado contraditório volta em ciclo até a DLQ.
- **AC5.2.3:** não tem caminho na API da R1. Não existe operação para pedir outro cartão ao mesmo portador: o cadastro exige CPF inédito (BR2.2) e renovação e substituição estão fora do escopo (brief §9). Proposta: reescrever no nível do dado: "Dado um cartão `CANCELED`, quando outro cartão do mesmo portador e produto é gravado, então o índice parcial permite" (teste de integração do repositório), com nota de que a R1 não expõe esse fluxo.
- **AC6.1.1:** indicar que a republicação da reconciliação passa pelo outbox (Forbidden: publicar no SQS direto da transação), na mesma transação que incrementa o contador e grava o instante da tentativa, e que a seleção do lote usa `FOR UPDATE SKIP LOCKED` para funcionar com mais de uma instância (NFR4).
- **AC6.2.3:** o contexto de tracing não atravessa o outbox sozinho. Proposta: "o `correlationId` e o contexto W3C capturados na transação de negócio são gravados na linha do outbox e enviados como atributos da mensagem".

### 5. Comportamentos ausentes que o código terá de ter

Cada item tem origem nos requisitos ou nos padrões; proponho como critério da história indicada.

1. **US2.1, replay e catálogo:** a verificação da chave existente vem antes da consulta ao catálogo (`engineering-standards.md`, ordem do cadastro). Critério: "um replay válido devolve o recibo original mesmo que o produto tenha sido cancelado depois do aceite".
2. **US2.3, só o aceite é memorizado:** CPF duplicado (409), produto rejeitado (422) e erro de validação não gravam a chave (a transação é desfeita ou nem começa). Critério: "uma repetição de uma requisição rejeitada é reavaliada, não reproduzida". Origem: FR2.5 (chave gravada na transação do cadastro).
3. **US2.3, chave expirada ainda não removida:** com `INSERT ... ON CONFLICT DO NOTHING`, uma chave vencida e ainda não limpa bloquearia um novo uso. Critério: "chave com mais de 24 h é tratada como nova, mesmo antes da limpeza". Origem: BR3.1 (replay só por 24 h).
4. **US3.3 e US3.4, desempate por violação de unicidade:** o `ON CONFLICT (pan_hmac)` só absorve a colisão de PAN; as violações de `cards (issuance_request_id)` e do índice parcial abortam a transação. Critério: "após uma violação de unicidade, a transação é desfeita e a decisão é refeita do início, lendo primeiro `issuance_processing`; se já houver resultado, ele é republicado; senão, a existência de cartão não cancelado do par gera `FAILED` com `NON_CANCELED_CARD_ALREADY_EXISTS` em nova transação". Origem: FR4.1, FR4.3, FR4.4.
5. **US3.2, lápide usada na decisão:** como `CANCELED` é terminal (BR1.2), uma lápide `CANCELED` ou 404 no cache pode encerrar a solicitação como `FAILED` sem nova consulta ao catálogo, e nunca autoriza emissão. Critério a confirmar no gate, porque altera o número de chamadas ao catálogo, não a regra.
6. **US3.2, Redis e catálogo indisponíveis:** FR4.2 exige falha técnica com retentativa; o rascunho só cobre o caso de cache vencido (TC6). Critério: "Redis indisponível e catálogo indisponível: nenhuma emissão, mensagem volta para retentativa". E também: "falha ao gravar no Redis não falha a emissão; registra a degradação".
7. **US3.5 e US7.1, token do Keycloak indisponível:** card-service e cardholder-service obtêm token por client credentials antes de chamar o catálogo. Critério: "falha ao obter token por indisponibilidade é transitória; `invalid_client` é falha de configuração". No cadastro, segue BR3.4 (aceita e adia a validação). Origem: FR9.2, `engineering-standards.md` (classificação de respostas).
8. **US3.5, fim do orçamento:** falta o critério de BR4.4 "depois disso, a solicitação fica retida para recuperação operacional, com alerta". Critério: "após `maxReceiveCount` a mensagem vai para a DLQ, a solicitação continua `PENDING` no cadastro e é elegível à reconciliação". No teste de integração, o `maxReceiveCount` é reduzido por configuração do init do LocalStack; o valor ≈ 29 fica só no ambiente.
9. **US4.1, mensagem de resultado inválida ou desconhecida:** o consumidor do cardholder-service precisa das mesmas classes de falha do card-service. Critério: "envelope inválido ou versão não suportada vai para a DLQ com alerta; resultado para `issuanceRequestId` inexistente gera alerta e vai para a DLQ". Origem: FR3.2, FR4.7 (simetria), ALWAYS consumidores idempotentes.
10. **US4.2, quinto estado:** produto sem nenhuma observação disponível (catálogo fora e nenhuma observação anterior guardada). Hoje os quatro casos de BR6.2 não cobrem isso. Critério: "a parte do produto aparece como indisponível, sinalizada, sem derrubar a consulta" (BR6.3). E também: "para `PENDING` e `FAILED`, a consulta não chama o card-service", o que torna esses casos completos mesmo com o card-service fora.
11. **US5.3, `X-Actor-Id` inválido:** critério: "header com mais de 100 caracteres gera 400". Origem: FR7.3.
12. **US6.1, resultado depois da suspensão:** critério: "um resultado que chega para uma solicitação suspensa é aplicado normalmente e encerra a suspensão". A suspensão é um marcador da solicitação, não um novo valor de `IssuanceStatus` (que continua `PENDING`, `ISSUED`, `FAILED`). E US6.3 precisa dizer como o operador retoma uma suspensa sem interface administrativa (fora do escopo): por comando ou SQL documentado no README.

### 6. Dependências técnicas ocultas

- **Origem do produto na consulta consolidada (US4.2):** o cache de produto está no card-service (brief §4), mas a consulta consolidada está no cardholder-service. Nenhum artefato diz de onde vem o produto nem o que é a "observação antiga" nesse serviço. Opções: (a) o cardholder-service consulta o catálogo e, em falha, usa uma observação própria guardada no cadastro, com o instante; (b) o card-service expõe o produto do seu cache. A escolha muda o desenho das unidades e deve ser fechada antes da Geração de Unidades; o Desenho de Contratos cobre só a forma da resposta.
- **Porta da fonte aleatória do PAN:** necessária para TC-PAN e AC3.1.3 (20 colisões); é um limite real (`engineering-standards.md`, interfaces só em limites reais).
- **Contexto de tracing no outbox:** colunas de `correlationId` e `traceparent` nas tabelas de outbox dos dois serviços (seção 4, AC6.2.3).
- **Profundidade das filas (US6.2):** a aplicação só consegue expor essa métrica consultando `GetQueueAttributes` periodicamente, com timeout. É um componente a mais, pequeno, mas precisa estar no plano.
- **Ocupação do BIN (FR4.9):** contagem de cartões por produto, periódica, dividida por 10^7; precisa de índice por `product_id`.
- **Jobs com várias instâncias:** worker do outbox, reconciliação e limpeza de chaves usam `FOR UPDATE SKIP LOCKED` ou `DELETE` em lote; nenhum lock distribuído extra é necessário (YAGNI), mas os critérios devem supor mais de uma instância (NFR4).
- **Cobertura combinada (US0.3):** o piso de 80% sobre unitários e integração exige `jacoco:merge` (ou `report` com os dois `.exec`) antes do `jacoco:check`. Configurar isso no esqueleto é o que garante que o piso nunca seja rebaixado depois (`team.md`).
- **Testes que param dependências:** TC1 e AC4.2.3 exigem pausar e retomar containers (LocalStack) ou simular a queda com WireMock (card-service, catálogo). Definir isso cedo evita testes críticos instáveis, o que importa porque retentativa automática de teste é proibida.

### 7. Ajustes em "Dependências entre histórias" e nas notas INVEST

- Acrescentar: US6.1 depende de US4.1 (TC10 exige o resultado aplicado no cadastro).
- Acrescentar: US4.2 depende da decisão sobre a origem do produto (seção 6).
- Acrescentar: US0.1 contém versões finas de US1.1, US1.2, US2.1, US3.1, US4.1, US4.2, US5.2 e US7.1 (seção 3).
- Nota **Small:** reescrever. US0.1 não é pequena e não há como torná-la pequena sem violar regras NEVER; US0.3, US2.3, US3.2 e US3.4 são grandes. O correto é registrar isso e deixar a Delivery Planning distribuir o esforço, não afirmar que cabem num incremento curto.
- Sugestão de sequência para US0.3: `./mvnw verify` com cobertura, `spotless:check`, SpotBugs e ArchUnit, mais o job de smoke test, nascem com o esqueleto (`team.md`); Trivy e Dependabot (AC0.3.3) podem entrar logo depois, sem bloquear o checkpoint do esqueleto. `team.md` só exige "junto com o esqueleto" para o `verify` e o job de smoke test.

### 8. Questões para as próximas etapas

- Código HTTP do cadastro quando o catálogo responde 401, 403 ou contrato inválido (R-01), com a minha proposta de 202 + alerta.
- Disposição da mensagem em falha de configuração na emissão (retentar com backoff ou DLQ).
- Origem do produto na consulta consolidada.
- Valor do `lock_timeout` da aquisição da chave de idempotência.
- Se a transição de `IssuanceStatus` (`PENDING` → `ISSUED`/`FAILED`) entra no histórico de transições (ALWAYS registrar o histórico de transições de status); FR7.3 cita só produto, portador e cartão.
- Se a consulta de portador (US2.4) retorna a data de nascimento: FR2.8 diz "sem expor além do necessário", mas o critério não diz se ela aparece.
- Se o CPF é aceito com máscara na entrada e normalizado (BR2.1 diz "armazenado normalizado", não diz o formato aceito).
- Coerência entre reconciliação e retry: com intervalo de 30 min entre tentativas e suspensão após 3, uma solicitação pode ser suspensa enquanto a cópia republicada ainda está dentro do seu orçamento de ~2 h. É aceitável se o item 5.12 for adotado; confirmar no Desenho Funcional junto com a margem das 3 h.

## Positions

- AGREE: Agrupamento por fluxo e comportamentos sob falha como critérios ligados a `TC*`; é o formato que permite escrever cada teste crítico antes da implementação.
- AGREE: Esqueleto primeiro (US0.1) com CI e ambiente nascendo junto; só a composição e o tamanho precisam ficar explícitos.
- OBJECT: AC2.2.3 sem resultado HTTP não tem critério de aprovação; propor um resultado provisório (202 + alerta) e marcar para confirmação.
- OBJECT: AC5.2.3 não tem caminho na API da R1; reescrever como teste do índice parcial, com a nota de que o fluxo não é exposto.
- OBJECT: AC2.3.3 é não determinístico e não prova o 409 por `lock_timeout`, exigido por `project.md`; separar nos três critérios propostos.
- OBJECT: AC3.2.5 e AC3.5.4 não são mensuráveis como estão; reescrever com `Clock` e com o teto de 300 s.
- OBJECT: AC3.2.2 (TC7) não define como identificar uma "resposta mais antiga"; sem lápide e `validatedAt` do início da consulta, uma consulta atrasada pode restaurar `ACTIVE`.
- OBJECT: AC3.1.1 usa `PENDING`, que não existe no card-service; trocar por "sem resultado em `issuance_processing`".
- OBJECT: US4.2 depende de uma decisão não tomada (origem do produto no cardholder-service) e não cobre produto sem observação disponível.
- OBJECT: A nota INVEST "Small" subestima US0.1, US0.3, US2.3, US3.2 e US3.4; com a capacidade de C-O2, isso precisa aparecer para a Delivery Planning.
