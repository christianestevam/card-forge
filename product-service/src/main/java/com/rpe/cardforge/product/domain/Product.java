package com.rpe.cardforge.product.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Produto do catálogo. O BIN é imutável; todo produto nasce ACTIVE. */
public final class Product {

  private final UUID id;
  private final String name;
  private final String description;
  private final Bin bin;
  private final ProductStatus status;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Long version;

  public Product(
      UUID id,
      String name,
      String description,
      Bin bin,
      ProductStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = Objects.requireNonNull(id);
    this.name = Objects.requireNonNull(name);
    this.description = description;
    this.bin = Objects.requireNonNull(bin);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.version = version;
  }

  public static Product create(UUID id, String name, String description, Bin bin, Instant now) {
    return new Product(id, name, description, bin, ProductStatus.ACTIVE, now, now, null);
  }

  /**
   * Atualiza só nome e descrição (o BIN é imutável, BR1.1).
   *
   * @throws ProductCanceledException se o produto estiver CANCELED
   */
  public Product describe(String newName, String newDescription, Instant now) {
    if (status == ProductStatus.CANCELED) {
      throw new ProductCanceledException();
    }
    if (newName.equals(name) && java.util.Objects.equals(newDescription, description)) {
      return this;
    }
    return new Product(id, newName, newDescription, bin, status, createdAt, now, version);
  }

  /**
   * ACTIVE -> CANCELED (BR1.2). Cancelar um produto já cancelado devolve o próprio produto, sem
   * mudança (contrato C1, idempotente).
   */
  public Product cancel(Instant now) {
    if (status == ProductStatus.CANCELED) {
      return this;
    }
    return new Product(id, name, description, bin, ProductStatus.CANCELED, createdAt, now, version);
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public Bin bin() {
    return bin;
  }

  public ProductStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Long version() {
    return version;
  }
}
