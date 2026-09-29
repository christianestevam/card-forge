package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
import java.time.Clock;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transições de status do cartão (FR7.2). Sem histórico de transições nesta release (desvio D5): a
 * mudança só atualiza {@code updatedAt}.
 */
@Service
public class CardStatusService {

  private final CardRepository cards;
  private final Clock clock;

  public CardStatusService(CardRepository cards, Clock clock) {
    this.cards = cards;
    this.clock = clock;
  }

  @Transactional
  public Card block(UUID cardId) {
    return change(cardId, (card, now) -> card.block(now));
  }

  @Transactional
  public Card unblock(UUID cardId) {
    return change(cardId, (card, now) -> card.unblock(now));
  }

  @Transactional
  public Card cancel(UUID cardId) {
    return change(cardId, (card, now) -> card.cancel(now));
  }

  private Card change(UUID cardId, BiFunction<Card, java.time.Instant, Card> transition) {
    Card current =
        cards.findByIdForUpdate(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
    Card next = transition.apply(current, clock.instant());
    if (next != current) {
      cards.updateStatus(next);
    }
    return next;
  }
}
