package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.ProductObservation;
import java.util.Optional;
import java.util.UUID;

/** Porta do cache de observações de produto (um registro por produto, com validatedAt). */
public interface ProductCache {

  /** Lê sem renovar {@code validatedAt}. */
  Optional<ProductObservation> find(UUID productId);

  /**
   * Resolve e devolve a observação vencedora na mesma operação atômica. CANCELED é terminal e
   * prevalece mesmo quando sua consulta começou antes da observação ACTIVE armazenada. Fora desse
   * caso vence o instante mais recente; em empate, NOT_FOUND prevalece sobre ACTIVE. A observação
   * retornada venceu nesse instante, sem impedir atualizações posteriores.
   */
  ProductObservation mergeAndGet(ProductObservation observation);

  /** Redis indisponível: quem chama registra a degradação e segue para o catálogo. */
  class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
