package com.rpe.cardforge.card.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpe.cardforge.card.domain.FailureReason;
import com.rpe.cardforge.card.domain.IssuanceDecision;
import com.rpe.cardforge.card.domain.IssuanceStatus;
import java.util.UUID;

/** Payload de {@code card-issuance-completed} v1 (C5). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuanceCompleted(
    UUID issuanceRequestId, IssuanceStatus status, FailureReason failureReason, UUID cardId) {

  public static final String EVENT_TYPE = "IssuanceCompleted";
  public static final int EVENT_VERSION = 1;

  public static IssuanceCompleted from(IssuanceDecision decision) {
    return new IssuanceCompleted(
        decision.issuanceRequestId(),
        decision.status(),
        decision.failureReason(),
        decision.cardId());
  }
}
