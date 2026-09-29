package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.ProductObservationRepository;
import com.rpe.cardforge.cardholder.domain.ProductObservation;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProductObservationRepository implements ProductObservationRepository {

  private final JdbcClient jdbc;

  JdbcProductObservationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ProductObservation> find(UUID productId) {
    return jdbc.sql(
            "SELECT product_id, name, bin, status, observed_at FROM product_observations"
                + " WHERE product_id = ?")
        .param(productId)
        .query(
            (rs, row) ->
                new ProductObservation(
                    rs.getObject("product_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("bin"),
                    rs.getString("status"),
                    rs.getTimestamp("observed_at").toInstant()))
        .optional();
  }

  @Override
  public void saveIfNewer(ProductObservation o) {
    jdbc.sql(
            """
            INSERT INTO product_observations (product_id, name, bin, status, observed_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (product_id) DO UPDATE
              SET name = EXCLUDED.name, bin = EXCLUDED.bin, status = EXCLUDED.status,
                  observed_at = EXCLUDED.observed_at
              WHERE product_observations.observed_at < EXCLUDED.observed_at
            """)
        .params(o.productId(), o.name(), o.bin(), o.status(), Timestamp.from(o.observedAt()))
        .update();
  }
}
