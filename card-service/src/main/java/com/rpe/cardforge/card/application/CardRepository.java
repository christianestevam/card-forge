package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
import java.util.List;
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

  /** Cartões do portador, do mais recente para o mais antigo. */
  List<Card> findByCardholderId(UUID cardholderId, int offset, int limit);

  long countByCardholderId(UUID cardholderId);

  /** Lê com lock de escrita, para aplicar transições concorrentes em série. */
  Optional<Card> findByIdForUpdate(UUID id);

  void updateStatus(Card card);

  class NonCanceledCardConflictException extends RuntimeException {
    public NonCanceledCardConflictException(Throwable cause) {
      super("A non-canceled card already exists for this cardholder and product", cause);
    }
  }
}
