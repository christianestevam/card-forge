# ADR-0004: Outbox transacional com relay interno

- **Status:** Accepted (2026-09-27; implementado na R1)

## Contexto

O cadastro precisa ser aceito mesmo com a SQS fora (BR3.5), e nenhuma solicitação aceita pode se perder entre o banco e a mensageria (NFR6). A emissão tem a mesma exigência para o resultado. Publicar na SQS dentro da transação de negócio é proibido: a transação pode fazer rollback depois do envio, ou o envio falhar depois do commit (dual write).

## Decisão

- Cada serviço que publica (`cardholder-service` e `card-service`) tem a tabela `outbox_events`, gravada **na mesma transação** do dado de negócio. O `OutboxWriter` exige transação ativa (`Propagation.MANDATORY`).
- Um relay agendado dentro do próprio serviço (a cada 500 ms) seleciona até 10 eventos pendentes com `FOR UPDATE SKIP LOCKED` e envia por `SendMessageBatch` com timeout de 5 s, **mantendo o lock durante o envio**. Só os eventos aceitos individualmente recebem `sent_at`.
- Envelope: `eventId` (identidade da linha, muda a cada publicação), `eventType`, `eventVersion`, `occurredAt`, `correlationId` e `payload` mínimo, sem CPF, data de nascimento ou PAN. O `correlationId` também vai como atributo SQS.
- Métricas: `cardforge_outbox_pending` e `cardforge_outbox_oldest_age_seconds`.
- O código fica no `cardforge-platform` e é ativado por `cardforge.outbox.enabled=true`.

## Consequências

- O cadastro responde 202 com a SQS fora. O evento sai quando ela volta (TC1, com prova por mutação).
- Entrega **pelo menos uma vez**: uma resposta perdida do `SendMessageBatch` republica o evento. Os consumidores são idempotentes ([ADR-0005](0005-issuance-idempotency.md)).
- Várias instâncias podem rodar o relay em paralelo sem enviar o mesmo lote (`SKIP LOCKED`).
- Manter o lock durante o envio segura a linha por até 5 s, mas evita dois envios do mesmo evento por instâncias diferentes.
- O banco é a fila de saída: linhas enviadas não são limpas nesta release (débito). O `traceparent` não é gravado, então o trace não atravessa a fila (desvio D11).

## Alternativas rejeitadas

- **Publicação direta depois do commit** (`afterCommit`): perde o evento se o processo cair entre o commit e o envio.
- **CDC com Debezium:** exige Kafka Connect e mais infraestrutura, desproporcional para uma pessoa e dois tipos de evento.
- **Marcar como enviado antes do envio:** perde eventos quando a SQS falha. A mutação desse comportamento fez o TC1 falhar.
- **Worker externo separado:** mais um processo para implantar e monitorar, sem ganho sobre o agendador interno com `SKIP LOCKED`.

## Segurança

O payload do evento é mínimo e sem dados pessoais. O relay loga só destino e quantidade, nunca o payload.
