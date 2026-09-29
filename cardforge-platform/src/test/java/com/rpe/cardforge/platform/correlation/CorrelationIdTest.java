package com.rpe.cardforge.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTest {

  @Test
  void keepsSafeIncomingValue() {
    assertThat(CorrelationId.sanitize("abc-123_X.y")).isEqualTo("abc-123_X.y");
  }

  @Test
  void replacesUnsafeOrMissingValue() {
    assertThat(CorrelationId.sanitize("bad value\nforged")).isNotEqualTo("bad value\nforged");
    assertThat(CorrelationId.sanitize(null)).hasSize(36);
    assertThat(CorrelationId.sanitize("x".repeat(65))).hasSize(36);
  }

  @Test
  void scopeRestoresPreviousValue() {
    MDC.put(CorrelationId.MDC_KEY, "outer");
    try (CorrelationId.Scope ignored = CorrelationId.open("inner")) {
      assertThat(CorrelationId.current()).isEqualTo("inner");
    }
    assertThat(CorrelationId.current()).isEqualTo("outer");
    MDC.clear();
  }
}
