package com.rpe.cardforge.card.application;

import java.util.function.DoubleSupplier;

/**
 * Backoff exponencial para falhas técnicas: 30 s na primeira, dobrando a cada recebimento, com
 * jitter de ±20% e teto rígido (5 min) aplicado depois do jitter.
 */
public class RetryBackoff {

  private final long initialSeconds;
  private final long maxSeconds;
  private final DoubleSupplier random;

  public RetryBackoff(long initialSeconds, long maxSeconds, DoubleSupplier random) {
    this.initialSeconds = initialSeconds;
    this.maxSeconds = maxSeconds;
    this.random = random;
  }

  /**
   * @param receiveCount quantas vezes a mensagem já foi recebida (1 na primeira entrega)
   * @return segundos até a próxima visibilidade
   */
  public int delaySeconds(int receiveCount) {
    int exponent = Math.max(0, Math.min(receiveCount - 1, 20));
    double base = Math.min(initialSeconds * Math.pow(2, exponent), maxSeconds * 2.0);
    double jittered = base * (0.8 + 0.4 * random.getAsDouble());
    return (int) Math.max(1, Math.min(Math.round(jittered), maxSeconds));
  }
}
