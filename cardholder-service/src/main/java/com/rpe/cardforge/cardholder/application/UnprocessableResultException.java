package com.rpe.cardforge.cardholder.application;

/** Resultado que não pode ser aplicado: solicitação desconhecida ou contraditória. */
public class UnprocessableResultException extends RuntimeException {

  public UnprocessableResultException(String message) {
    super(message);
  }
}
