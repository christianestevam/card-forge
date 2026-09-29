package com.rpe.cardforge.cardholder.application;

import java.util.UUID;

/** Porta de leitura dos cartões no card-service (consulta consolidada). */
public interface CardDirectory {

  /**
   * @throws CardDirectoryUnavailableException em qualquer falha técnica ou resposta inesperada
   */
  CardView get(UUID cardId);

  record CardView(UUID id, String panLastFour, String expirationDate, String status) {}

  class CardDirectoryUnavailableException extends RuntimeException {
    public CardDirectoryUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
