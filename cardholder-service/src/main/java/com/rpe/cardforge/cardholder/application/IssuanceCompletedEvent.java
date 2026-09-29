package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.FailureReason;
import com.rpe.cardforge.cardholder.domain.IssuanceStatus;
import com.rpe.cardforge.platform.events.InvalidEventException;
import java.util.UUID;

/** Payload de {@code card-issuance-completed} v1 (C5). */
public record IssuanceCompletedEvent(
    UUID issuanceRequestId, IssuanceStatus status, FailureReason failureReason, UUID cardId) {

  public static final String EVENT_TYPE = "IssuanceCompleted";
  public static final int EVENT_VERSION = 1;

  public IssuanceCompletedEvent requireConsistent() {
    boolean valid =
        issuanceRequestId != null
            && ((status == IssuanceStatus.ISSUED && cardId != null && failureReason == null)
                || (status == IssuanceStatus.FAILED && cardId == null && failureReason != null));
    if (!valid) {
      throw new InvalidEventException("IssuanceCompleted payload is inconsistent");
    }
    return this;
  }
}
