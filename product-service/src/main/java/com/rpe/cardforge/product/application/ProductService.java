package com.rpe.cardforge.product.application;

import com.rpe.cardforge.product.domain.Bin;
import com.rpe.cardforge.product.domain.Product;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do catálogo (caminho fino da R1: criar e consultar). */
@Service
public class ProductService {

  private final ProductRepository repository;
  private final Clock clock;

  public ProductService(ProductRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Product create(String name, String description, String bin) {
    Product product =
        Product.create(UUID.randomUUID(), name, description, new Bin(bin), clock.instant());
    return repository.insert(product);
  }

  /**
   * Atualiza nome e descrição de um produto ACTIVE. Campos ausentes mantêm o valor atual.
   *
   * @param name novo nome, ou vazio para manter
   * @param description nova descrição (pode ser nula para limpar), ou vazio para manter
   */
  @Transactional
  public Product update(UUID id, Optional<String> name, Optional<Optional<String>> description) {
    Product current =
        repository.findByIdForUpdate(id).orElseThrow(() -> new ProductNotFoundException(id));
    // Optional.map não serve aqui: descrição presente e nula significa limpar o campo.
    String newDescription =
        description.isPresent() ? description.get().orElse(null) : current.description();
    Product updated =
        current.describe(name.orElse(current.name()), newDescription, clock.instant());
    return updated == current ? current : repository.update(updated);
  }

  /** Cancela o produto; novas emissões param em até 5 minutos (BR1.3). */
  @Transactional
  public Product cancel(UUID id) {
    Product current =
        repository.findByIdForUpdate(id).orElseThrow(() -> new ProductNotFoundException(id));
    Product canceled = current.cancel(clock.instant());
    return canceled == current ? current : repository.update(canceled);
  }

  @Transactional(readOnly = true)
  public ProductPage list(int page, int size) {
    return new ProductPage(repository.findPage(page, size), page, size, repository.count());
  }

  public record ProductPage(List<Product> content, int page, int size, long totalElements) {}

  @Transactional(readOnly = true)
  public Product get(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }
}
