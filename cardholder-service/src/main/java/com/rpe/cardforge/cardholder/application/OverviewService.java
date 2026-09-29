package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.application.Overview.CardAvailability;
import com.rpe.cardforge.cardholder.application.Overview.CardPart;
import com.rpe.cardforge.cardholder.application.Overview.ProductAvailability;
import com.rpe.cardforge.cardholder.application.Overview.ProductPart;
import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import com.rpe.cardforge.cardholder.domain.IssuanceStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Monta a consulta consolidada (BR6.1 a BR6.3). A falha de uma dependência só marca a parte
 * correspondente como indisponível; a consulta inteira nunca cai por isso.
 */
@Service
public class OverviewService {

  private static final Logger log = LoggerFactory.getLogger(OverviewService.class);

  private final CardholderRepository cardholders;
  private final IssuanceRequestRepository requests;
  private final ProductCatalog catalog;
  private final CardDirectory cards;
  private final Clock clock;

  public OverviewService(
      CardholderRepository cardholders,
      IssuanceRequestRepository requests,
      ProductCatalog catalog,
      CardDirectory cards,
      Clock clock) {
    this.cardholders = cardholders;
    this.requests = requests;
    this.catalog = catalog;
    this.cards = cards;
    this.clock = clock;
  }

  public Overview get(UUID cardholderId) {
    Cardholder cardholder =
        cardholders
            .findById(cardholderId)
            .orElseThrow(() -> new CardholderNotFoundException(cardholderId));
    IssuanceRequest issuance =
        requests
            .findByCardholderId(cardholderId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cardholder " + cardholderId + " has no issuance request"));
    return new Overview(cardholder, issuance, cardPart(issuance), productPart(cardholder));
  }

  private CardPart cardPart(IssuanceRequest issuance) {
    if (issuance.status() != IssuanceStatus.ISSUED) {
      return new CardPart(CardAvailability.NOT_APPLICABLE, null);
    }
    try {
      return new CardPart(CardAvailability.AVAILABLE, cards.get(issuance.cardId()));
    } catch (CardDirectory.CardDirectoryUnavailableException e) {
      log.warn("Card details unavailable for card {}: {}", issuance.cardId(), e.getMessage());
      return new CardPart(CardAvailability.UNAVAILABLE, null);
    }
  }

  private ProductPart productPart(Cardholder cardholder) {
    Instant observedAt = clock.instant();
    try {
      if (catalog.lookup(cardholder.productId()) instanceof ProductCatalog.Found found) {
        return new ProductPart(ProductAvailability.CURRENT, observedAt, found);
      }
      return new ProductPart(ProductAvailability.UNAVAILABLE, null, null);
    } catch (ProductCatalog.CatalogUnavailableException
        | ProductCatalog.CatalogMisconfiguredException e) {
      log.warn("Product unavailable for overview: {}", e.getMessage());
      return new ProductPart(ProductAvailability.UNAVAILABLE, null, null);
    }
  }
}
