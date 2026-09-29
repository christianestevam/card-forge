package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository {

  /**
   * Insere o cartão se o {@code panHmac} estiver livre ({@code ON CONFLICT (pan_hmac) DO NOTHING}).
   *
   * @return false se o PAN colidiu com um cartão existente
   * @throws NonCanceledCardConflictException se já existe cartão não cancelado para o portador e o
   *     produto (índice único parcial)
   */
  boolean insertIfPanFree(Card card);

  boolean existsNonCanceled(UUID cardholderId, UUID productId);

  Optional<Card> findById(UUID id);

  class NonCanceledCardConflictException extends RuntimeException {
    public NonCanceledCardConflictException(Throwable cause) {
      super("A non-canceled card already exists for this cardholder and product", cause);
    }
  }
}
