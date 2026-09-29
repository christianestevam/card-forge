package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.CardholderRepository;
import com.rpe.cardforge.cardholder.application.CpfAlreadyRegisteredException;
import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.CardholderStatus;
import com.rpe.cardforge.cardholder.domain.Cpf;
import com.rpe.cardforge.platform.persistence.UniqueConstraints;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaCardholderRepository implements CardholderRepository {

  static final String UK_CPF = "uk_cardholders_cpf";

  private final SpringDataCardholderRepository jpa;

  JpaCardholderRepository(SpringDataCardholderRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void insert(Cardholder c) {
    CardholderJpaEntity e = new CardholderJpaEntity();
    e.id = c.id();
    e.cpf = c.cpf().digits();
    e.fullName = c.fullName();
    e.birthDate = c.birthDate();
    e.productId = c.productId();
    e.status = c.status().name();
    e.createdAt = c.createdAt();
    e.updatedAt = c.updatedAt();
    try {
      jpa.saveAndFlush(e);
    } catch (DataIntegrityViolationException ex) {
      if (UniqueConstraints.isViolation(ex, UK_CPF)) {
        throw new CpfAlreadyRegisteredException();
      }
      throw ex;
    }
  }

  @Override
  public Optional<Cardholder> findById(UUID id) {
    return jpa.findById(id).map(JpaCardholderRepository::toDomain);
  }

  @Override
  public Optional<Cardholder> findByIdForUpdate(UUID id) {
    return jpa.findByIdForUpdate(id).map(JpaCardholderRepository::toDomain);
  }

  @Override
  public void updateStatus(Cardholder c) {
    CardholderJpaEntity e =
        jpa.findById(c.id()).orElseThrow(() -> new IllegalStateException("Missing " + c.id()));
    e.status = c.status().name();
    e.updatedAt = c.updatedAt();
  }

  private static Cardholder toDomain(CardholderJpaEntity e) {
    return new Cardholder(
        e.id,
        Cpf.of(e.cpf),
        e.fullName,
        e.birthDate,
        e.productId,
        CardholderStatus.valueOf(e.status),
        e.createdAt,
        e.updatedAt,
        e.version);
  }
}
