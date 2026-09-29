package com.rpe.cardforge.platform.events;

/** Mensagem que não pode ser interpretada: JSON inválido, tipo ou versão desconhecidos. */
public class InvalidEventException extends RuntimeException {

  public InvalidEventException(String message) {
    super(message);
  }

  public InvalidEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
