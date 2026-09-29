package com.rpe.cardforge.cardholder.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Corpo do cadastro. As regras de CPF, idade e nome ficam no domínio, que lista todas as violações
 * de uma vez. Contém dados pessoais: {@link #toString()} não os expõe.
 */
record RegisterCardholderRequest(
    @NotBlank String cpf,
    @NotBlank String fullName,
    @NotNull LocalDate birthDate,
    @NotNull UUID productId) {

  @Override
  public String toString() {
    return "RegisterCardholderRequest[productId=" + productId + "]";
  }
}
