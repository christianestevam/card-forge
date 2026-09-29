package com.rpe.cardforge.cardholder.application;

import java.util.UUID;

/** Payload de {@code card-issuance-requested} v1 (C4): sem CPF nem data de nascimento. */
public record IssuanceRequestedEvent(UUID issuanceRequestId, UUID cardholderId, UUID productId) {

  public static final String EVENT_TYPE = "IssuanceRequested";
  public static final int EVENT_VERSION = 1;
}
