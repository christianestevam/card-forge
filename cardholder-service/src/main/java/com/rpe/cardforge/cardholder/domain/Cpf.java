package com.rpe.cardforge.cardholder.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CPF guardado só com dígitos (BR2.1). Aceita 11 dígitos ou o formato 000.000.000-00. {@link
 * #toString()} nunca mostra o número completo.
 */
public final class Cpf {

  private static final Pattern DIGITS = Pattern.compile("\\d{11}");
  private static final Pattern FORMATTED = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

  private final String digits;

  private Cpf(String digits) {
    this.digits = digits;
  }

  /** Motivo da rejeição, ou vazio se o valor é um CPF válido. */
  public static Optional<String> rejectionRule(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.of("REQUIRED");
    }
    if (!DIGITS.matcher(raw).matches() && !FORMATTED.matcher(raw).matches()) {
      return Optional.of("CPF_FORMAT");
    }
    String digits = raw.replaceAll("\\D", "");
    if (digits.chars().distinct().count() == 1) {
      return Optional.of("CPF_REPEATED_DIGITS");
    }
    if (checkDigit(digits, 9) != digits.charAt(9) - '0'
        || checkDigit(digits, 10) != digits.charAt(10) - '0') {
      return Optional.of("CPF_CHECK_DIGITS");
    }
    return Optional.empty();
  }

  public static Cpf of(String raw) {
    rejectionRule(raw)
        .ifPresent(
            rule -> {
              throw new IllegalArgumentException("Invalid CPF: " + rule);
            });
    return new Cpf(raw.replaceAll("\\D", ""));
  }

  private static int checkDigit(String digits, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += (digits.charAt(i) - '0') * (length + 1 - i);
    }
    int rest = (sum * 10) % 11;
    return rest == 10 ? 0 : rest;
  }

  public String digits() {
    return digits;
  }

  /** Formato mascarado das respostas: {@code ***.456.789-**}. */
  public String masked() {
    return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Cpf other && other.digits.equals(digits);
  }

  @Override
  public int hashCode() {
    return digits.hashCode();
  }

  @Override
  public String toString() {
    return "Cpf[" + masked() + "]";
  }
}
