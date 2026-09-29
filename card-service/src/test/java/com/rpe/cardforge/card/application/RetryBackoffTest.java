package com.rpe.cardforge.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryBackoffTest {

  private static RetryBackoff withRandom(double r) {
    return new RetryBackoff(30, 300, () -> r);
  }

  @Test
  void startsAtThirtySecondsAndDoubles() {
    RetryBackoff noJitter = withRandom(0.5);
    assertThat(noJitter.delaySeconds(1)).isEqualTo(30);
    assertThat(noJitter.delaySeconds(2)).isEqualTo(60);
    assertThat(noJitter.delaySeconds(3)).isEqualTo(120);
    assertThat(noJitter.delaySeconds(4)).isEqualTo(240);
  }

  @Test
  void jitterStaysWithinTwentyPercent() {
    assertThat(withRandom(0.0).delaySeconds(1)).isEqualTo(24);
    assertThat(withRandom(0.999999).delaySeconds(1)).isEqualTo(36);
  }

  @Test
  void hardCapIsAppliedAfterJitter() {
    assertThat(withRandom(0.999999).delaySeconds(5)).isEqualTo(300);
    assertThat(withRandom(0.999999).delaySeconds(29)).isEqualTo(300);
    assertThat(withRandom(0.0).delaySeconds(29)).isEqualTo(300);
  }

  @Test
  void unknownReceiveCountIsTreatedAsFirst() {
    assertThat(withRandom(0.5).delaySeconds(0)).isEqualTo(30);
  }
}
