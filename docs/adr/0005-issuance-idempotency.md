# ADR-0005: Idempotência da emissão e da aplicação do resultado

- **Status:** Accepted (2026-09-27; implementado na R1, com o desvio D4 no cadastro)

## Contexto

A SQS Standard entrega pelo menos uma vez, e o outbox pode republicar. A mesma solicitação pode chegar duas vezes, inclusive ao mesmo tempo, ou voltar depois de decidida. A regra de negócio exige no máximo um desfecho terminal por solicitação, nunca reavaliado (BR4.2), e no máximo um cartão não cancelado por portador e produto (BR4.3).

## Decisão

**Emissão (`card-service`):**
- Tabela `issuance_processing` com uma linha por `issuanceRequestId`, gravada com `INSERT … ON CONFLICT DO NOTHING` na mesma transação do cartão e do outbox.
- Solicitação já decidida: **nunca é reavaliada**. O resultado persistido é recolocado no outbox, e só então a mensagem é confirmada.
- Duas entregas simultâneas: a segunda espera o commit da primeira no `ON CONFLICT`, recebe "já decidida" e republica o mesmo resultado.
- Defesas no banco: `uk_cards_issuance_request` (um cartão por solicitação) e o índice parcial `uk_cards_active_per_cardholder_product`. Uma violação do índice parcial desfaz a transação e a decisão recomeça, terminando em `NON_CANCELED_CARD_ALREADY_EXISTS`.

**Resultado (`cardholder-service`):** idempotente por estado, com lock de linha:
- `PENDING` aplica o resultado;
- o mesmo resultado de novo não muda nada;
- resultado contraditório não muda nada e vai para a DLQ com alerta.

**Cadastro:** nesta release não há `Idempotency-Key` (desvio D4). A repetição é barrada pela unicidade do CPF (409).

## Consequências

- TC2, TC3, TC4 e TC5 cobrem reentrega, duplicata simultânea, recusa reentregue e concorrência entre solicitações. O TC3 tem prova por mutação: sem as proteções da aplicação, o resultado da segunda entrega não é republicado; sem as do banco, surgem dois cartões.
- A republicação do resultado permite reprocessar solicitações pelo procedimento manual sem risco de cartão duplicado.
- **Limitação do cadastro:** um cliente que perdeu o recibo e repete recebe 409 `cpf-already-registered`, sem o recibo original.

## Alternativas rejeitadas

- **Deduplicação pelo `MessageId` da SQS:** o outbox gera mensagens novas a cada republicação, então o `MessageId` muda para a mesma solicitação.
- **Filas FIFO com deduplicação:** a janela de 5 minutos não cobre retentativas de horas, e há limite de vazão.
- **Lock distribuído no Redis:** acrescenta uma dependência ao caminho crítico; o banco já dá atomicidade com o dado.
- **Reavaliar a elegibilidade em cada entrega:** poderia mudar um desfecho já comunicado (proibido por BR4.2).
