package com.rpe.cardforge.card.application;

/**
 * Falha de configuração (401, 403 ou contrato inválido do catálogo). Gera alerta e nunca é
 * tratada como produto inexistente; a mensagem fica para nova tentativa depois da correção.
 */
public class IssuanceConfigurationException extends RuntimeException {

  public IssuanceConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
