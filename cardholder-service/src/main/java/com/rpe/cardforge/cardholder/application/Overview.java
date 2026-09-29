package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.application.CardDirectory.CardView;
import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import java.time.Instant;

/**
 * Consulta consolidada: separa o estado de negócio ({@code issuance.status}) da completude de cada
 * parte ({@code availability}).
 */
public record Overview(
    Cardholder cardholder, IssuanceRequest issuance, CardPart card, ProductPart product) {

  public enum CardAvailability {
    NOT_APPLICABLE,
    AVAILABLE,
    UNAVAILABLE
  }

  public enum ProductAvailability {
    CURRENT,
    STALE,
    UNAVAILABLE
  }

  public record CardPart(CardAvailability availability, CardView data) {}

  public record ProductPart(
      ProductAvailability availability, Instant observedAt, ProductCatalog.Found data) {}
}
