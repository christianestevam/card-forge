package com.rpe.cardforge.card.domain;

/** Transição de status não permitida a partir do status atual. */
public class InvalidStatusTransitionException extends RuntimeException {

  public InvalidStatusTransitionException(CardStatus from, CardStatus to) {
    super("Card cannot go from " + from + " to " + to);
  }
}
