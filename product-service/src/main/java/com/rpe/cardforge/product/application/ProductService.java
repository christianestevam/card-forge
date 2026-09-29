package com.rpe.cardforge.product.application;

import com.rpe.cardforge.product.domain.Bin;
import com.rpe.cardforge.product.domain.Product;
import java.time.Clock;
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

  /** Cancela o produto; novas emissões param em até 5 minutos (BR1.3). */
  @Transactional
  public Product cancel(UUID id) {
    Product current =
        repository.findByIdForUpdate(id).orElseThrow(() -> new ProductNotFoundException(id));
    Product canceled = current.cancel(clock.instant());
    return canceled == current ? current : repository.update(canceled);
  }

  @Transactional(readOnly = true)
  public Product get(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
  }
}
