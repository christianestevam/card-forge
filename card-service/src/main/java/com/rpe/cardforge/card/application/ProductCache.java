package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.ProductObservation;
import java.util.Optional;
import java.util.UUID;

/** Porta do cache de observações de produto (um registro por produto, com validatedAt). */
public interface ProductCache {

  /** Lê sem renovar {@code validatedAt}. */
  Optional<ProductObservation> find(UUID productId);

  void save(ProductObservation observation);

  void evict(UUID productId);

  /** Redis indisponível: quem chama registra a degradação e segue para o catálogo. */
  class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
