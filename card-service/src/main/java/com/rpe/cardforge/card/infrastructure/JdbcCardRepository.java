package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.application.CardRepository;
import com.rpe.cardforge.card.domain.Card;
import com.rpe.cardforge.card.domain.CardStatus;
import com.rpe.cardforge.platform.persistence.UniqueConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistência do cartão com JDBC: as garantias de unicidade dependem de {@code ON CONFLICT}
 * direcionado a uma constraint específica, que o JPA não expressa.
 */
@Repository
class JdbcCardRepository implements CardRepository {

  static final String UK_ACTIVE_PER_CARDHOLDER_PRODUCT = "uk_cards_active_per_cardholder_product";

  private final JdbcClient jdbc;

  JdbcCardRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfPanFree(Card card) {
    try {
      return jdbc.sql(
                  """
                  INSERT INTO cards
                    (id, cardholder_id, product_id, issuance_request_id, pan_hmac, pan_last_four,
                     expiration_date, status, created_at, updated_at, version)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                  ON CONFLICT (pan_hmac) DO NOTHING
                  """)
              .params(
                  card.id(),
                  card.cardholderId(),
                  card.productId(),
                  card.issuanceRequestId(),
                  card.panHmac(),
                  card.panLastFour(),
                  card.expirationDate().toString(),
                  card.status().name(),
                  Timestamp.from(card.createdAt()),
                  Timestamp.from(card.updatedAt()))
              .update()
          == 1;
    } catch (DataIntegrityViolationException e) {
      if (UniqueConstraints.isViolation(e, UK_ACTIVE_PER_CARDHOLDER_PRODUCT)) {
        throw new NonCanceledCardConflictException(e);
      }
      throw e;
    }
  }

  @Override
  public boolean existsNonCanceled(UUID cardholderId, UUID productId) {
    return jdbc.sql(
            """
            SELECT EXISTS (SELECT 1 FROM cards
                           WHERE cardholder_id = ? AND product_id = ? AND status <> 'CANCELED')
            """)
        .params(cardholderId, productId)
        .query(Boolean.class)
        .single();
  }

  @Override
  public Optional<Card> findById(UUID id) {
    return jdbc.sql("SELECT * FROM cards WHERE id = ?").param(id).query(this::map).optional();
  }

  private Card map(ResultSet rs, int row) throws SQLException {
    return new Card(
        rs.getObject("id", UUID.class),
        rs.getObject("cardholder_id", UUID.class),
        rs.getObject("product_id", UUID.class),
        rs.getObject("issuance_request_id", UUID.class),
        rs.getString("pan_hmac"),
        rs.getString("pan_last_four"),
        YearMonth.parse(rs.getString("expiration_date")),
        CardStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
