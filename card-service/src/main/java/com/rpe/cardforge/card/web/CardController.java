package com.rpe.cardforge.card.web;

import com.rpe.cardforge.card.application.CardQueryService;
import com.rpe.cardforge.card.application.CardStatusService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
class CardController {

  private final CardQueryService cards;
  private final CardStatusService status;

  CardController(CardQueryService cards, CardStatusService status) {
    this.cards = cards;
    this.status = status;
  }

  @GetMapping("/{cardId}")
  CardResponse get(@PathVariable UUID cardId) {
    return CardResponse.from(cards.get(cardId));
  }

  /** ACTIVE -> BLOCKED; 200 sem mudança se já estiver BLOCKED; 409 a partir de CANCELED. */
  @PostMapping("/{cardId}/block")
  CardResponse block(@PathVariable UUID cardId) {
    return CardResponse.from(status.block(cardId));
  }

  /** BLOCKED -> ACTIVE; 200 sem mudança se já estiver ACTIVE; 409 a partir de CANCELED. */
  @PostMapping("/{cardId}/unblock")
  CardResponse unblock(@PathVariable UUID cardId) {
    return CardResponse.from(status.unblock(cardId));
  }

  /** ACTIVE ou BLOCKED -> CANCELED (terminal); 200 sem mudança se já estiver CANCELED. */
  @PostMapping("/{cardId}/cancel")
  CardResponse cancel(@PathVariable UUID cardId) {
    return CardResponse.from(status.cancel(cardId));
  }
}
