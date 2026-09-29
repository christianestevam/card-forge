package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.ProductObservation;
import java.util.Optional;
import java.util.UUID;

public interface ProductObservationRepository {

  Optional<ProductObservation> find(UUID productId);

  /** Grava a observação só se for mais recente que a guardada (upsert condicional). */
  void saveIfNewer(ProductObservation observation);
}
