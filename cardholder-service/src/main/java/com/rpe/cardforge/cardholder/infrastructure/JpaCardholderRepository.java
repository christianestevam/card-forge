package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.CardholderRepository;
import com.rpe.cardforge.cardholder.application.CpfAlreadyRegisteredException;
import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.CardholderStatus;
import com.rpe.cardforge.cardholder.domain.Cpf;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JpaCardholderRepository implements CardholderRepository {

  private final SpringDataCardholderRepository jpa;
  private final JdbcClient jdbc;

  JpaCardholderRepository(SpringDataCardholderRepository jpa, JdbcClient jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  /**
   * Insere com {@code ON CONFLICT (cpf) DO NOTHING}: um CPF repetido não gera erro no banco, então
   * o valor não aparece no log do PostgreSQL nem no do Hibernate. A constraint continua sendo a
   * garantia de unicidade, também sob concorrência.
   */
  @Override
  public void insert(Cardholder c) {
    int inserted =
        jdbc.sql(
                """
                INSERT INTO cardholders
                  (id, cpf, full_name, birth_date, product_id, status, created_at, updated_at,
                   version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT (cpf) DO NOTHING
                """)
            .params(
                c.id(),
                c.cpf().digits(),
                c.fullName(),
                c.birthDate(),
                c.productId(),
                c.status().name(),
                Timestamp.from(c.createdAt()),
                Timestamp.from(c.updatedAt()))
            .update();
    if (inserted == 0) {
      throw new CpfAlreadyRegisteredException();
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
