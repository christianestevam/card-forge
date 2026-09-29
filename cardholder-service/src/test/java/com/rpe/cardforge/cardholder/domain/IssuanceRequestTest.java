package com.rpe.cardforge.cardholder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceRequestTest {

  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private static IssuanceRequest pending() {
    return IssuanceRequest.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
  }

  @Test
  void pendingAppliesIssuedOnceAndIgnoresDuplicate() {
    IssuanceRequest request = pending();
    UUID cardId = UUID.randomUUID();

    assertThat(request.applyIssued(cardId, NOW)).isEqualTo(IssuanceRequest.Outcome.APPLIED);
    assertThat(request.applyIssued(cardId, NOW.plusSeconds(1)))
        .isEqualTo(IssuanceRequest.Outcome.ALREADY_APPLIED);
    assertThat(request.status()).isEqualTo(IssuanceStatus.ISSUED);
    assertThat(request.decidedAt()).isEqualTo(NOW);
  }

  @Test
  void contradictoryResultsChangeNothing() {
    IssuanceRequest request = pending();
    UUID cardId = UUID.randomUUID();
    request.applyIssued(cardId, NOW);

    assertThat(request.applyFailed(FailureReason.PRODUCT_CANCELED, NOW))
        .isEqualTo(IssuanceRequest.Outcome.CONTRADICTORY);
    assertThat(request.applyIssued(UUID.randomUUID(), NOW))
        .isEqualTo(IssuanceRequest.Outcome.CONTRADICTORY);
    assertThat(request.status()).isEqualTo(IssuanceStatus.ISSUED);
    assertThat(request.cardId()).isEqualTo(cardId);
  }

  @Test
  void failedOutcomeIsTerminal() {
    IssuanceRequest request = pending();
    assertThat(request.applyFailed(FailureReason.PRODUCT_NOT_FOUND, NOW))
        .isEqualTo(IssuanceRequest.Outcome.APPLIED);
    assertThat(request.applyFailed(FailureReason.PRODUCT_NOT_FOUND, NOW))
        .isEqualTo(IssuanceRequest.Outcome.ALREADY_APPLIED);
    assertThat(request.applyFailed(FailureReason.PRODUCT_CANCELED, NOW))
        .isEqualTo(IssuanceRequest.Outcome.CONTRADICTORY);
  }
}
