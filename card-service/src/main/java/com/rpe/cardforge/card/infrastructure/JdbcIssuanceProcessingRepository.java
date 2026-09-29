package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.application.IssuanceProcessingRepository;
import com.rpe.cardforge.card.domain.FailureReason;
import com.rpe.cardforge.card.domain.IssuanceDecision;
import com.rpe.cardforge.card.domain.IssuanceStatus;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIssuanceProcessingRepository implements IssuanceProcessingRepository {

  private final JdbcClient jdbc;

  JdbcIssuanceProcessingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<IssuanceDecision> find(UUID issuanceRequestId) {
    return jdbc.sql(
            """
            SELECT issuance_request_id, status, card_id, failure_reason, processed_at
            FROM issuance_processing WHERE issuance_request_id = ?
            """)
        .param(issuanceRequestId)
        .query(
            (rs, row) -> {
              String reason = rs.getString("failure_reason");
              return new IssuanceDecision(
                  rs.getObject("issuance_request_id", UUID.class),
                  IssuanceStatus.valueOf(rs.getString("status")),
                  rs.getObject("card_id", UUID.class),
                  reason == null ? null : FailureReason.valueOf(reason),
                  rs.getTimestamp("processed_at").toInstant());
            })
        .optional();
  }

  @Override
  public boolean insertIfAbsent(IssuanceDecision decision) {
    return jdbc.sql(
                """
                INSERT INTO issuance_processing
                  (issuance_request_id, status, card_id, failure_reason, processed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (issuance_request_id) DO NOTHING
                """)
            .params(
                decision.issuanceRequestId(),
                decision.status().name(),
                decision.cardId(),
                decision.failureReason() == null ? null : decision.failureReason().name(),
                Timestamp.from(decision.processedAt()))
            .update()
        == 1;
  }
}
