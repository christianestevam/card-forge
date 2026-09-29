package com.rpe.cardforge.card.web;

import com.rpe.cardforge.card.domain.Card;
import com.rpe.cardforge.card.domain.CardStatus;
import java.time.Instant;
import java.util.UUID;

/** Visão pública do cartão: só {@code panLastFour} (BR5.5). */
record CardResponse(
    UUID id,
    UUID cardholderId,
    UUID productId,
    UUID issuanceRequestId,
    String panLastFour,
    String expirationDate,
    CardStatus status,
    Instant createdAt,
    Instant updatedAt) {

  static CardResponse from(Card c) {
    return new CardResponse(
        c.id(),
        c.cardholderId(),
        c.productId(),
        c.issuanceRequestId(),
        c.panLastFour(),
        c.expirationDate().toString(),
        c.status(),
        c.createdAt(),
        c.updatedAt());
  }
}
