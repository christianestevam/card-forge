package com.rpe.cardforge.card.domain;

/** Motivos de recusa de negócio: encerram a solicitação como FAILED, sem retentativa. */
public enum FailureReason {
  PRODUCT_NOT_FOUND,
  PRODUCT_CANCELED,
  NON_CANCELED_CARD_ALREADY_EXISTS
}
