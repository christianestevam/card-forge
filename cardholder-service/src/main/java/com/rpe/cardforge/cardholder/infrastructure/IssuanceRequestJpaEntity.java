package com.rpe.cardforge.cardholder.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuance_requests")
class IssuanceRequestJpaEntity {

  @Id UUID id;

  @Column(name = "cardholder_id", nullable = false, updatable = false)
  UUID cardholderId;

  @Column(name = "product_id", nullable = false, updatable = false)
  UUID productId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(name = "failure_reason", length = 50)
  String failureReason;

  @Column(name = "card_id")
  UUID cardId;

  @Column(name = "requested_at", nullable = false, updatable = false)
  Instant requestedAt;

  @Column(name = "decided_at")
  Instant decidedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Version Long version;

  protected IssuanceRequestJpaEntity() {}
}
