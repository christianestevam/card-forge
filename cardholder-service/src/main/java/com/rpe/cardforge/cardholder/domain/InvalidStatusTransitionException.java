package com.rpe.cardforge.cardholder.domain;

/** Transição de status não permitida a partir do status atual. */
public class InvalidStatusTransitionException extends RuntimeException {

  public InvalidStatusTransitionException(CardholderStatus from, CardholderStatus to) {
    super("Cardholder cannot go from " + from + " to " + to);
  }
}
