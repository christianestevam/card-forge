package com.rpe.cardforge.product.application;

import com.rpe.cardforge.product.domain.Product;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência do catálogo. */
public interface ProductRepository {

  /**
   * Insere um produto novo.
   *
   * @throws BinAlreadyRegisteredException se o BIN já existe (constraint no banco)
   */
  Product insert(Product product);

  Optional<Product> findById(UUID id);

  /** Lê com lock de escrita, para aplicar mudanças concorrentes em série. */
  Optional<Product> findByIdForUpdate(UUID id);

  /** Grava nome, descrição, status e {@code updatedAt}; o BIN nunca muda. */
  Product update(Product product);
}
