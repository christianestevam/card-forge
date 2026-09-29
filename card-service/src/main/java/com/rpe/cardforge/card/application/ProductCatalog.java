package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.ProductState;
import java.util.UUID;

/** Porta do catálogo de produtos (product-service). Uma tentativa por chamada. */
public interface ProductCatalog {

  /**
   * @throws CatalogUnavailableException em timeout, 5xx ou conexão recusada
   * @throws CatalogMisconfiguredException em 401, 403 ou resposta fora do contrato
   */
  Lookup lookup(UUID productId);

  sealed interface Lookup permits Found, NotFound {}

  record Found(UUID productId, String name, String bin, ProductState status) implements Lookup {}

  record NotFound(UUID productId) implements Lookup {}

  class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  class CatalogMisconfiguredException extends RuntimeException {
    public CatalogMisconfiguredException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
