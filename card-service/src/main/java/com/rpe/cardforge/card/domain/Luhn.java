package com.rpe.cardforge.card.domain;

/** Dígito verificador de Luhn (mod 10). */
public final class Luhn {

  private Luhn() {}

  /** Dígito que torna {@code digitsWithoutCheck + digito} válido segundo Luhn. */
  public static int checkDigit(String digitsWithoutCheck) {
    int sum = 0;
    boolean doubleIt = true;
    for (int i = digitsWithoutCheck.length() - 1; i >= 0; i--) {
      int d = digitsWithoutCheck.charAt(i) - '0';
      if (d < 0 || d > 9) {
        throw new IllegalArgumentException("Only digits are allowed");
      }
      if (doubleIt) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return (10 - sum % 10) % 10;
  }

  public static boolean isValid(String digits) {
    if (digits == null || digits.length() < 2 || !digits.chars().allMatch(Character::isDigit)) {
      return false;
    }
    int last = digits.charAt(digits.length() - 1) - '0';
    return checkDigit(digits.substring(0, digits.length() - 1)) == last;
  }
}
