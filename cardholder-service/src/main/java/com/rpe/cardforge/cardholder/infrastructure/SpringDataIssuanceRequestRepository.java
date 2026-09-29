package com.rpe.cardforge.cardholder.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataIssuanceRequestRepository
    extends JpaRepository<IssuanceRequestJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from IssuanceRequestJpaEntity r where r.id = :id")
  Optional<IssuanceRequestJpaEntity> findByIdForUpdate(UUID id);

  Optional<IssuanceRequestJpaEntity> findFirstByCardholderIdOrderByRequestedAtDesc(UUID cardholderId);
}
