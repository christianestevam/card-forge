package com.rpe.cardforge.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rpe.cardforge.card.application.IssuanceProperties;
import com.rpe.cardforge.card.application.ProductCatalog;
import com.rpe.cardforge.card.application.ProductDetails;
import com.rpe.cardforge.card.domain.ProductObservation;
import com.rpe.cardforge.card.domain.ProductState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Semântica do registro único por produto no Redis, incluindo a lápide de cancelamento (TC7). */
class RedisProductCacheIT {

  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:8.8.3-alpine").withExposedPorts(6379);
  static LettuceConnectionFactory connections;
  static RedisProductCache cache;

  static final Instant T0 = Instant.parse("2026-09-29T12:00:00Z");

  @BeforeAll
  static void start() {
    redis.start();
    connections =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
    connections.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(connections);
    template.afterPropertiesSet();
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    cache = new RedisProductCache(template, mapper);
  }

  @AfterAll
  static void stop() {
    connections.destroy();
    redis.stop();
  }

  private static ProductObservation observed(UUID id, ProductState state, Instant at) {
    return new ProductObservation(id, "Gold", "12345678", state, at);
  }

  @Test
  void olderActiveResponseCannotRestoreKnownCancellation() {
    UUID id = UUID.randomUUID();
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0));
    cache.mergeAndGet(observed(id, ProductState.CANCELED, T0.plusSeconds(10)));

    // Resposta ACTIVE de uma consulta iniciada antes do cancelamento, gravada por último.
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(5)));

    assertThat(cache.find(id))
        .get()
        .extracting(ProductObservation::status)
        .isEqualTo(ProductState.CANCELED);
  }

  @Test
  void cancellationIsKeptEvenAgainstNewerActiveObservation() {
    UUID id = UUID.randomUUID();
    cache.mergeAndGet(observed(id, ProductState.CANCELED, T0));

    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(60)));

    assertThat(cache.find(id))
        .get()
        .extracting(ProductObservation::status)
        .isEqualTo(ProductState.CANCELED);
  }

  @Test
  void mergeReturnsTheStoredWinnerWithoutRenewingItsTimestamp() {
    UUID id = UUID.randomUUID();
    ProductObservation canceled = observed(id, ProductState.CANCELED, T0);
    cache.mergeAndGet(canceled);
    assertThat(cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(60))))
        .isEqualTo(canceled);
  }

  @Test
  void notFoundWinsTimestampTiesAgainstActiveInEitherOrder() {
    UUID id = UUID.randomUUID();
    var missing = ProductObservation.notFound(id, T0);
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0));
    assertThat(cache.mergeAndGet(missing)).isEqualTo(missing);
    assertThat(cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0))).isEqualTo(missing);
  }

  @Test
  void olderActiveObservationDoesNotReplaceNewerOne() {
    UUID id = UUID.randomUUID();
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(30)));

    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0));

    assertThat(cache.find(id))
        .get()
        .extracting(ProductObservation::validatedAt)
        .isEqualTo(T0.plusSeconds(30));
  }

  @Test
  void newerActiveObservationRefreshesValidatedAt() {
    UUID id = UUID.randomUUID();
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0));

    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(30)));

    assertThat(cache.find(id))
        .get()
        .extracting(ProductObservation::validatedAt)
        .isEqualTo(T0.plusSeconds(30));
  }

  @Test
  void cancellationReceivedLastWinsEvenWithAnOlderRequestTimestamp() {
    UUID id = UUID.randomUUID();
    cache.mergeAndGet(observed(id, ProductState.ACTIVE, T0.plusSeconds(1)));
    assertThat(cache.mergeAndGet(observed(id, ProductState.CANCELED, T0)).status())
        .isEqualTo(ProductState.CANCELED);
    assertThat(cache.find(id))
        .get()
        .extracting(ProductObservation::status)
        .isEqualTo(ProductState.CANCELED);
  }

  @Test
  void queryUsesTheCancellationThatWinsWhileTheCatalogCallIsInFlight() {
    UUID id = UUID.randomUUID();
    ProductCatalog catalog =
        productId -> {
          cache.mergeAndGet(observed(id, ProductState.CANCELED, T0.plusSeconds(1)));
          return new ProductCatalog.Found(id, "Gold", "12345678", ProductState.ACTIVE);
        };
    var properties =
        new IssuanceProperties(
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            20,
            "requested",
            "dlq",
            "completed");
    var details =
        new ProductDetails(
            cache, catalog, Clock.fixed(T0.plusSeconds(2), ZoneOffset.UTC), properties);
    assertThat(details.forQuery(id).status()).isEqualTo(ProductState.CANCELED);
  }
}
