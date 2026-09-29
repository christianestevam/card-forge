package com.rpe.cardforge.cardholder.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpe.cardforge.cardholder.application.CardDirectory.CardView;
import com.rpe.cardforge.cardholder.application.Overview;
import com.rpe.cardforge.cardholder.domain.FailureReason;
import com.rpe.cardforge.cardholder.domain.IssuanceStatus;
import java.time.Instant;
import java.util.UUID;

/** Consulta consolidada (C2 {@code CardholderOverview}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
record OverviewResponse(
    CardholderResponse cardholder, Issuance issuance, CardSection card, ProductSection product) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Issuance(
      UUID issuanceRequestId,
      IssuanceStatus status,
      FailureReason failureReason,
      UUID cardId,
      Instant requestedAt,
      Instant decidedAt) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record CardSection(Overview.CardAvailability availability, CardView data) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ProductSection(
      Overview.ProductAvailability availability, Instant observedAt, ProductData data) {}

  record ProductData(UUID id, String name, String bin, String status) {}

  static OverviewResponse from(Overview o) {
    var i = o.issuance();
    var product = o.product();
    return new OverviewResponse(
        CardholderResponse.from(o.cardholder()),
        new Issuance(
            i.id(), i.status(), i.failureReason(), i.cardId(), i.requestedAt(), i.decidedAt()),
        new CardSection(o.card().availability(), o.card().data()),
        new ProductSection(
            product.availability(),
            product.observedAt(),
            product.data() == null
                ? null
                : new ProductData(
                    product.data().id(),
                    product.data().name(),
                    product.data().bin(),
                    product.data().status())));
  }
}
