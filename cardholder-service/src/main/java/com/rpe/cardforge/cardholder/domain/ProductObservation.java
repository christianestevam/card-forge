package com.rpe.cardforge.cardholder.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Última observação de um produto do catálogo conhecida pelo cadastro. */
public record ProductObservation(
    UUID productId, String name, String bin, String status, Instant observedAt) {

  public ProductObservation {
    Objects.requireNonNull(productId);
    Objects.requireNonNull(bin);
    Objects.requireNonNull(status);
    Objects.requireNonNull(observedAt);
  }
}
