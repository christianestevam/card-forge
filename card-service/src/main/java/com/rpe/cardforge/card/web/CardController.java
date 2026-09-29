package com.rpe.cardforge.card.web;

import static com.rpe.cardforge.platform.openapi.ProblemResponsesCustomizer.PROBLEM_SCHEMA;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.rpe.cardforge.card.application.CardQueryService;
import com.rpe.cardforge.card.application.CardStatusService;
import com.rpe.cardforge.platform.paging.PageMetadata;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

  private static final String PROBLEM_JSON = APPLICATION_PROBLEM_JSON_VALUE;
  private static final String PROBLEM = PROBLEM_SCHEMA;

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

  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: cartão inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @GetMapping("/{cardId}")
  CardResponse get(@PathVariable UUID cardId) {
    return CardResponse.from(cards.get(cardId));
  }

  /** ACTIVE -> BLOCKED; 200 sem mudança se já estiver BLOCKED; 409 a partir de CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "invalid-status-transition: a partir de CANCELED",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardId}/block")
  CardResponse block(@PathVariable UUID cardId) {
    return CardResponse.from(status.block(cardId));
  }

  /** BLOCKED -> ACTIVE; 200 sem mudança se já estiver ACTIVE; 409 a partir de CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "invalid-status-transition: a partir de CANCELED",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardId}/unblock")
  CardResponse unblock(@PathVariable UUID cardId) {
    return CardResponse.from(status.unblock(cardId));
  }

  /** ACTIVE ou BLOCKED -> CANCELED (terminal); 200 sem mudança se já estiver CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardId}/cancel")
  CardResponse cancel(@PathVariable UUID cardId) {
    return CardResponse.from(status.cancel(cardId));
  }

  record CardPageResponse(List<CardResponse> content, PageMetadata page) {}
}
