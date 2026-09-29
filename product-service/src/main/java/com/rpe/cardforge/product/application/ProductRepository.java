package com.rpe.cardforge.product.application;

import com.rpe.cardforge.product.domain.Product;
import java.util.List;
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

  /** Página de produtos, do mais recente para o mais antigo. */
  List<Product> findPage(int page, int size);

  long count();

  /** Lê com lock de escrita, para aplicar mudanças concorrentes em série. */
  Optional<Product> findByIdForUpdate(UUID id);

  /** Grava nome, descrição, status e {@code updatedAt}; o BIN nunca muda. */
  Product update(Product product);
}
