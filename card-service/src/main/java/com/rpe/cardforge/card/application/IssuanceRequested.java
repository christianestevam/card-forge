package com.rpe.cardforge.card.application;

import com.rpe.cardforge.platform.events.InvalidEventException;
import java.util.UUID;

/** Payload de {@code card-issuance-requested} v1 (C4). */
public record IssuanceRequested(UUID issuanceRequestId, UUID cardholderId, UUID productId) {

  public static final String EVENT_TYPE = "IssuanceRequested";
  public static final int EVENT_VERSION = 1;

  public IssuanceRequested requireComplete() {
    if (issuanceRequestId == null || cardholderId == null || productId == null) {
      throw new InvalidEventException("IssuanceRequested payload is missing required fields");
    }
    return this;
  }
}
