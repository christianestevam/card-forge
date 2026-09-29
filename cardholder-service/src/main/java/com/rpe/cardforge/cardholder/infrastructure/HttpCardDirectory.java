package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.CardDirectory;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Leitura do cartão no card-service (ADR-003); qualquer falha deixa os detalhes indisponíveis. */
@Component
class HttpCardDirectory implements CardDirectory {

  private final RestClient cards;

  HttpCardDirectory(@Qualifier("cardRestClient") RestClient cards) {
    this.cards = cards;
  }

  @Override
  public CardView get(UUID cardId) {
    try {
      CardView view = cards.get().uri("/api/v1/cards/{id}", cardId).retrieve().body(CardView.class);
      if (view == null || !cardId.equals(view.id())) {
        throw new CardDirectoryUnavailableException("Card response out of contract", null);
      }
      return view;
    } catch (RestClientException | OAuth2AuthorizationException e) {
      throw new CardDirectoryUnavailableException("Card service call failed: " + e.getMessage(), e);
    }
  }
}
