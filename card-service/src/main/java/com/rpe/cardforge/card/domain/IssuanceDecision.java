package com.rpe.cardforge.card.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Desfecho terminal de uma solicitação de emissão; uma vez gravado, nunca é reavaliado. */
public record IssuanceDecision(
    UUID issuanceRequestId,
    IssuanceStatus status,
    UUID cardId,
    FailureReason failureReason,
    Instant processedAt) {

  public IssuanceDecision {
    Objects.requireNonNull(issuanceRequestId);
    Objects.requireNonNull(status);
    Objects.requireNonNull(processedAt);
    if (status == IssuanceStatus.ISSUED && (cardId == null || failureReason != null)) {
      throw new IllegalArgumentException("ISSUED requires a cardId and no failure reason");
    }
    if (status == IssuanceStatus.FAILED && (cardId != null || failureReason == null)) {
      throw new IllegalArgumentException("FAILED requires a failure reason and no cardId");
    }
  }

  public static IssuanceDecision issued(UUID issuanceRequestId, UUID cardId, Instant now) {
    return new IssuanceDecision(issuanceRequestId, IssuanceStatus.ISSUED, cardId, null, now);
  }

  public static IssuanceDecision failed(
      UUID issuanceRequestId, FailureReason reason, Instant now) {
    return new IssuanceDecision(issuanceRequestId, IssuanceStatus.FAILED, null, reason, now);
  }
}
