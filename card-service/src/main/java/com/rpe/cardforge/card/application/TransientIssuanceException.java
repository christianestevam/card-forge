package com.rpe.cardforge.card.application;

/** Falha técnica transitória: nada é gravado e a SQS entrega de novo depois do backoff. */
public class TransientIssuanceException extends RuntimeException {

  public TransientIssuanceException(String message, Throwable cause) {
    super(message, cause);
  }

  public TransientIssuanceException(String message) {
    super(message);
  }
}
