package com.rpe.cardforge.product.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ProductJpaEntity p where p.id = :id")
  Optional<ProductJpaEntity> findByIdForUpdate(UUID id);
}
