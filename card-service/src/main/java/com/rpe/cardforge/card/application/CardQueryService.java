package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
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
}
