package com.rpe.cardforge.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class UniqueConstraintsTest {

  private static SQLException duplicate(String constraint) {
    return new SQLException(
        "ERROR: duplicate key value violates unique constraint \"" + constraint + "\"", "23505");
  }

  @Test
  void matchesNamedConstraintAnywhereInCauseChain() {
    RuntimeException wrapped = new RuntimeException(new RuntimeException(duplicate("uk_a")));
    assertThat(UniqueConstraints.isViolation(wrapped, "uk_a")).isTrue();
  }

  @Test
  void ignoresOtherConstraintsAndOtherErrors() {
    assertThat(UniqueConstraints.isViolation(duplicate("uk_b"), "uk_a")).isFalse();
    assertThat(UniqueConstraints.isViolation(new SQLException("x", "23503"), "uk_a")).isFalse();
    assertThat(UniqueConstraints.isViolation(new RuntimeException("boom"), "uk_a")).isFalse();
  }
}
