package com.rpe.cardforge.card.web;

import com.rpe.cardforge.card.application.CardQueryService;
import com.rpe.cardforge.card.application.CardStatusService;
import com.rpe.cardforge.platform.paging.PageMetadata;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /** Lista os cartões de um portador; {@code size} acima de 100 gera 400. */
  @GetMapping
  CardPageResponse list(
      @RequestParam UUID cardholderId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    CardQueryService.CardPage result = cards.listByCardholder(cardholderId, page, size);
    return new CardPageResponse(
        result.content().stream().map(CardResponse::from).toList(),
        PageMetadata.of(result.page(), result.size(), result.totalElements()));
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

  record CardPageResponse(List<CardResponse> content, PageMetadata page) {}
}
