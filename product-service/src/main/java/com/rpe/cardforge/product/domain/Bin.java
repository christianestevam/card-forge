package com.rpe.cardforge.product.domain;

import java.util.regex.Pattern;

/** BIN do produto: exatamente 8 dígitos numéricos (BR1.1). */
public record Bin(String value) {

  private static final Pattern FORMAT = Pattern.compile("\\d{8}");

  public Bin {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("BIN must have exactly 8 numeric digits");
    }
  }
}
