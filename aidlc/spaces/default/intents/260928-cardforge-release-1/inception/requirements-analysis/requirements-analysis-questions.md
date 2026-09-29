# Análise de Requisitos: perguntas

O product brief, os padrões de engenharia, as decisões de `project.md` e as etapas anteriores já cobrem quase todos os requisitos. As perguntas abaixo tratam só das lacunas que o brief deixa em aberto e que mudam o comportamento verificável do sistema.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Como identificar as regras de negócio nos requisitos e nos testes?

Contexto: o brief numera as regras como `BR-P1`, `BR-H3`, `BR-I2`. A verificação de rastreabilidade do workflow espera o formato `BR{grupo}.{seq}` (ex.: `BR1.1`). Esta escolha vale para requisitos, design e as tags dos testes.

A. Manter os IDs do brief (`BR-P1`...) como canônicos e incluir uma tabela de correspondência para o formato `BR{grupo}.{seq}`
B. Renumerar tudo para `BR{grupo}.{seq}` e citar o ID do brief entre parênteses
C. Not yet defined
X. Other (please specify)

[Answer]:B — Mapeamento de grupos: P→1, H→2, R→3, I→4, C→5, V→6. Exemplo: BR1.1 (BR-P1), BR4.2 (BR-I2).

## Q2. Qual o tamanho de página das listagens (produtos, cartões de um portador)?

A. Padrão 20, máximo 100; valor acima do máximo é rejeitado com 400
B. Padrão 20, máximo 100; valor acima do máximo é reduzido para 100
C. Padrão 50, máximo 200
D. Not yet defined
X. Other (please specify)

[Answer]:A

## Q3. O que acontece com uma emissão pendente se o portador for bloqueado ou cancelado antes de ela ser processada?

Contexto: o brief não trata esse caso. A cascata de status entre portador e cartões está fora da release (seção 9 do brief), e o motivo de falha só tem três valores: `PRODUCT_NOT_FOUND`, `PRODUCT_CANCELED` e `NON_CANCELED_CARD_ALREADY_EXISTS`.

A. A emissão segue normalmente: o status do portador não afeta a emissão nesta release (sem cascata, sem motivo de falha novo)
B. A emissão falha com um motivo novo (ex.: `CARDHOLDER_NOT_ACTIVE`), o que amplia o conjunto de motivos do brief
C. Not yet defined
X. Other (please specify)

[Answer]:X — A emissão segue normalmente (opção A), registrada como limitação conhecida da R1 e tratada junto com a cascata de status na R1.1.

## Q4. Um produto cancelado pode ter os dados descritivos (nome, descrição) atualizados?

Contexto: `CANCELED` é terminal (BR-P2) e o BIN é imutável (BR-P1). O brief não diz se nome e descrição continuam editáveis.

A. Não: produto cancelado é somente leitura; tentativa de atualização responde 409
B. Sim: nome e descrição continuam editáveis; status e BIN não
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q5. Como o CPF aparece mascarado nas respostas?

Contexto: o brief exige CPF mascarado nas respostas, sem definir o formato.

A. Só os 2 dígitos verificadores e os 3 anteriores visíveis: `***.***.789-01`
B. Só os 3 dígitos do meio visíveis: `***.456.***-**`
C. Nenhum dígito visível: o CPF não aparece nas respostas
D. Not yet defined
X. Other (please specify)

[Answer]: X — Formato do brief: ***.456.789-** (visíveis apenas do 4º ao 9º dígito), o mesmo padrão usado pela administração pública federal, como no Portal da Transparência.

## Q6. Como o gateway informa o ator original, que vai para o histórico de transições?

Contexto: o brief diz que o CardForge registra o cliente técnico (o `client_id` do token) e, quando informado pelo gateway, o ator original.

A. Header opcional `X-Actor-Id` (texto de até 100 caracteres, sem dados pessoais); ausente, o histórico guarda só o `client_id`
B. Claim no próprio token emitido para o gateway
C. Não registrar o ator original nesta release; apenas o `client_id`
D. Not yet defined
X. Other (please specify)

[Answer]:A

## Q7. Quais os parâmetros da reconciliação de solicitações sem desfecho?

Contexto: está decidido que a reconciliação republica solicitações pendentes mais antigas que o orçamento de retry (~2 h), em lotes limitados, e suspende após N tentativas. Os números ficaram em aberto.

A. Idade mínima de 3 h; execução a cada 5 min; lote de até 50; intervalo mínimo de 30 min entre tentativas da mesma solicitação; suspensão após 3 tentativas, com alerta. Todos os valores configuráveis
B. Idade mínima de 2 h 30 min; execução a cada 1 min; lote de até 100; suspensão após 5 tentativas; configuráveis
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q8. Qual formato é aceito para o header `Idempotency-Key`?

A. UUID (qualquer versão); fora desse formato responde 400
B. Qualquer texto ASCII visível de 1 a 255 caracteres
C. Not yet defined
X. Other (please specify)

[Answer]: X — UUID (qualquer versão), aceito com ou sem aspas, já que o draft da IETF define o header como sf-string; fora desse formato responde 400.

## Consolidated Summary Confirmation

Resumo das respostas:

- IDs de regra (Q1): renumerar para `BR{grupo}.{seq}` citando o ID do brief entre parênteses; grupos P→1, H→2, R→3, I→4, C→5, V→6 (ex.: BR1.1 (BR-P1), BR4.2 (BR-I2)).
- Paginação (Q2): padrão 20, máximo 100; acima do máximo, 400.
- Emissão com portador bloqueado ou cancelado (Q3): segue normalmente; registrada como limitação conhecida da R1, a tratar junto com a cascata de status na R1.1.
- Produto cancelado (Q4): somente leitura; atualização responde 409.
- Máscara do CPF (Q5): `***.456.789-**` (visíveis do 4º ao 9º dígito).
- Ator original (Q6): header opcional `X-Actor-Id` (até 100 caracteres, sem dados pessoais); ausente, o histórico guarda só o `client_id`.
- Reconciliação (Q7): idade mínima de 3 h; a cada 5 min; lote de até 50; 30 min entre tentativas da mesma solicitação; suspensão após 3 tentativas, com alerta; tudo configurável.
- `Idempotency-Key` (Q8): UUID de qualquer versão, com ou sem aspas (sf-string); fora disso, 400.

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
