# Mapa de Histórias por Unidade: CardForge Release 1.0

Base: `stories.md` (US0.1 a US8.4), `unit-of-work.md` e `units-generation-questions.md`.

## Mapa

| História | Unidade | Diretório | Observação |
|---|---|---|---|
| US0.1 | U1 | u1-walking-skeleton | Fluxo ponta a ponta e smoke test |
| US0.2 | U1 | u1-walking-skeleton | Ambiente e segredos |
| US0.3 | U1 | u1-walking-skeleton | CI (Trivy e Dependabot logo depois do checkpoint) |
| US1.1 | U4 | u4-product-catalog | Caminho fino criado na U1; erros pelo handler base de ProblemDetail do `cardforge-platform` |
| US1.2 | U4 | u4-product-catalog | Consulta por ID criada na U1; listagem na U4 |
| US1.3 | U4 | u4-product-catalog | |
| US1.4 | U4 | u4-product-catalog | |
| US2.1 | U3 | u3-cardholder-registration | Caminho feliz criado na U1 |
| US2.2 | U3 | u3-cardholder-registration | TC1 |
| US2.3 | U3 | u3-cardholder-registration | TC-IDEM |
| US2.4 | U3 | u3-cardholder-registration | |
| US3.1 | U2 | u2-card-issuance | Emissão básica criada na U1; TC-PAN na U2 |
| US3.2 | U2 | u2-card-issuance | TC6, TC7 |
| US3.3 | U2 | u2-card-issuance | TC5 |
| US3.4 | U2 | u2-card-issuance | TC2, TC3, TC4 |
| US3.5 | U2 | u2-card-issuance | |
| US3.6 | U2 | u2-card-issuance | |
| US4.1 | U3 | u3-cardholder-registration | `PENDING` → `ISSUED` criado na U1 |
| US4.2 | U5 | u5-consolidated-view | Caso com cartão emitido criado na U1; TC8 |
| US5.1 | U3 | u3-cardholder-registration | |
| US5.2 | U2 | u2-card-issuance | Consulta por ID criada na U1 |
| US5.3 | U3 | u3-cardholder-registration | Transversal: histórico do cartão em U2 e do produto em U4 |
| US6.1 | U3 | u3-cardholder-registration | TC9, TC10 |
| US6.2 | U2 | u2-card-issuance | Transversal: base na U1, métricas do cadastro e da reconciliação na U3 |
| US6.3 | U3 | u3-cardholder-registration | Procedimento de DLQ e reconciliação no README |
| US7.1 | U1 | u1-walking-skeleton | Transversal: testes de 401 e 403 por escopo em cada unidade |
| US7.2 | U2 | u2-card-issuance | Transversal: validação da chave de fingerprint e não vazamento no cadastro na U3 |
| US8.1 | U6 | u6-delivery-docs | Transversal: cada unidade publica o OpenAPI do que expõe; a U6 confere |
| US8.2 | U6 | u6-delivery-docs | Transversal: cada unidade atualiza o README; a U6 consolida |
| US8.3 | U6 | u6-delivery-docs | Transversal: cada unidade atualiza o Postman; a U6 revisa |
| US8.4 | U6 | u6-delivery-docs | Transversal: cada unidade registra os seus ADRs; a U6 confere |

## Histórias transversais

| História | Unidade principal | Também em |
|---|---|---|
| US5.3 (histórico) | U3 | U2 (cartão), U4 (produto) |
| US6.2 (métricas) | U2 | U1 (base), U3 (cadastro e reconciliação) |
| US7.1 (autenticação) | U1 | U2, U3, U4, U5 (testes de escopo) |
| US7.2 (proteção de dados) | U2 | U3 (chave de fingerprint, não vazamento) |
| US8.1 a US8.4 (documentação) | U6 | Todas as unidades (Q5) |

## Ordem das histórias dentro de cada unidade

Primeiro as garantias, cada uma com o seu teste crítico escrito antes (`team.md`); depois o restante.

- **U1:** US0.2 → US0.1 → US0.3 → US7.1.
- **U2:** US3.4 → US3.3 → US3.2 → US3.1 (TC-PAN) → US3.5 → US5.2 → US7.2 → US3.6 → US6.2.
- **U3:** US2.3 → US2.1 → US2.2 → US4.1 → US6.1 → US5.1 → US5.3 → US2.4 → US6.3.
- **U4:** US1.1 → US1.4 → US1.3 → US1.2.
- **U5:** US4.2.
- **U6:** US8.2 → US8.4 → US8.1 → US8.3.

## Verificação de cobertura

- **Histórias:** as 31 de `stories.md` estão atribuídas a exatamente uma unidade principal.
- **Unidades:** toda unidade tem pelo menos uma história.
  - U1: 4
  - U2: 9
  - U3: 9
  - U4: 4
  - U5: 1
  - U6: 4
