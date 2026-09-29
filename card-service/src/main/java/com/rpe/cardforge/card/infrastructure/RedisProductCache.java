package com.rpe.cardforge.card.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.card.application.ProductCache;
import com.rpe.cardforge.card.domain.ProductObservation;
import com.rpe.cardforge.card.domain.ProductState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Cache explícito de observações de produto: registro único por produto em {@code
 * cardforge:product:v1:{id}}, JSON, TTL físico de 24 h. A leitura nunca renova {@code validatedAt}.
 * A gravação é condicional e atômica (script Lua): vence a observação mais recente, e uma
 * observação CANCELED (lápide) nunca é substituída.
 */
@Component
class RedisProductCache implements ProductCache {

  private static final Logger log = LoggerFactory.getLogger(RedisProductCache.class);
  static final String KEY_PREFIX = "cardforge:product:v1:";
  static final Duration TTL = Duration.ofHours(24);

  /**
   * KEYS[1] = chave; ARGV[1] = JSON novo; ARGV[2] = validatedAt em epoch millis; ARGV[3] = TTL em
   * millis. Retorna 1 se gravou, 0 se descartou.
   */
  private static final RedisScript<Long> SAVE_IF_NEWEST =
      RedisScript.of(
          """
          local current = redis.call('GET', KEYS[1])
          if current then
            local ok, obj = pcall(cjson.decode, current)
            if ok and type(obj) == 'table' then
              if obj.status == 'CANCELED' then return 0 end
              local currentAt = tonumber(obj.validatedAtMillis)
              if currentAt and currentAt > tonumber(ARGV[2]) then return 0 end
            end
          end
          redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
          return 1
          """,
          Long.class);

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
              cached.productId(),
              cached.name(),
              cached.bin(),
              cached.status(),
              cached.validatedAt()));
    } catch (JsonProcessingException | RuntimeException e) {
      log.warn("Ignoring unreadable cache entry for product {}", productId);
      return Optional.empty();
    }
  }

  @Override
  public boolean save(ProductObservation o) {
    String json;
    try {
      json =
          objectMapper.writeValueAsString(
              new CachedProduct(
                  o.productId(),
                  o.name(),
                  o.bin(),
                  o.status(),
                  o.validatedAt(),
                  o.validatedAt().toEpochMilli()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize product observation", e);
    }
    try {
      Long written =
          redis.execute(
              SAVE_IF_NEWEST,
              List.of(KEY_PREFIX + o.productId()),
              json,
              String.valueOf(o.validatedAt().toEpochMilli()),
              String.valueOf(TTL.toMillis()));
      boolean saved = written != null && written == 1L;
      if (!saved) {
        log.info(
            "Kept existing cache entry for product {}: newer or definitive observation",
            o.productId());
      }
      return saved;
    } catch (DataAccessException e) {
      throw new CacheUnavailableException("Redis write failed", e);
    }
  }

  record CachedProduct(
      UUID productId,
      String name,
      String bin,
      ProductState status,
      Instant validatedAt,
      Long validatedAtMillis) {}
}
