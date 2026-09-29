package com.rpe.cardforge.card.domain;

/**
 * PAN de 16 dígitos. Existe só em memória, durante a emissão: nunca é persistido, logado nem
 * exposto; {@link #toString()} mostra apenas os 4 últimos dígitos.
 */
public final class Pan {

  private final String digits;

  public Pan(String digits) {
    if (digits == null || digits.length() != 16 || !Luhn.isValid(digits)) {
      throw new IllegalArgumentException("PAN must have 16 digits and a valid Luhn check digit");
    }
    this.digits = digits;
  }

  public String digits() {
    return digits;
  }

  public String bin() {
    return digits.substring(0, 8);
  }

  public String lastFour() {
    return digits.substring(12);
  }

  @Override
  public String toString() {
    return "Pan[************" + lastFour() + "]";
  }
}
