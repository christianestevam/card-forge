package com.rpe.cardforge.product.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
class ProductJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false, length = 8, updatable = false)
  private String bin;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected ProductJpaEntity() {}

  ProductJpaEntity(
      UUID id,
      String name,
      String description,
      String bin,
      String status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.bin = bin;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  void apply(String name, String description, String status, Instant updatedAt) {
    this.name = name;
    this.description = description;
    this.status = status;
    this.updatedAt = updatedAt;
  }

  UUID getId() {
    return id;
  }

  String getName() {
    return name;
  }

  String getDescription() {
    return description;
  }

  String getBin() {
    return bin;
  }

  String getStatus() {
    return status;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  Long getVersion() {
    return version;
  }
}
