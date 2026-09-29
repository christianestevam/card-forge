package com.rpe.cardforge.platform.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Pendências do outbox e idade do evento pendente mais antigo (FR3.3). */
public class OutboxMetrics implements MeterBinder {

  private final JdbcClient jdbc;

  public OutboxMetrics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("cardforge.outbox.pending", this::pending)
        .description("Outbox events not yet published")
        .register(registry);
    Gauge.builder("cardforge.outbox.oldest.age", this::oldestAgeSeconds)
        .description("Age in seconds of the oldest pending outbox event")
        .baseUnit("seconds")
        .register(registry);
  }

  private double pending() {
    return jdbc.sql("SELECT count(*) FROM outbox_events WHERE sent_at IS NULL")
        .query(Long.class)
        .single();
  }

  private double oldestAgeSeconds() {
    Double age =
        jdbc.sql(
                """
                SELECT EXTRACT(EPOCH FROM (now() - min(occurred_at)))
                FROM outbox_events WHERE sent_at IS NULL
                """)
            .query(Double.class)
            .optional()
            .orElse(null);
    return age == null ? 0 : age;
  }
}
