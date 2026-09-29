package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.application.ProductCatalog.Found;
import com.rpe.cardforge.card.domain.ProductObservation;
import com.rpe.cardforge.card.domain.ProductState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Detalhes do produto para a consulta do cartão. Só leitura: não autoriza emissão e aceita
 * observações de qualquer idade, sinalizadas. Um cartão de produto cancelado continua consultável.
 *
 * <ol>
 *   <li>Cache em qualquer idade: CURRENT até a janela de 5 minutos, STALE depois.
 *   <li>Sem registro utilizável: catálogo (CURRENT), gravando a observação no cache.
 *   <li>Sem nenhum dos dois: UNAVAILABLE.
 * </ol>
 */
@Service
public class ProductDetails {

  private static final Logger log = LoggerFactory.getLogger(ProductDetails.class);

  private final ProductCache cache;
  private final ProductCatalog catalog;
  private final Clock clock;
  private final Duration freshness;

  public ProductDetails(
      ProductCache cache, ProductCatalog catalog, Clock clock, IssuanceProperties properties) {
    this.cache = cache;
    this.catalog = catalog;
    this.clock = clock;
    this.freshness = properties.eligibilityWindow();
  }

  public ProductView forQuery(UUID productId) {
    Optional<ProductObservation> cached = readCache(productId);
    if (cached.isPresent() && cached.get().status() != ProductState.NOT_FOUND) {
      ProductObservation o = cached.get();
      Duration age = Duration.between(o.validatedAt(), clock.instant());
      Availability availability =
          age.compareTo(freshness) <= 0 ? Availability.CURRENT : Availability.STALE;
      return ProductView.of(availability, o);
    }

    Instant observedAt = clock.instant();
    try {
      if (catalog.lookup(productId) instanceof Found found) {
        ProductObservation observation =
            new ProductObservation(
                productId, found.name(), found.bin(), found.status(), observedAt);
        writeCache(observation);
        return ProductView.of(Availability.CURRENT, observation);
      }
    } catch (ProductCatalog.CatalogUnavailableException
        | ProductCatalog.CatalogMisconfiguredException e) {
      log.warn("Product details unavailable for product {}: {}", productId, e.getMessage());
    }
    return ProductView.unavailable();
  }

  private Optional<ProductObservation> readCache(UUID productId) {
    try {
      return cache.find(productId);
    } catch (ProductCache.CacheUnavailableException e) {
      log.warn("Product cache unavailable for card query: {}", e.toString());
      return Optional.empty();
    }
  }

  private void writeCache(ProductObservation observation) {
    try {
      cache.save(observation);
    } catch (ProductCache.CacheUnavailableException e) {
      log.warn("Product cache unavailable for card query: {}", e.toString());
    }
  }

  public enum Availability {
    CURRENT,
    STALE,
    UNAVAILABLE
  }

  /** Produto como visto pela consulta; campos de dados nulos quando UNAVAILABLE. */
  public record ProductView(
      Availability availability,
      Instant observedAt,
      UUID id,
      String name,
      String bin,
      ProductState status) {

    static ProductView of(Availability availability, ProductObservation o) {
      return new ProductView(
          availability, o.validatedAt(), o.productId(), o.name(), o.bin(), o.status());
    }

    static ProductView unavailable() {
      return new ProductView(Availability.UNAVAILABLE, null, null, null, null, null);
    }
  }
}
