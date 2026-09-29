package com.rpe.cardforge.cardholder.application;

import java.util.UUID;

public class CardholderNotFoundException extends RuntimeException {

  public CardholderNotFoundException(UUID id) {
    super("Cardholder " + id + " not found");
  }
}
