package com.rpe.cardforge.platform.time;

import java.time.Clock;
import java.time.Duration;

/** Relógios com a mesma precisão do PostgreSQL. */
public final class Clocks {

  private Clocks() {}

  /**
   * Relógio UTC com precisão de microssegundos, a mesma do {@code TIMESTAMPTZ}. Sem isso, no Linux
   * o instante em memória tem nanossegundos e difere do valor lido do banco: o mesmo recurso
   * apareceria com dois {@code updatedAt} diferentes conforme a leitura.
   */
  public static Clock systemUtcMicros() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
