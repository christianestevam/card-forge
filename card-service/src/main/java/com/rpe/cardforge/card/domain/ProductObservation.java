package com.rpe.cardforge.card.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Observação do produto feita no catálogo em {@code validatedAt}. Autoriza emissão só se for
 * ACTIVE e tiver no máximo a janela de elegibilidade (5 minutos, inclusivo) (BR4.1).
 */
public record ProductObservation(
    UUID productId, String name, String bin, ProductState status, Instant validatedAt) {

  public ProductObservation {
    Objects.requireNonNull(productId);
    Objects.requireNonNull(bin);
    Objects.requireNonNull(status);
    Objects.requireNonNull(validatedAt);
  }

  public boolean authorizesIssuanceAt(Instant now, Duration window) {
    if (status != ProductState.ACTIVE) {
      return false;
    }
    Duration age = Duration.between(validatedAt, now);
    return !age.isNegative() && age.compareTo(window) <= 0;
  }
}
