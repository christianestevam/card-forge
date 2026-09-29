package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.ProductObservation;
import java.util.Optional;
import java.util.UUID;

/** Porta do cache de observações de produto (um registro por produto, com validatedAt). */
public interface ProductCache {

  /** Lê sem renovar {@code validatedAt}. */
  Optional<ProductObservation> find(UUID productId);

  /**
   * Grava a observação de forma atômica, só se ela for a mais recente. Uma observação CANCELED
   * (lápide) nunca é substituída; CANCELED ou NOT_FOUND nunca são desfeitos por uma resposta ACTIVE
   * mais antiga.
   *
   * @return false se a observação foi descartada por já existir outra mais recente ou definitiva
   */
  boolean save(ProductObservation observation);

  /** Redis indisponível: quem chama registra a degradação e segue para o catálogo. */
  class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
