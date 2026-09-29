package com.rpe.cardforge.card.domain;

/** Fonte de dígitos aleatórios; substituível em teste para forçar colisões. */
public interface RandomDigits {

  /** Um dígito de 0 a 9. */
  int nextDigit();
}
