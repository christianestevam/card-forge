# Avaliação: histórias de usuário são necessárias?

## Decisão

**Execute.**

## Justificativa

- **Lógica de negócio complexa:** 27 regras de negócio, máquinas de estado de produto, portador e cartão, garantias de desfecho único, idempotência e comportamento definido sob falha (`requirements.md`, FR1 a FR8). Os critérios de aceite em Given/When/Then tornam essas garantias testáveis por comportamento, que é o que a postura de testes do time exige (`team-practices.md`, Testing Posture: o teste crítico de cada garantia é escrito antes).
- **Mais de um público:** o gateway de onboarding (cliente técnico, em nome de varejistas e consumidores), a unidade de Processamento (dona e operadora) e a Segurança/Compliance (informada sobre os controles). Cada um percebe o sistema de um jeito diferente.
- **Coordenação entre times:** o time do gateway consome os contratos; o Processamento opera DLQ e reconciliação.

## Fatores considerados

| Fator | Observação |
|---|---|
| Tipo de projeto | Greenfield, produto novo com três serviços |
| Interface com usuário | Nenhuma interface gráfica: só APIs. As "pessoas usuárias" são sistemas clientes e operadores |
| Sinais de complexidade | Concorrência, mensageria com reentrega, cache com janela de validade, dados de pagamento |

## Onde as histórias agregam mais valor

- Transformar as garantias de integridade (desfecho único, nenhuma perda, sem duplicidade) em critérios de aceite verificáveis, ligados aos dez testes críticos.
- Explicitar o que o gateway vê em cada caso de falha (códigos HTTP, recibo, replay).
- Dar ao Processamento histórias de operação (retenção, reconciliação, alertas) que os requisitos descrevem só pelo lado técnico.
- Tornar visíveis para a Delivery Planning os tamanhos relativos e a composição do esqueleto, que concentram o risco de prazo.
