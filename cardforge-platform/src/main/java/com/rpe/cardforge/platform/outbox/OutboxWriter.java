package com.rpe.cardforge.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.correlation.CorrelationId;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava eventos no outbox, sempre dentro da transação de negócio de quem chama. */
public class OutboxWriter {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxWriter(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Enfileira um evento. Exige transação ativa: publicar fora dela quebraria a atomicidade entre o
   * dado de negócio e o evento.
   *
   * @return o eventId da linha criada
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID write(
      String destination, String eventType, int eventVersion, UUID aggregateId, Object payload) {
    UUID eventId = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO outbox_events
              (event_id, aggregate_id, destination, event_type, event_version, occurred_at,
               correlation_id, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
            """)
        .params(
            eventId,
            aggregateId,
            destination,
            eventType,
            eventVersion,
            Timestamp.from(clock.instant()),
            CorrelationId.current(),
            toJson(payload))
        .update();
    return eventId;
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Event payload is not serializable", e);
    }
  }
}
