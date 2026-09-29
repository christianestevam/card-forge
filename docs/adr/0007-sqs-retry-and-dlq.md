# ADR-0007: Retry pela SQS com backoff e DLQ

- **Status:** Accepted (2026-09-27; implementado na R1)

## Contexto

Falhas técnicas (catálogo fora, timeout, Redis e catálogo juntos, espaço de PAN) precisam de retentativa sem bloquear outras mensagens e sem perda. Falhas de negócio não podem ser retentadas (BR4.4). Mensagens inválidas não podem ficar em ciclo.

## Decisão

- **Filas:** SQS Standard `card-issuance-requested` e `card-issuance-completed`, cada uma com DLQ (`-dlq`) e redrive policy. A DLQ retém 14 dias e a fila de origem 4, porque a mensagem mantém o timestamp original ao ir para a DLQ.
- **Uma tentativa HTTP por processamento;** a retentativa é da SQS.
- **Quatro classes de falha no `card-service`:**
  - **negócio:** grava `FAILED` e confirma, sem retry;
  - **técnica:** não confirma e adia a próxima entrega com `ChangeMessageVisibility`: 30 s na primeira, dobrando a cada recebimento (`ApproximateReceiveCount`), jitter de ±20% e **teto rígido de 5 min aplicado depois do jitter**;
  - **configuração:** alerta (`ALERT configuration`) e o mesmo backoff, nunca mascarada como produto inexistente;
  - **mensagem inválida ou versão desconhecida:** envio explícito e imediato para a DLQ, com alerta.
- **Redrive:** `maxReceiveCount` de 29 em `card-issuance-requested`, o que dá um orçamento nominal de cerca de 2 h. Em `card-issuance-completed` é 5, e resultados contraditórios, desconhecidos ou inválidos vão direto para a DLQ.
- **ACK** só quando o listener termina sem exceção, depois do commit.

## Consequências

- Quando a dependência volta, as emissões retidas retomam em no máximo 5 minutos (NFR8). Testes com 5xx, timeout e catálogo vencido cobrem a volta.
- O jitter espalha as retentativas quando muitas mensagens falham juntas.
- A mensagem que esgota o orçamento vai para a DLQ e a solicitação fica `PENDING`. Sem reconciliação automática (desvio D12), a recuperação segue o procedimento manual do README.
- O Spring Cloud AWS loga o stack trace a cada falha técnica: é ruído aceito.

## Alternativas rejeitadas

- **Retry em memória com Resilience4j `Retry`:** prende a thread do consumidor e perde o estado se o processo cair.
- **Backoff fixo pelo visibility timeout da fila:** não cresce com as falhas, então martela a dependência fora ou demora demais na primeira retentativa.
- **Fila de retry separada com delay:** o `DelaySeconds` da SQS tem teto de 15 min e exige mais filas e roteamento.
- **Retentar falhas de negócio:** proibido por BR4.4. A mutação desse comportamento fez o teste de "recusa sem retry" falhar.
