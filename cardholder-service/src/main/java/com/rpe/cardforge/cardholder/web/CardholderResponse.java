package com.rpe.cardforge.cardholder.web;

import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.CardholderStatus;
import java.time.Instant;
import java.util.UUID;

/** Portador nas respostas: CPF mascarado e sem data de nascimento. */
record CardholderResponse(
    UUID id,
    String maskedCpf,
    String fullName,
    UUID productId,
    CardholderStatus status,
    Instant createdAt,
    Instant updatedAt) {

  static CardholderResponse from(Cardholder c) {
    return new CardholderResponse(
        c.id(),
        c.cpf().masked(),
        c.fullName(),
        c.productId(),
        c.status(),
        c.createdAt(),
        c.updatedAt());
  }
}
