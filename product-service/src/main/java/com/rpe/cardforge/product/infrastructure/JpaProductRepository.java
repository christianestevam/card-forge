package com.rpe.cardforge.product.infrastructure;

import com.rpe.cardforge.platform.persistence.UniqueConstraints;
import com.rpe.cardforge.product.application.BinAlreadyRegisteredException;
import com.rpe.cardforge.product.application.ProductRepository;
import com.rpe.cardforge.product.domain.Bin;
import com.rpe.cardforge.product.domain.Product;
import com.rpe.cardforge.product.domain.ProductStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaProductRepository implements ProductRepository {

  static final String UK_BIN = "uk_products_bin";

  private final SpringDataProductRepository jpa;

  JpaProductRepository(SpringDataProductRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Product insert(Product product) {
    try {
      return toDomain(jpa.saveAndFlush(toEntity(product)));
    } catch (DataIntegrityViolationException e) {
      if (UniqueConstraints.isViolation(e, UK_BIN)) {
        throw new BinAlreadyRegisteredException();
      }
      throw e;
    }
  }

  @Override
  public Optional<Product> findById(UUID id) {
    return jpa.findById(id).map(JpaProductRepository::toDomain);
  }

  @Override
  public Optional<Product> findByIdForUpdate(UUID id) {
    return jpa.findByIdForUpdate(id).map(JpaProductRepository::toDomain);
  }

  @Override
  public Product update(Product product) {
    ProductJpaEntity entity =
        jpa.findById(product.id())
            .orElseThrow(() -> new IllegalStateException("Missing product " + product.id()));
    entity.apply(
        product.name(), product.description(), product.status().name(), product.updatedAt());
    return toDomain(jpa.saveAndFlush(entity));
  }

  private static ProductJpaEntity toEntity(Product p) {
    return new ProductJpaEntity(
        p.id(),
        p.name(),
        p.description(),
        p.bin().value(),
        p.status().name(),
        p.createdAt(),
        p.updatedAt(),
        p.version());
  }

  private static Product toDomain(ProductJpaEntity e) {
    return new Product(
        e.getId(),
        e.getName(),
        e.getDescription(),
        new Bin(e.getBin()),
        ProductStatus.valueOf(e.getStatus()),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion());
  }
}
