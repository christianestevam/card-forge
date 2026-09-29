package com.rpe.cardforge.card.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Cartão emitido. Guarda só o identificador de unicidade do PAN ({@code panHmac}), o BIN (8
 * primeiros dígitos, públicos no produto) e os 4 últimos dígitos; o PAN completo não é persistido e
 * não há CVV (BR5.4). O {@code bin} pode ser nulo em cartões emitidos antes da sua introdução.
 */
public record Card(
    UUID id,
    UUID cardholderId,
    UUID productId,
    UUID issuanceRequestId,
    String bin,
    String panHmac,
    String panLastFour,
    YearMonth expirationDate,
    CardStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static final int VALIDITY_YEARS = 5;

  public Card {
    Objects.requireNonNull(id);
    Objects.requireNonNull(cardholderId);
    Objects.requireNonNull(productId);
    Objects.requireNonNull(issuanceRequestId);
    Objects.requireNonNull(panHmac);
    Objects.requireNonNull(panLastFour);
    Objects.requireNonNull(expirationDate);
    Objects.requireNonNull(status);
  }

  /** Novo cartão ACTIVE, com validade de 5 anos a partir do mês da emissão (BR5.2, BR5.3). */
  public static Card issue(
      UUID id,
      UUID cardholderId,
      UUID productId,
      UUID issuanceRequestId,
      String panHmac,
      Pan pan,
      Instant now) {
    YearMonth expiration = YearMonth.from(now.atZone(ZoneOffset.UTC)).plusYears(VALIDITY_YEARS);
    return new Card(
        id,
        cardholderId,
        productId,
        issuanceRequestId,
        pan.bin(),
        panHmac,
        pan.lastFour(),
        expiration,
        CardStatus.ACTIVE,
        now,
        now);
  }

  /** ACTIVE -> BLOCKED. Pedido para o status atual devolve o próprio cartão (BR5.3). */
  public Card block(Instant now) {
    return transition(CardStatus.BLOCKED, now);
  }

  /** BLOCKED -> ACTIVE. */
  public Card unblock(Instant now) {
    return transition(CardStatus.ACTIVE, now);
  }

  /** ACTIVE ou BLOCKED -> CANCELED, terminal. */
  public Card cancel(Instant now) {
    return transition(CardStatus.CANCELED, now);
  }

  private Card transition(CardStatus target, Instant now) {
    if (status == target) {
      return this;
    }
    if (status == CardStatus.CANCELED) {
      throw new InvalidStatusTransitionException(status, target);
    }
    return new Card(
        id,
        cardholderId,
        productId,
        issuanceRequestId,
        bin,
        panHmac,
        panLastFour,
        expirationDate,
        target,
        createdAt,
        now);
  }
}
