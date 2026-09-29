package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.application.IssuanceConfigurationException;
import com.rpe.cardforge.card.application.IssuanceProcessor;
import com.rpe.cardforge.card.application.IssuanceProperties;
import com.rpe.cardforge.card.application.IssuanceRequested;
import com.rpe.cardforge.card.application.RetryBackoff;
import com.rpe.cardforge.card.application.TransientIssuanceException;
import com.rpe.cardforge.platform.correlation.CorrelationId;
import com.rpe.cardforge.platform.events.DeadLetterPublisher;
import com.rpe.cardforge.platform.events.EventReader;
import com.rpe.cardforge.platform.events.InvalidEventException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code card-issuance-requested}. A mensagem só é confirmada quando o método termina
 * sem exceção, isto é, depois do commit do resultado.
 *
 * <ul>
 *   <li>Negócio (produto inexistente ou cancelado): FAILED gravado, confirma, sem retry.
 *   <li>Técnica ou configuração: não confirma e adia a próxima entrega por {@code
 *       ChangeMessageVisibility} (backoff exponencial com jitter e teto).
 *   <li>Mensagem inválida ou versão desconhecida: DLQ explícita e alerta.
 * </ul>
 */
@Component
class IssuanceRequestedListener {

  private static final Logger log = LoggerFactory.getLogger(IssuanceRequestedListener.class);

  private final IssuanceProcessor processor;
  private final EventReader reader;
  private final DeadLetterPublisher deadLetters;
  private final RetryBackoff backoff;
  private final IssuanceProperties properties;

  IssuanceRequestedListener(
      IssuanceProcessor processor,
      EventReader reader,
      DeadLetterPublisher deadLetters,
      RetryBackoff backoff,
      IssuanceProperties properties) {
    this.processor = processor;
    this.reader = reader;
    this.deadLetters = deadLetters;
    this.backoff = backoff;
    this.properties = properties;
  }

  @SqsListener(value = "${cardforge.issuance.requested-queue}", id = "issuance-requested")
  void onMessage(
      String body,
      Visibility visibility,
      @Header(
              name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
              required = false)
          String receiveCount,
      @Header(name = CorrelationId.MESSAGE_ATTRIBUTE, required = false) String correlationId) {
    try (CorrelationId.Scope ignored = CorrelationId.open(correlationId)) {
      IssuanceRequested request;
      try {
        request =
            reader
                .read(
                    body,
                    IssuanceRequested.EVENT_TYPE,
                    IssuanceRequested.EVENT_VERSION,
                    IssuanceRequested.class)
                .requireComplete();
      } catch (InvalidEventException e) {
        log.error("ALERT invalid issuance request message sent to DLQ: {}", e.getMessage());
        deadLetters.send(properties.requestedDeadLetterQueue(), body, e.getMessage());
        return;
      }

      try {
        processor.process(request);
      } catch (TransientIssuanceException | IssuanceConfigurationException e) {
        int attempt = parseReceiveCount(receiveCount);
        int delay = backoff.delaySeconds(attempt);
        log.warn(
            "Issuance of request {} postponed {}s (receive #{}): {}",
            request.issuanceRequestId(),
            delay,
            attempt,
            e.getMessage());
        visibility.changeTo(delay);
        throw e;
      }
    }
  }

  private static int parseReceiveCount(String value) {
    try {
      return value == null ? 1 : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 1;
    }
  }
}
