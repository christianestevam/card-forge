package com.rpe.cardforge.card.domain;

/**
 * Ocupação da faixa de PANs de um BIN. Cada BIN tem 10⁷ PANs possíveis (7 dígitos aleatórios); o
 * alerta dispara em 70%, antes que as colisões fiquem frequentes (FR4.9).
 */
public record BinOccupancy(String bin, long cards) {

  public static final long PAN_SPACE_PER_BIN = 10_000_000L;
  public static final double ALERT_THRESHOLD = 0.70;

  public double ratio() {
    return (double) cards / PAN_SPACE_PER_BIN;
  }

  public boolean requiresAlert() {
    return ratio() >= ALERT_THRESHOLD;
  }
}
