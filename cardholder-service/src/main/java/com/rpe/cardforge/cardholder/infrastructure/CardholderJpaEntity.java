package com.rpe.cardforge.cardholder.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cardholders")
class CardholderJpaEntity {

  @Id UUID id;

  @Column(nullable = false, length = 11, updatable = false)
  String cpf;

  @Column(name = "full_name", nullable = false, length = 120)
  String fullName;

  @Column(name = "birth_date", nullable = false)
  LocalDate birthDate;

  @Column(name = "product_id", nullable = false)
  UUID productId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version Long version;

  protected CardholderJpaEntity() {}

  @Override
  public String toString() {
    return "CardholderJpaEntity[id=" + id + "]";
  }
}
