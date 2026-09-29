package com.rpe.cardforge.cardholder.domain;

import java.util.List;

/** Uma ou mais regras de cadastro violadas; lista todas de uma vez. */
public class CardholderValidationException extends RuntimeException {

  private final List<Violation> violations;

  public CardholderValidationException(List<Violation> violations) {
    super("Cardholder registration has " + violations.size() + " invalid field(s)");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
