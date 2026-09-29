**Collaborator:** aidlc-design-agent

## Contribution

Foco: experiência de API de um produto sem interface gráfica. O "usuário" de cada história é quem lê a resposta HTTP, o contrato ou o README e precisa decidir o que fazer em seguida: renovar o token, corrigir o payload, repetir a chamada, aguardar ou informar o parceiro. Cada sugestão abaixo vem classificada conforme `project.md` (Way of Working): **[requisito]** quando só explicita algo que já está em `requirements.md`, no brief, em `engineering-standards.md` ou nas respostas Q1 a Q5; **[opção em avaliação]** quando é algo novo, que só vale se for aprovado em gate e deve ser decidido no Desenho de Contratos ou no Desenho Funcional. Nenhuma sugestão cria endpoint nem comportamento fora do brief.

### 1. Fidelidade das personas

As quatro personas seguem a Q1 (A) e o `stakeholder-map.md`. Ajustes pontuais:

- **P1 junta dois papéis que as histórias tratam de forma diferente.** US2.x a US5.x falam do gateway **em execução** (sistema que chama a API). US8.1 e US8.3 ("integrar sem depender de conversa", "testar a integração rapidamente") falam do **time que integra o gateway**, que é quem o `stakeholder-map.md` lista como destinatário dos contratos OpenAPI. Proposta de texto para o campo "Papel" de P1: *"Único cliente técnico do CardForge: o sistema do gateway em execução e o time que o desenvolve e integra aos contratos."* [requisito: `stakeholder-map.md`, Requisitos de comunicação]
- **P1 representa varejistas e consumidores finais de forma indireta.** O mapa lista os dois como interessados que influenciam. Proposta: acrescentar em P1 a linha *"Representa indiretamente os varejistas parceiros (não perder nem travar adesões) e o consumidor final (não esperar pela emissão no checkout)"*. Assim, os "para" de US2.1 e US2.2 têm um dono explícito. [requisito: `stakeholder-map.md`; Q1 (A) "em nome de varejistas e consumidores"]
- **P1, campo "Dores":** a dor "respostas ambíguas que não dizem se o pedido foi aceito" é a mais importante da persona, mas hoje nenhuma história cobre a parte de "o que fazer com cada erro" (ver 2.1). Proposta: completar a dor com *"...e erros com o mesmo código HTTP que pedem ações diferentes (repetir, corrigir, desistir)"*.
- **P4, campo "Objetivos":** o `stakeholder-map.md` define para Segurança/Compliance um canal próprio: documentação versionada com os **controles de segurança e privacidade adotados**. A Q5 (A) também diz "Segurança (controles documentados)". Nenhuma história entrega esse documento (ver 2.9). O objetivo de P4 fica correto; falta a história ou o critério que o cumpra.
- **P2 e P3:** fiéis ao brief e ao mapa. Não há ajuste.

### 2. Experiência de API por história

#### 2.1 Erros que dividem o mesmo código HTTP precisam de identidade própria (US8.1, transversal)

Pelos padrões de `engineering-standards.md`, o gateway recebe o mesmo status para situações que exigem ações opostas:

| Status | Causas nas histórias | Ação esperada do gateway |
|---|---|---|
| 409 | CPF já cadastrado (AC2.1.5); requisição idempotente em andamento (AC2.3.3); BIN duplicado (AC1.1.3); transição inválida (AC1.3.3, AC1.4.2, AC5.1.2, AC5.2.2) | Desistir e informar o parceiro / **repetir mais tarde com a mesma chave** / corrigir a requisição |
| 422 | Regra de cadastro violada (AC2.1.2 a AC2.1.4); produto inexistente ou cancelado (AC2.1.6); chave reutilizada com payload diferente (AC2.3.2) | Corrigir campos / informar que o produto não existe ou foi descontinuado / gerar nova chave (bug no cliente) |

Sem um identificador estável por causa, o gateway só consegue distinguir os casos lendo o texto do `detail`, o que quebra a integração a cada mudança de redação. Proposta de critério para US8.1 [requisito: FR10.1 (`ProblemDetail` com os códigos do padrão) e P1 "respostas ambíguas"; o formato do identificador fica para o Desenho de Contratos]:

- **AC8.1.3 (proposto):**
  - Dadas duas causas de erro que usam o mesmo status HTTP,
  - quando retornadas,
  - então cada uma tem um `type` (URI do RFC 9457) estável e distinto, documentado no OpenAPI junto com a ação esperada do cliente. Nenhuma decisão do cliente depende do texto de `title` ou `detail`.

No mínimo, os pares que precisam ser distinguíveis: CPF duplicado × requisição em andamento (409); produto inexistente × produto cancelado × regra de campo × chave reutilizada (422).

#### 2.2 Erros nunca devolvem dado pessoal (US8.1, US2.1, US2.3)

A lista de campos em erros de validação (FR2.2) costuma ecoar o valor rejeitado. No cadastro, esse valor seria o CPF ou a data de nascimento. Proposta [requisito: brief §6, "CPF mascarado nas respostas"; NFR9]:

- **AC8.1.4 (proposto):**
  - Dado qualquer erro no cadastro ou na consulta de portador,
  - quando retornado,
  - então o `ProblemDetail` identifica o campo e a regra violada, mas nunca contém o CPF, a data de nascimento ou o PAN enviados. Em AC2.3.2 (payload diferente), a resposta também não revela o payload original.

#### 2.3 O recibo de aceite não promete emissão (US2.1, US2.2)

Do ponto de vista do gateway, o 202 de AC2.1.1 e o 202 de AC2.2.1 (catálogo fora do ar) são idênticos, e é correto que sejam, porque o recibo é estável (BR3.1). A consequência precisa ficar escrita para o integrador: **202 significa "aceito e rastreável", não "cartão emitido"**. Um cadastro aceito com o catálogo fora ainda pode terminar em `FAILED` com `PRODUCT_NOT_FOUND`. Proposta [requisito: BR3.4, BR4.5, FR2.5]:

- Acrescentar a AC2.2.1: *"...e o desfecho (inclusive `FAILED` com `PRODUCT_NOT_FOUND` ou `PRODUCT_CANCELED`) aparece depois na consulta consolidada (US4.2)"*.
- Acrescentar a US8.1 (descrição do contrato): o `POST /api/v1/cardholders` documenta que o desfecho só é conhecido pela consulta consolidada e que, na R1, não existe notificação ativa ao cliente. Também documenta o tempo-alvo de emissão (NFR2, meta não medida) como referência para a cadência de consulta. [requisito: FR10.2; a orientação de cadência é documentação, não comportamento novo]
- `Location` (AC2.1.1): hoje não se diz para onde aponta. Para o gateway, o útil é apontar para o recurso que vai mostrar o desfecho (a consulta consolidada do portador). [opção em avaliação: Desenho de Contratos]

#### 2.4 Replay e 24 h vistos pelo cliente (US2.3)

- **AC2.3.3:** o 409 por requisição em andamento é a única resposta do cadastro que pede "repita depois, com a mesma chave". Proposta: o `type` do 409 (ver 2.1) diz isso de forma explícita. Um header `Retry-After` seria o complemento natural. [`type`: requisito via 2.1; `Retry-After`: opção em avaliação]
- **AC2.3.5** descreve um mecanismo interno (a limpeza roda), sem nada que o gateway observe. Proposta de reescrita em termos do cliente, mantendo o teste do mecanismo: *"Dada uma chave aceita há mais de 24 h, quando o mesmo cliente a reenvia, então a requisição é tratada como nova (e, com o mesmo CPF, recebe 409 de CPF duplicado, não o recibo)."* [requisito: BR3.1, FR2.6, FR2.7]
- **Limitação a documentar:** se o gateway perder a resposta do cadastro e só repetir depois de 24 h, recebe 409 de CPF duplicado e não tem como recuperar o `cardholderId`, porque a R1 não oferece busca por CPF. Proposta: registrar como limitação conhecida no README e na descrição do endpoint no OpenAPI. [requisito: FR11.4 (limitações conhecidas); não propõe busca por CPF, que seria escopo novo]

#### 2.5 Validações: 400 ou 422 precisa ser previsível (US1.1, US2.1)

BIN fora do formato gera 400 (AC1.1.2, FR1.1). Nome fora de 3 a 120 caracteres gera 422 (AC2.1.4, FR2.2). As duas são restrições de campo e, para o integrador, parecem da mesma natureza. As histórias estão rastreadas e corretas; o que falta é a regra explícita. Proposta: o OpenAPI documenta o critério (400 = payload malformado ou header ausente; 422 = regra de negócio, conforme `engineering-standards.md`), diz em qual grupo cai cada validação do cadastro e o que acontece quando os dois tipos ocorrem na mesma requisição. Proposta de critério para US2.1: **todas** as violações de regra de uma requisição voltam juntas na lista de campos, para o gateway corrigir tudo em uma única volta. [requisito: FR2.2 "com a lista de campos"; a precedência 400 × 422 fica para o Desenho de Contratos]

#### 2.6 Consulta de portador: data de nascimento (US2.4)

FR2.8 diz "sem expor a data de nascimento além do necessário", mas US2.4 não tem critério para isso. Proposta: **AC2.4.3** fixa a política (omitir, ou expor de forma reduzida) e a torna testável. [requisito: FR2.8; a forma exata fica para o Desenho de Contratos]

#### 2.7 Consulta consolidada: critérios que qualquer formato precisa cumprir (US4.2)

O formato está em aberto para o Desenho de Contratos (`requirements.md`, Questões em aberto). Sem antecipar o formato, proponho critérios que qualquer formato precisa cumprir para o gateway **não informar algo errado ao parceiro**, que é o "para" da história:

- **AC4.2.5 (reescrita, TC8):** *"...então os quatro casos são distinguíveis **por campos estruturados da resposta**, e não pelo status HTTP, pela simples ausência de um campo ou pelo texto de uma mensagem."* O texto atual ("distinguíveis") não diz como distinguir, e um teste que compara as respostas inteiras passaria com sinalização só textual. [requisito: BR6.2, TC8]
- **AC4.2.6 (proposto):** nos quatro casos a resposta é 200. A indisponibilidade de uma parte nunca vira 5xx nem 206. [requisito: BR6.3, FR8.2]
- **AC4.2.7 (proposto):** a situação da emissão (`PENDING`/`ISSUED`/`FAILED`) vem do cardholder-service e nunca é rebaixada pela falha do card-service. Em AC4.2.3, a resposta continua `ISSUED` e ainda traz o `cardId`, que o cardholder-service já conhece pelo resultado (FR3.2, FR5.1). Só os detalhes do cartão ficam indisponíveis. [requisito: BR6.2, FR5.1]
- **Caso ausente em BR6.2:** produto **sem nenhuma observação utilizável** (catálogo fora do ar e nada em cache). BR6.2 cobre só "observação antiga". BR6.3 exige que a consulta não caia, mas nenhum critério define o que o gateway vê. Proposta: **AC4.2.8**, com a parte do produto sinalizada como indisponível, no mesmo padrão do cartão. [requisito: BR6.3; ressalva para o gate, porque amplia os quatro casos de BR6.2 em um quinto]
- **`PENDING` longo:** o gateway não distingue "em processamento há 3 s" de "retido há 5 h, suspenso para intervenção" (FR6.2). Expor o instante do aceite (`requestedAt`, dado que o cardholder-service já tem) permite ao gateway decidir quando escalar, sem expor a mecânica interna. [opção em avaliação: Desenho de Contratos]

#### 2.8 Mudança de status: repetir não pode parecer erro (US5.1, US5.2)

AC5.1.2 responde 409 para "transição para o mesmo status". Esse caso não aparece em FR7.1 nem em BR2.5 de forma explícita, e a origem não está documentada (regra da Inception: "não introduzir requisito sem documentar a origem"). Do ponto de vista do gateway, um bloqueio aplicado cuja resposta se perdeu, quando repetido, volta como erro, embora o estado desejado já tenha sido alcançado. Proposta, em ordem de preferência:

1. Transição para o status atual responde sucesso com o estado atual e não grava nova entrada no histórico (operação idempotente). [opção em avaliação: Desenho de Contratos, junto com o formato dos endpoints de status]
2. Se o 409 for mantido, ele usa um `type` distinto de "transição inválida", para que o cliente possa tratá-lo como sucesso (ver 2.1).

Em qualquer das duas, AC5.1.2 precisa citar a origem da regra.

**AC5.2.3 descreve um fluxo que o gateway não consegue executar.** "Quando o mesmo portador pede um novo cartão do produto": na R1, a única forma de criar uma solicitação de emissão é o cadastro (FR2.5). O CPF é único mesmo para portador cancelado (BR2.2), e substituição de cartão está fora do escopo (brief §9). O critério sugere ao integrador uma capacidade que não existe. Proposta: reescrever como comportamento do índice, verificável em teste de componente (*"Dado um cartão `CANCELED`, então ele não conta para a regra de um cartão não cancelado por portador e produto (BR4.3)"*), sem a frase "o portador pede", e registrar no README que a R1 não oferece novo cartão para o mesmo portador. [requisito: BR4.3, brief §9]

**AC5.1.3 (emissão segue para portador bloqueado):** a limitação está documentada no README (FR4.8), mas quem precisa dela é o time do gateway, que lê o contrato. Proposta: repetir a limitação na descrição do endpoint de status do portador no OpenAPI. [requisito: FR4.8, FR10.2]

#### 2.9 Controles documentados para Segurança/Compliance (US7.2 ou US8.2)

Como apontado em 1, a Q5 (A) e o `stakeholder-map.md` pedem um documento de controles para P4. Hoje P4 recebe ADRs (US8.4), que registram decisões e não evidência de controle. Proposta de critério, em US7.2 (preferido, porque a dor de P4 é "controles só declarados, sem evidência"):

- **AC7.2.5 (proposto):**
  - Dado o README,
  - quando lido,
  - então há uma seção de controles de segurança e privacidade adotados, que lista cada controle (PAN cifrado com versão da chave, HMAC com chave separada, só `panLastFour`, CPF mascarado, nada sensível em logs e mensagens, histórico auditável, chaves validadas na inicialização) com o teste que o comprova, e declara que a release não afirma conformidade integral com PCI-DSS ou LGPD.

[requisito: Q5 (A); `stakeholder-map.md`; correção de `project.md` sobre conformidade. É parte do README já obrigatório, e não um documento novo.]

#### 2.10 Autenticação vista pelo cliente (US7.1, US8.1)

- AC8.1.2 diz que **qualquer** erro sai em `application/problem+json`. Os 401 e 403 são gerados pela cadeia de segurança, antes dos controllers, e ficam fora do `@RestControllerAdvice`. Proposta: explicitar em AC7.1.1 e AC7.1.2 que o corpo também é `ProblemDetail`. O 401 mantém o header `WWW-Authenticate` padrão do OAuth2, para o cliente saber que deve renovar o token. [requisito: FR10.1, AC8.1.2]

#### 2.11 Postman demonstra o replay (US8.3)

Se a collection gerar uma `Idempotency-Key` nova a cada envio, que é o comportamento cômodo, ela nunca demonstra o replay de AC2.3.1. Proposta para AC8.3.1: a collection tem uma requisição que reenvia a mesma chave e mostra o 202 com `Idempotent-Replayed: true`. [requisito: FR11.3, "Idempotency-Key" no Mandated de `project.md`; cabe no nível essencial do corte 3]

#### 2.12 Emissão vista pelo gateway (US3.1, US3.3, US3.4)

Essas histórias têm o gateway como ator, mas todos os critérios são internos (outbox, transação, índice). É correto para testar a garantia. Proposta leve: cada uma ganha um critério do que o gateway **vê**, sempre pela consulta consolidada. Exemplo para US3.3: *"...e a consulta consolidada mostra `FAILED` com `NON_CANCELED_CARD_ALREADY_EXISTS`"*. Isso também reduz a dependência de sequência apontada nas notas de INVEST, porque o critério observável fica declarado em cada história. [requisito: BR4.5]

### 3. Os benefícios ("para") são valor real?

| História | Benefício atual | Avaliação | Proposta |
|---|---|---|---|
| US0.1 | "validar a integração antes de qualquer funcionalidade detalhada" | Valor da pessoa desenvolvedora, não do gateway | "...**para** ter, desde o início, um fluxo real e estável contra o qual integrar e testar" |
| US0.3 | "que nada entre em `main` sem prova de funcionamento" | Valor de processo; o operador não atua em `main` | "...**para** operar apenas versões que passaram pelos testes críticos e pelo smoke test" |
| US3.4 | "confiar que cada solicitação tem no máximo um desfecho terminal" | Repete a propriedade do sistema | "...**para** informar ao parceiro um desfecho que não muda depois de comunicado" |
| US7.1 | "que só clientes autorizados acessem o CardForge" | Valor da RPE/Segurança, não do gateway | "...**para** saber, pela resposta, se deve renovar o token (401) ou corrigir a configuração de escopos (403)"; como alternativa, mover o ator para P4 |
| US8.4 | "entender as escolhas e os riscos aceitos" | Aceitável para P4 (ex.: risco do Spring Boot 3.5) | Manter |
| Demais | | Valor real e ligado aos objetivos e às dores da persona | Manter |

### Sources

- `personas.md`, `stories.md`, `user-stories-questions.md` (Q1 a Q5), `user-stories-assessment.md`
- `requirements.md` (FR1 a FR11, BR1.1 a BR6.3, Questões em aberto), `requirements-analysis/reviews/review-01.md` (R-01)
- `ideation/intent-capture/stakeholder-map.md`
- `product-brief.md` §3, §5, §6, §9; `engineering-standards.md` (códigos HTTP, idempotência)
- `project.md` (Way of Working, Forbidden, Mandated, Corrections); `phases/inception.md`

## Positions

- AGREE: As quatro personas e a prioridade (P1 > P3 > P2 > P4) seguem a Q1 e o `stakeholder-map.md`; os ajustes em 1 são de texto, não de estrutura.
- AGREE: Agrupar por fluxo e tratar falhas como critérios de aceite (Q2, Q3) deixa claro, para o gateway, o que acontece em cada caso de falha dentro da própria história.
- AGREE: Deixar o código HTTP de AC2.2.3 (catálogo 401/403/contrato inválido) para o Desenho Funcional (R-01). Como insumo: prefiro 202, porque um erro de configuração interno não é culpa do cliente e recusar o cadastro contraria o espírito de BR3.5, desde que o alerta seja gerado e a emissão nunca vire `PRODUCT_NOT_FOUND`.
- OBJECT: AC5.2.3 descreve "o portador pede um novo cartão", fluxo que a API da R1 não oferece (cadastro é a única origem de solicitação, CPF é único e substituição está fora do escopo); precisa ser reescrito como comportamento do índice.
- OBJECT: AC5.1.2 introduz o 409 para "transição para o mesmo status" sem origem documentada e torna erro a repetição segura de uma chamada já aplicada; é preciso citar a origem e tornar a operação idempotente ou dar a esse caso um `type` distinto.
- OBJECT: Nenhuma história entrega os "controles documentados" que a Q5 (A) e o `stakeholder-map.md` prometem a Segurança/Compliance; falta um critério como o AC7.2.5 proposto.
- OBJECT: US8.1 não exige identidade estável para erros que dividem o mesmo status (409 e 422), o que deixa o gateway dependente do texto do erro e sem saber se deve repetir, corrigir ou desistir, que é justamente a dor declarada de P1.
- OBJECT: AC4.2.5 ("distinguíveis") não é verificável como está: um teste passaria com sinalização só textual; precisa exigir distinção por campos estruturados.
