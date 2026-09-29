package com.rpe.cardforge.cardholder.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataCardholderRepository extends JpaRepository<CardholderJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CardholderJpaEntity c where c.id = :id")
  Optional<CardholderJpaEntity> findByIdForUpdate(UUID id);
}
