package com.rpe.cardforge.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BinTest {

  @Test
  void acceptsEightDigits() {
    assertThat(new Bin("12345678").value()).isEqualTo("12345678");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"1234567", "123456789", "1234567a", " 12345678", ""})
  void rejectsAnythingElse(String value) {
    assertThatThrownBy(() -> new Bin(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void newProductIsActive() {
    Instant now = Instant.parse("2026-09-29T10:00:00Z");
    Product product = Product.create(UUID.randomUUID(), "Gold", null, new Bin("12345678"), now);
    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.createdAt()).isEqualTo(now).isEqualTo(product.updatedAt());
  }

  @Test
  void cancelIsTerminalAndIdempotent() {
    Instant now = Instant.parse("2026-09-29T10:00:00Z");
    Product active = Product.create(UUID.randomUUID(), "Gold", null, new Bin("12345678"), now);

    Product canceled = active.cancel(now.plusSeconds(60));

    assertThat(canceled.status()).isEqualTo(ProductStatus.CANCELED);
    assertThat(canceled.updatedAt()).isEqualTo(now.plusSeconds(60));
    assertThat(canceled.bin()).isEqualTo(active.bin());
    assertThat(canceled.cancel(now.plusSeconds(120))).isSameAs(canceled);
  }
}
