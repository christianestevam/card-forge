#!/bin/sh
set -eu
for queue in card-issuance-requested card-issuance-completed; do
 dlq_url=$(awslocal sqs create-queue --queue-name "$queue-dlq" --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
 arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --query Attributes.QueueArn --output text)
 awslocal sqs create-queue --queue-name "$queue" --attributes "{\"VisibilityTimeout\":\"60\",\"MessageRetentionPeriod\":\"345600\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$arn\\\",\\\"maxReceiveCount\\\":\\\"29\\\"}\"}"
done
touch /tmp/cardforge-queues-ready
