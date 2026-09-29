package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.IssuanceRequestRepository;
import com.rpe.cardforge.cardholder.domain.FailureReason;
import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import com.rpe.cardforge.cardholder.domain.IssuanceStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaIssuanceRequestRepository implements IssuanceRequestRepository {

  private final SpringDataIssuanceRequestRepository jpa;
  private final Clock clock;

  JpaIssuanceRequestRepository(SpringDataIssuanceRequestRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public void insert(IssuanceRequest r) {
    IssuanceRequestJpaEntity e = new IssuanceRequestJpaEntity();
    e.id = r.id();
    e.cardholderId = r.cardholderId();
    e.productId = r.productId();
    e.requestedAt = r.requestedAt();
    e.createdAt = r.requestedAt();
    copyState(r, e);
    jpa.save(e);
  }

  @Override
  public Optional<IssuanceRequest> findByIdForUpdate(UUID id) {
    return jpa.findByIdForUpdate(id).map(JpaIssuanceRequestRepository::toDomain);
  }

  @Override
  public Optional<IssuanceRequest> findByCardholderId(UUID cardholderId) {
    return jpa.findFirstByCardholderIdOrderByRequestedAtDesc(cardholderId)
        .map(JpaIssuanceRequestRepository::toDomain);
  }

  @Override
  public void update(IssuanceRequest r) {
    IssuanceRequestJpaEntity e =
        jpa.findById(r.id()).orElseThrow(() -> new IllegalStateException("Missing " + r.id()));
    copyState(r, e);
  }

  private void copyState(IssuanceRequest r, IssuanceRequestJpaEntity e) {
    e.status = r.status().name();
    e.failureReason = r.failureReason() == null ? null : r.failureReason().name();
    e.cardId = r.cardId();
    e.decidedAt = r.decidedAt();
    e.updatedAt = clock.instant();
  }

  private static IssuanceRequest toDomain(IssuanceRequestJpaEntity e) {
    return new IssuanceRequest(
        e.id,
        e.cardholderId,
        e.productId,
        IssuanceStatus.valueOf(e.status),
        e.failureReason == null ? null : FailureReason.valueOf(e.failureReason),
        e.cardId,
        e.requestedAt,
        e.decidedAt);
  }
}
