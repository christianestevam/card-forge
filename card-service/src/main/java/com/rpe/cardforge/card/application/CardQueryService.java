package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.Card;
import com.rpe.cardforge.platform.paging.PageBounds;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CardQueryService {

  private final CardRepository cards;
  private final ProductDetails products;

  public CardQueryService(CardRepository cards, ProductDetails products) {
    this.cards = cards;
    this.products = products;
  }

  public Card get(UUID id) {
    return cards.findById(id).orElseThrow(() -> new CardNotFoundException(id));
  }

  /** Cartão com os detalhes do produto (cache ou catálogo), sem afetar a regra de emissão. */
  public CardDetails getWithProduct(UUID id) {
    Card card = get(id);
    return new CardDetails(card, products.forQuery(card.productId()));
  }

  public record CardDetails(Card card, ProductDetails.ProductView product) {}

  /** Página de cartões do portador; portador sem cartões devolve página vazia. */
  public CardPage listByCardholder(UUID cardholderId, int page, int size) {
    long offset = PageBounds.offset(page, size);
    long total = cards.countByCardholderId(cardholderId);
    List<Card> content = cards.findByCardholderId(cardholderId, offset, size);
    return new CardPage(content, page, size, total);
  }

  public record CardPage(List<Card> content, int page, int size, long totalElements) {}
}
