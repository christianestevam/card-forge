package com.rpe.cardforge.card.web;

import com.rpe.cardforge.card.application.CardQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
class CardController {

  private final CardQueryService cards;

  CardController(CardQueryService cards) {
    this.cards = cards;
  }

  @GetMapping("/{cardId}")
  CardResponse get(@PathVariable UUID cardId) {
    return CardResponse.from(cards.get(cardId));
  }
}
