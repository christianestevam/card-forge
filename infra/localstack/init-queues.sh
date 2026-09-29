#!/bin/bash
# Cria as filas SQS Standard, cada uma com DLQ e redrive policy.
# A retenção da DLQ (14 dias) é maior que a da fila de origem (4 dias): em filas Standard a
# mensagem mantém o timestamp original ao ir para a DLQ.
set -euo pipefail

create_with_dlq() {
  local queue="$1" max_receive="$2"
  local dlq_url dlq_arn
  dlq_url=$(awslocal sqs create-queue --queue-name "${queue}-dlq" \
    --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)
  awslocal sqs create-queue --queue-name "$queue" --attributes "{
    \"MessageRetentionPeriod\": \"345600\",
    \"VisibilityTimeout\": \"60\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"${max_receive}\\\"}\"
  }" > /dev/null
  echo "created ${queue} (+ ${queue}-dlq, maxReceiveCount=${max_receive})"
}

# ~29 recebimentos: orçamento nominal de ~2 h com backoff de 30 s dobrando até o teto de 5 min.
create_with_dlq card-issuance-requested 29
create_with_dlq card-issuance-completed 5
