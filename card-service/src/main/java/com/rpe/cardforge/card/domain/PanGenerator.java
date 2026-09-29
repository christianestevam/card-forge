package com.rpe.cardforge.card.domain;

/** Gera candidatos de PAN: BIN (8) + 7 dígitos aleatórios + dígito de Luhn (BR5.1). */
public class PanGenerator {

  private final RandomDigits random;

  public PanGenerator(RandomDigits random) {
    this.random = random;
  }

  public Pan generate(String bin) {
    if (bin == null || bin.length() != 8 || !bin.chars().allMatch(Character::isDigit)) {
      throw new IllegalArgumentException("BIN must have exactly 8 numeric digits");
    }
    StringBuilder digits = new StringBuilder(16).append(bin);
    for (int i = 0; i < 7; i++) {
      digits.append(random.nextDigit());
    }
    digits.append(Luhn.checkDigit(digits.toString()));
    return new Pan(digits.toString());
  }
}
