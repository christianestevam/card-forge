package com.rpe.cardforge.card.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Observação do produto feita no catálogo em {@code validatedAt}. Autoriza emissão só se for ACTIVE
 * e tiver no máximo a janela de elegibilidade (5 minutos, inclusivo) (BR4.1). CANCELED é terminal
 * (BR1.2): uma observação CANCELED recusa a emissão em qualquer idade.
 */
public record ProductObservation(
    UUID productId, String name, String bin, ProductState status, Instant validatedAt) {

  public ProductObservation {
    Objects.requireNonNull(productId);
    Objects.requireNonNull(status);
    Objects.requireNonNull(validatedAt);
    if (status == ProductState.ACTIVE && bin == null) {
      throw new IllegalArgumentException("An ACTIVE observation needs the product BIN");
    }
  }

  public static ProductObservation notFound(UUID productId, Instant at) {
    return new ProductObservation(productId, null, null, ProductState.NOT_FOUND, at);
  }

  public boolean authorizesIssuanceAt(Instant now, Duration window) {
    if (status != ProductState.ACTIVE) {
      return false;
    }
    Duration age = Duration.between(validatedAt, now);
    return !age.isNegative() && age.compareTo(window) <= 0;
  }

  public boolean isKnownCancellation() {
    return status == ProductState.CANCELED;
  }
}
