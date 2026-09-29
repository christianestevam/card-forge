package com.rpe.cardforge.card.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpe.cardforge.card.application.ProductDetails;
import com.rpe.cardforge.card.domain.Card;
import com.rpe.cardforge.card.domain.CardStatus;
import com.rpe.cardforge.card.domain.ProductState;
import java.time.Instant;
import java.util.UUID;

/** Visão pública do cartão: só {@code panLastFour} (BR5.5); {@code product} na consulta por ID. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CardResponse(
    UUID id,
    UUID cardholderId,
    UUID productId,
    UUID issuanceRequestId,
    String panLastFour,
    String expirationDate,
    CardStatus status,
    Instant createdAt,
    Instant updatedAt,
    ProductSection product) {

  static CardResponse from(Card c) {
    return from(c, null);
  }

  static CardResponse from(Card c, ProductSection product) {
    return new CardResponse(
        c.id(),
        c.cardholderId(),
        c.productId(),
        c.issuanceRequestId(),
        c.panLastFour(),
        c.expirationDate().toString(),
        c.status(),
        c.createdAt(),
        c.updatedAt(),
        product);
  }

  /**
   * Produto do cartão: CURRENT (observação de até 5 minutos), STALE (mais antiga, com {@code
   * observedAt}) ou UNAVAILABLE (sem cache nem catálogo).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ProductSection(
      ProductDetails.Availability availability, Instant observedAt, ProductData data) {

    static ProductSection from(ProductDetails.ProductView view) {
      ProductData data =
          view.availability() == ProductDetails.Availability.UNAVAILABLE
              ? null
              : new ProductData(view.id(), view.name(), view.bin(), view.status());
      return new ProductSection(view.availability(), view.observedAt(), data);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ProductData(UUID id, String name, String bin, ProductState status) {}
}
