package com.rpe.cardforge.cardholder.application;

import java.util.UUID;

/** Porta do catálogo de produtos (product-service), com orçamento curto e sem retentativa. */
public interface ProductCatalog {

  /**
   * @throws CatalogUnavailableException em timeout, 5xx ou conexão recusada
   * @throws CatalogMisconfiguredException em 401, 403 ou resposta fora do contrato
   */
  Lookup lookup(UUID productId);

  sealed interface Lookup permits Found, NotFound {}

  record Found(UUID id, String name, String bin, String status) implements Lookup {
    public boolean isCanceled() {
      return "CANCELED".equals(status);
    }
  }

  record NotFound(UUID id) implements Lookup {}

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
