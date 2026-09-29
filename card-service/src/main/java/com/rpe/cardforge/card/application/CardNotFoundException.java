package com.rpe.cardforge.card.application;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {

  public CardNotFoundException(UUID id) {
    super("Card " + id + " not found");
  }
}
