# Justificativa de Risco e Sequência: CardForge Release 1.0

Base:
- `unit-of-work-dependency.md` (grafo);
- `stories.md` (tamanhos relativos e testes críticos);
- `feasibility-assessment.md` e `raid-log.md` (risco de prazo);
- `team-practices.md`;
- `delivery-planning-questions.md` (Q1 a Q6).

Um **Bolt** é uma passada de construção sobre uma parte do trabalho que termina em algo que roda.

## Heurística usada

**Esqueleto primeiro, depois risco primeiro** (walking skeleton de Cockburn, seguido de risk-first de Boehm). Não se usa pontuação formal do tipo WSJF (Q2). O **walking skeleton** é a versão mínima que percorre o sistema inteiro e prova que as peças se conectam.

## Por que esta ordem

1. **U1 primeiro (B1):**
   - é obrigação do time (`team-practices.md`, Walking Skeleton) e a única raiz do grafo;
   - concentra as duas preocupações declaradas (Q6): o prazo, porque é a maior unidade, e a integração do ambiente local (Keycloak, LocalStack, segredos);
   - resolver isso cedo reduz o risco de todo o resto.
2. **U2 antes de U3 (B2):**
   - a emissão concentra os testes críticos de maior risco técnico (TC2 a TC7, TC-PAN: concorrência, reentrega, cache e colisão de PAN);
   - é a autoridade das garantias de desfecho único e da regra dos 5 minutos;
   - U3 depende do comportamento de U2 para demonstrar TC9 e TC10 (reconciliação e resultado perdido).
3. **U3 em seguida (B2):** idempotência do cadastro (TC-IDEM), outbox sob falha da SQS (TC1) e reconciliação completam as garantias de integridade.
4. **U4, U5 e U6 por último (B3):**
   - o catálogo completo é pequeno e de baixo risco, porque o esqueleto já cobre o que U2 e U3 precisam dele;
   - a consulta consolidada depende de U2, U3 e U4 (grafo);
   - U6 consolida a documentação que cada unidade foi atualizando.

## Aderência ao grafo

A ordem U1 → U2 → U3 → U4 → U5 → U6 é uma ordenação topológica válida do grafo de `unit-of-work-dependency.md`:
- U2, U3 e U4 dependem só de U1;
- U5 depende de U2, U3 e U4;
- U6 depende de todas.

Não há desvio da topologia. A execução em série, sem paralelismo entre U2, U3 e U4, é uma escolha de capacidade: uma pessoa em dedicação parcial (Q1, Q4).

## Riscos e tratamento na sequência

| Risco | Onde aparece | Tratamento |
|---|---|---|
| Prazo: U1 é XL e bloqueia tudo (Q6) | B1 | O esqueleto contém só os caminhos finos exigidos pelas regras NEVER; o Trivy e o Dependabot podem entrar logo depois do checkpoint (US0.3) |
| Integração do ambiente local (Q6) | B1 | Container de inicialização e realm como template no primeiro commit; versão do LocalStack fixada e verificada no esqueleto |
| Testes críticos instáveis (retentativa proibida) | B2 | `Clock` controlado, barreiras entre threads e containers próprios para os testes que derrubam dependências (`stories.md`, Convenções) |
| Escopo não caber no prazo | B3 | Os cortes de profundidade pré-aprovados (métricas, runbooks, Postman) são aplicados na ordem da Definição de Escopo e registrados como débito |
