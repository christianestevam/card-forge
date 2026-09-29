package com.rpe.cardforge.cardholder.application;

import java.time.LocalDate;
import java.util.UUID;

/** Dados do cadastro. Contém dados pessoais: {@link #toString()} não os expõe. */
public record RegisterCardholderCommand(
    String cpf, String fullName, LocalDate birthDate, UUID productId) {

  @Override
  public String toString() {
    return "RegisterCardholderCommand[productId=" + productId + "]";
  }
}
