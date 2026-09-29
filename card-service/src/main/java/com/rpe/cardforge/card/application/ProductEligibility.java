package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.application.ProductCatalog.CatalogMisconfiguredException;
import com.rpe.cardforge.card.application.ProductCatalog.CatalogUnavailableException;
import com.rpe.cardforge.card.application.ProductCatalog.Found;
import com.rpe.cardforge.card.application.ProductCatalog.NotFound;
import com.rpe.cardforge.card.domain.FailureReason;
import com.rpe.cardforge.card.domain.ProductObservation;
import com.rpe.cardforge.card.domain.ProductState;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Autoridade sobre a elegibilidade do produto para emissão (BR4.1, BR1.3). Usa o cache só com
 * observação ACTIVE de no máximo 5 minutos; senão consulta o catálogo uma vez. Nunca é chamada
 * dentro de transação.
 */
@Component
public class ProductEligibility {

  private static final Logger log = LoggerFactory.getLogger(ProductEligibility.class);

  private final ProductCache cache;
  private final ProductCatalog catalog;
  private final Clock clock;
  private final IssuanceProperties properties;
  private final MeterRegistry meters;

  public ProductEligibility(
      ProductCache cache,
      ProductCatalog catalog,
      Clock clock,
      IssuanceProperties properties,
      MeterRegistry meters) {
    this.cache = cache;
    this.catalog = catalog;
    this.clock = clock;
    this.properties = properties;
    this.meters = meters;
  }

  public Eligibility check(UUID productId) {
    Optional<ProductObservation> cached = readCache(productId);
    if (cached.isPresent() && cached.get().isKnownCancellation()) {
      // CANCELED é terminal: a lápide recusa sem consultar o catálogo (TC7).
      meters.counter("cardforge.product.cache", "result", "tombstone").increment();
      return new Eligibility.Ineligible(FailureReason.PRODUCT_CANCELED);
    }
    if (cached.isPresent()
        && cached.get().authorizesIssuanceAt(clock.instant(), properties.eligibilityWindow())) {
      meters.counter("cardforge.product.cache", "result", "hit").increment();
      return new Eligibility.Eligible(cached.get().bin());
    }
    meters.counter("cardforge.product.cache", "result", "miss").increment();

    // O instante anterior à chamada é conservador: a observação real é no mínimo tão recente.
    Instant observedAt = clock.instant();
    ProductCatalog.Lookup lookup;
    try {
      lookup = catalog.lookup(productId);
    } catch (CatalogUnavailableException e) {
      throw new TransientIssuanceException("Product catalog unavailable", e);
    } catch (CatalogMisconfiguredException e) {
      log.error(
          "ALERT configuration: product catalog call rejected or out of contract: {}",
          e.getMessage());
      throw new IssuanceConfigurationException("Product catalog misconfigured", e);
    }

    return switch (lookup) {
      case Found found when found.status() == ProductState.ACTIVE -> {
        writeCache(
            new ProductObservation(
                productId, found.name(), found.bin(), ProductState.ACTIVE, observedAt));
        yield new Eligibility.Eligible(found.bin());
      }
      case Found found -> {
        writeCache(
            new ProductObservation(
                productId, found.name(), found.bin(), ProductState.CANCELED, observedAt));
        yield new Eligibility.Ineligible(FailureReason.PRODUCT_CANCELED);
      }
      case NotFound notFound -> {
        writeCache(ProductObservation.notFound(productId, observedAt));
        yield new Eligibility.Ineligible(FailureReason.PRODUCT_NOT_FOUND);
      }
    };
  }

  private Optional<ProductObservation> readCache(UUID productId) {
    try {
      return cache.find(productId);
    } catch (ProductCache.CacheUnavailableException e) {
      degraded("read", e);
      return Optional.empty();
    }
  }

  private void writeCache(ProductObservation observation) {
    try {
      cache.save(observation);
    } catch (ProductCache.CacheUnavailableException e) {
      degraded("write", e);
    }
  }

  private void degraded(String operation, RuntimeException e) {
    meters.counter("cardforge.product.cache", "result", "error").increment();
    log.warn(
        "Product cache degraded on {}; falling back to the catalog: {}", operation, e.toString());
  }
}
