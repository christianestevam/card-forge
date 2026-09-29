package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CardQueryService {

  private final CardRepository cards;

  public CardQueryService(CardRepository cards) {
    this.cards = cards;
  }

  public Card get(UUID id) {
    return cards.findById(id).orElseThrow(() -> new CardNotFoundException(id));
  }

  /** Página de cartões do portador; portador sem cartões devolve página vazia. */
  public CardPage listByCardholder(UUID cardholderId, int page, int size) {
    long total = cards.countByCardholderId(cardholderId);
    List<Card> content = cards.findByCardholderId(cardholderId, page * size, size);
    return new CardPage(content, page, size, total);
  }

  public record CardPage(List<Card> content, int page, int size, long totalElements) {}
}
