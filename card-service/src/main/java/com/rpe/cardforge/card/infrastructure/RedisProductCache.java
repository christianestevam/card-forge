package com.rpe.cardforge.card.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.card.application.ProductCache;
import com.rpe.cardforge.card.domain.ProductObservation;
import com.rpe.cardforge.card.domain.ProductState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache explícito de observações de produto: registro único por produto em {@code
 * cardforge:product:v1:{id}}, JSON, TTL físico de 24 h. A leitura nunca renova {@code
 * validatedAt}.
 */
@Component
class RedisProductCache implements ProductCache {

  private static final Logger log = LoggerFactory.getLogger(RedisProductCache.class);
  static final String KEY_PREFIX = "cardforge:product:v1:";
  static final Duration TTL = Duration.ofHours(24);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  RedisProductCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ProductObservation> find(UUID productId) {
    String json;
    try {
      json = redis.opsForValue().get(KEY_PREFIX + productId);
    } catch (DataAccessException e) {
      throw new CacheUnavailableException("Redis read failed", e);
    }
    if (json == null) {
      return Optional.empty();
    }
    try {
      CachedProduct cached = objectMapper.readValue(json, CachedProduct.class);
      return Optional.of(
          new ProductObservation(
              cached.productId(), cached.name(), cached.bin(), cached.status(), cached.validatedAt()));
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("Ignoring unreadable cache entry for product {}", productId);
      return Optional.empty();
    }
  }

  @Override
  public void save(ProductObservation o) {
    try {
      String json =
          objectMapper.writeValueAsString(
              new CachedProduct(o.productId(), o.name(), o.bin(), o.status(), o.validatedAt()));
      redis.opsForValue().set(KEY_PREFIX + o.productId(), json, TTL);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize product observation", e);
    } catch (DataAccessException e) {
      throw new CacheUnavailableException("Redis write failed", e);
    }
  }

  @Override
  public void evict(UUID productId) {
    try {
      redis.delete(KEY_PREFIX + productId);
    } catch (DataAccessException e) {
      throw new CacheUnavailableException("Redis delete failed", e);
    }
  }

  record CachedProduct(
      UUID productId, String name, String bin, ProductState status, Instant validatedAt) {}
}
