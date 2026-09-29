package com.rpe.cardforge.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductObservationTest {

  private static final Duration WINDOW = Duration.ofMinutes(5);
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private static ProductObservation observed(ProductState state, Instant at) {
    return new ProductObservation(UUID.randomUUID(), "Gold", "12345678", state, at);
  }

  @Test
  void activeWithinFiveMinutesInclusiveAuthorizes() {
    assertThat(observed(ProductState.ACTIVE, NOW).authorizesIssuanceAt(NOW, WINDOW)).isTrue();
    assertThat(observed(ProductState.ACTIVE, NOW.minus(WINDOW)).authorizesIssuanceAt(NOW, WINDOW))
        .isTrue();
  }

  @Test
  void olderThanWindowDoesNotAuthorize() {
    assertThat(
            observed(ProductState.ACTIVE, NOW.minus(WINDOW).minusMillis(1))
                .authorizesIssuanceAt(NOW, WINDOW))
        .isFalse();
  }

  @Test
  void canceledOrFutureObservationDoesNotAuthorize() {
    assertThat(observed(ProductState.CANCELED, NOW).authorizesIssuanceAt(NOW, WINDOW)).isFalse();
    assertThat(observed(ProductState.ACTIVE, NOW.plusSeconds(1)).authorizesIssuanceAt(NOW, WINDOW))
        .isFalse();
  }
}
