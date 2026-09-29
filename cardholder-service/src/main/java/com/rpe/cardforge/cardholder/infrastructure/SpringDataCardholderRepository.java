package com.rpe.cardforge.cardholder.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCardholderRepository extends JpaRepository<CardholderJpaEntity, UUID> {}
