package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.CardholderProperties;
import com.rpe.cardforge.cardholder.application.IssuanceCompletedEvent;
import com.rpe.cardforge.cardholder.application.IssuanceResultService;
import com.rpe.cardforge.cardholder.application.UnprocessableResultException;
import com.rpe.cardforge.platform.correlation.CorrelationId;
import com.rpe.cardforge.platform.events.DeadLetterPublisher;
import com.rpe.cardforge.platform.events.EventReader;
import com.rpe.cardforge.platform.events.InvalidEventException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code card-issuance-completed}: confirma só depois de persistir. Resultado
 * contraditório, solicitação desconhecida ou mensagem inválida gera alerta e vai de forma
 * explícita e imediata para a DLQ, preservando a mensagem original.
 */
@Component
class IssuanceCompletedListener {

  private static final Logger log = LoggerFactory.getLogger(IssuanceCompletedListener.class);

  private final IssuanceResultService results;
  private final EventReader reader;
  private final DeadLetterPublisher deadLetters;
  private final CardholderProperties properties;

  IssuanceCompletedListener(
      IssuanceResultService results,
      EventReader reader,
      DeadLetterPublisher deadLetters,
      CardholderProperties properties) {
    this.results = results;
    this.reader = reader;
    this.deadLetters = deadLetters;
    this.properties = properties;
  }

  @SqsListener(value = "${cardforge.issuance.completed-queue}", id = "issuance-completed")
  void onMessage(
      String body,
      @Header(name = CorrelationId.MESSAGE_ATTRIBUTE, required = false) String correlationId) {
    try (CorrelationId.Scope ignored = CorrelationId.open(correlationId)) {
      try {
        IssuanceCompletedEvent event =
            reader
                .read(
                    body,
                    IssuanceCompletedEvent.EVENT_TYPE,
                    IssuanceCompletedEvent.EVENT_VERSION,
                    IssuanceCompletedEvent.class)
                .requireConsistent();
        results.apply(event);
      } catch (InvalidEventException | UnprocessableResultException e) {
        log.error("ALERT issuance result sent to DLQ: {}", e.getMessage());
        deadLetters.send(properties.completedDeadLetterQueue(), body, e.getMessage());
      }
    }
  }
}
