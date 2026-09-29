package com.rpe.cardforge.product.web;

import com.rpe.cardforge.product.domain.Product;
import com.rpe.cardforge.product.domain.ProductStatus;
import java.time.Instant;
import java.util.UUID;

record ProductResponse(
    UUID id,
    String name,
    String description,
    String bin,
    ProductStatus status,
    Instant createdAt,
    Instant updatedAt) {

  static ProductResponse from(Product p) {
    return new ProductResponse(
        p.id(),
        p.name(),
        p.description(),
        p.bin().value(),
        p.status(),
        p.createdAt(),
        p.updatedAt());
  }
}
