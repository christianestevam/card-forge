package com.rpe.cardforge.cardholder.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitação de emissão como vista pelo cliente. Nasce PENDING e recebe no máximo um desfecho
 * terminal; a aplicação do resultado é idempotente por estado (FR5.1).
 */
public final class IssuanceRequest {

  /** O que aconteceu ao aplicar um resultado. */
  public enum Outcome {
    APPLIED,
    ALREADY_APPLIED,
    CONTRADICTORY
  }

  private final UUID id;
  private final UUID cardholderId;
  private final UUID productId;
  private IssuanceStatus status;
  private FailureReason failureReason;
  private UUID cardId;
  private final Instant requestedAt;
  private Instant decidedAt;

  public IssuanceRequest(
      UUID id,
      UUID cardholderId,
      UUID productId,
      IssuanceStatus status,
      FailureReason failureReason,
      UUID cardId,
      Instant requestedAt,
      Instant decidedAt) {
    this.id = Objects.requireNonNull(id);
    this.cardholderId = Objects.requireNonNull(cardholderId);
    this.productId = Objects.requireNonNull(productId);
    this.status = Objects.requireNonNull(status);
    this.failureReason = failureReason;
    this.cardId = cardId;
    this.requestedAt = Objects.requireNonNull(requestedAt);
    this.decidedAt = decidedAt;
  }

  public static IssuanceRequest pending(UUID id, UUID cardholderId, UUID productId, Instant now) {
    return new IssuanceRequest(
        id, cardholderId, productId, IssuanceStatus.PENDING, null, null, now, null);
  }

  public Outcome applyIssued(UUID issuedCardId, Instant now) {
    Objects.requireNonNull(issuedCardId);
    if (status == IssuanceStatus.PENDING) {
      status = IssuanceStatus.ISSUED;
      cardId = issuedCardId;
      decidedAt = now;
      return Outcome.APPLIED;
    }
    return status == IssuanceStatus.ISSUED && issuedCardId.equals(cardId)
        ? Outcome.ALREADY_APPLIED
        : Outcome.CONTRADICTORY;
  }

  public Outcome applyFailed(FailureReason reason, Instant now) {
    Objects.requireNonNull(reason);
    if (status == IssuanceStatus.PENDING) {
      status = IssuanceStatus.FAILED;
      failureReason = reason;
      decidedAt = now;
      return Outcome.APPLIED;
    }
    return status == IssuanceStatus.FAILED && reason == failureReason
        ? Outcome.ALREADY_APPLIED
        : Outcome.CONTRADICTORY;
  }

  public UUID id() {
    return id;
  }

  public UUID cardholderId() {
    return cardholderId;
  }

  public UUID productId() {
    return productId;
  }

  public IssuanceStatus status() {
    return status;
  }

  public FailureReason failureReason() {
    return failureReason;
  }

  public UUID cardId() {
    return cardId;
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public Instant decidedAt() {
    return decidedAt;
  }
}
