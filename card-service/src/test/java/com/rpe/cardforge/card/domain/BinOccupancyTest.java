package com.rpe.cardforge.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BinOccupancyTest {

  @Test
  void alertsFromSeventyPercentOfTheBinSpace() {
    assertThat(new BinOccupancy("12345678", 6_999_999).requiresAlert()).isFalse();
    assertThat(new BinOccupancy("12345678", 7_000_000).requiresAlert()).isTrue();
    assertThat(new BinOccupancy("12345678", 7_000_000).ratio()).isEqualTo(0.7);
  }

  @Test
  void panExposesItsBin() {
    assertThat(new Pan("4111111111111111").bin()).isEqualTo("41111111");
  }
}
