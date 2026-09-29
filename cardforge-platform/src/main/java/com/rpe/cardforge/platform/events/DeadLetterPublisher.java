package com.rpe.cardforge.platform.events;

import com.rpe.cardforge.platform.correlation.CorrelationId;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Envia de forma explícita e imediata para a DLQ uma mensagem que não pode ser processada,
 * preservando o corpo original. Só depois disso a mensagem original pode ser confirmada.
 */
public class DeadLetterPublisher {

  private final SqsAsyncClient sqs;
  private final Duration timeout;

  public DeadLetterPublisher(SqsAsyncClient sqs, Duration timeout) {
    this.sqs = sqs;
    this.timeout = timeout;
  }

  /**
   * @throws IllegalStateException se o envio falhar; a mensagem original não deve ser confirmada
   */
  public void send(String deadLetterQueue, String originalBody, String reason) {
    try {
      String url =
          sqs.getQueueUrl(
                  b ->
                      b.queueName(deadLetterQueue)
                          .overrideConfiguration(o -> o.apiCallTimeout(timeout)))
              .get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS)
              .queueUrl();
      sqs.sendMessage(
              b ->
                  b.queueUrl(url)
                      .messageBody(originalBody)
                      .messageAttributes(
                          Map.of(
                              CorrelationId.MESSAGE_ATTRIBUTE, attribute(CorrelationId.current()),
                              "deadLetterReason", attribute(reason)))
                      .overrideConfiguration(o -> o.apiCallTimeout(timeout)))
          .get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while sending to " + deadLetterQueue, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Could not send message to " + deadLetterQueue, e);
    }
  }

  private static MessageAttributeValue attribute(String value) {
    String safe = value.length() > 256 ? value.substring(0, 256) : value;
    return MessageAttributeValue.builder().dataType("String").stringValue(safe).build();
  }
}
