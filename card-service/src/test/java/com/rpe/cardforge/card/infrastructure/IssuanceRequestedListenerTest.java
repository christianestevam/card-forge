package com.rpe.cardforge.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rpe.cardforge.card.application.IssuanceProcessor;
import com.rpe.cardforge.card.application.IssuanceProperties;
import com.rpe.cardforge.card.application.RetryBackoff;
import com.rpe.cardforge.card.application.TransientIssuanceException;
import com.rpe.cardforge.platform.events.DeadLetterPublisher;
import com.rpe.cardforge.platform.events.EventReader;
import io.awspring.cloud.sqs.listener.Visibility;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Falhas técnicas, inclusive banco ou transação indisponíveis, seguem o backoff do consumidor em
 * vez da visibilidade padrão da fila.
 */
class IssuanceRequestedListenerTest {

  private final IssuanceProcessor processor = mock(IssuanceProcessor.class);
  private final DeadLetterPublisher deadLetters = mock(DeadLetterPublisher.class);
  private final Visibility visibility = mock(Visibility.class);
  private final IssuanceRequestedListener listener =
      new IssuanceRequestedListener(
          processor,
          new EventReader(new ObjectMapper().registerModule(new JavaTimeModule())),
          deadLetters,
          new RetryBackoff(30, 300, () -> 0.5),
          new IssuanceProperties(
              Duration.ofMinutes(5),
              Duration.ofSeconds(30),
              Duration.ofMinutes(5),
              20,
              "card-issuance-requested",
              "card-issuance-requested-dlq",
              "card-issuance-completed"));

  static Stream<RuntimeException> technicalFailures() {
    return Stream.of(
        new TransientIssuanceException("catalog down"),
        new DataAccessResourceFailureException("database down"),
        new CannotCreateTransactionException("no connection"));
  }

  @ParameterizedTest
  @MethodSource("technicalFailures")
  void technicalFailuresAreNotAcknowledgedAndUseTheBackoff(RuntimeException failure) {
    doThrow(failure).when(processor).process(any());

    assertThatThrownBy(() -> listener.onMessage(validBody(), visibility, "1", "corr"))
        .isSameAs(failure);

    verify(visibility).changeTo(30);
    verifyNoInteractions(deadLetters);
  }

  private static String validBody() {
    return """
        {"eventId":"%s","eventType":"IssuanceRequested","eventVersion":1,
         "occurredAt":"2026-09-29T12:00:00Z","correlationId":"c",
         "payload":{"issuanceRequestId":"%s","cardholderId":"%s","productId":"%s"}}"""
        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  }
}
