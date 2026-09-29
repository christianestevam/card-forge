# Planejamento de Entrega: perguntas

Base:
- `unit-of-work.md`, `unit-of-work-dependency.md` e `unit-of-work-story-map.md`: 6 unidades; U1 é o esqueleto; U2, U3 e U4 podem ser feitas em paralelo.
- `contract-summary.md`, `requirements.md`, `stories.md` (tamanhos relativos) e `team-practices.md`.
- `feasibility-assessment.md`: prazo de 05/10/2026, uma pessoa em dedicação parcial.

Nesta etapa, um **Bolt** é uma passada de construção sobre uma parte do trabalho que termina em algo que roda. Aqui escolhemos a ordem dos Bolts.

**Já decidido, não é perguntado:**
- o esqueleto vem primeiro (`team.md`);
- a sua preferência, registrada na Geração de Unidades, é risco primeiro, U1 → U2 → U3 → U4 → U5 → U6, em sequência;
- não há dependências externas nesta release (Viabilidade Q4 e Q5);
- no escopo mvp não houve formação de times, então todo Bolt é executado pelo agente desenvolvedor, com você aprovando.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Confirma a ordem risco primeiro, U1 → U2 → U3 → U4 → U5 → U6, um Bolt de cada vez?

Contexto: U2 (emissão) concentra os testes críticos de maior risco (TC2 a TC7, TC-PAN), e U3 (cadastro) concentra TC1, TC9, TC10 e TC-IDEM. U4 (catálogo) é pequena e poderia vir antes de U2 e U3, mas o esqueleto já cobre o que elas precisam do catálogo.

A. Sim: exatamente essa ordem, sem paralelismo
B. Mesma ordem, mas U4 antes de U2 e U3 (catálogo completo cedo, menor risco)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Sim: exatamente essa ordem (U1 → U2 → U3 → U4 → U5 → U6), sem paralelismo **Mode:** chat

## Q2. Usar um modelo formal de pontuação (tipo WSJF, que pesa valor e urgência contra tamanho)?

A. Não: o argumento é risco primeiro, com o esqueleto na frente, e fica registrado em prosa
B. Sim, WSJF (especifique os pesos em X)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Não: argumento de risco primeiro, com o esqueleto na frente, registrado em prosa **Mode:** chat

## Q3. Qual o tamanho de um Bolt?

A. Uma unidade por Bolt (6 Bolts, B1 a B6)
B. Juntar U5 e U6 num Bolt final (5 Bolts)
C. Not yet defined
X. Other (please specify)

[Answer]: X — 3 Bolts: B1 = U1; B2 = U2 + U3; B3 = U4 + U5 + U6 **Mode:** chat

## Q4. Como a construção percorre as etapas?

Contexto: o padrão é unidade a unidade. Cada unidade passa pelo seu Desenho Funcional, NFR, Infraestrutura e Code Generation, e em seguida pelo checkpoint verificado, antes da próxima unidade começar. Pela sua prática, nada além do esqueleto é construído até ele passar e você aprovar.

A. Unidade a unidade, em série (o padrão)
B. Etapa a etapa: primeiro o desenho de todas as unidades, depois o código (o esqueleto continua passando por tudo primeiro)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Unidade a unidade, em série **Mode:** chat

## Q5. Como organizar a execução da construção?

A. Tudo nesta sessão, com você aprovando cada checkpoint (uma pessoa só)
B. Cada time dono de uma unidade, com aprovação independente
C. Not yet defined
X. Other (please specify)

[Answer]: A. Tudo nesta sessão, com você aprovando cada checkpoint **Mode:** chat

## Q6. O que mais preocupa você nesta construção, para atacarmos cedo? (select all that apply)

A. Prazo: a U1 é a maior unidade (XL) e precisa passar antes de tudo
B. Testes críticos de concorrência e falha ficarem instáveis (retentativa de teste é proibida)
C. Integração do ambiente local: Keycloak, LocalStack e geração de segredos no primeiro comando
D. None
X. Other (please specify)

[Answer]: A, C. Prazo (U1 é XL e precisa passar antes de tudo); integração do ambiente local (Keycloak, LocalStack, geração de segredos no primeiro comando) **Mode:** chat

## Consolidated Summary Confirmation

Resumo das respostas:

- Ordem (Q1): risco primeiro, U1 → U2 → U3 → U4 → U5 → U6, uma unidade de cada vez.
- Pontuação (Q2): sem modelo formal; argumento de risco primeiro com o esqueleto na frente.
- Bolts (Q3): três — B1 = U1 (esqueleto); B2 = U2 + U3 (emissão e cadastro, onde estão os testes críticos); B3 = U4 + U5 + U6 (catálogo, consulta consolidada e documentação).
- Construção (Q4): unidade a unidade, em série.
- Execução (Q5): tudo nesta sessão, com você aprovando cada checkpoint.
- Preocupações (Q6): prazo com a U1 grande; integração do ambiente local (Keycloak, LocalStack, segredos) no primeiro comando.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
