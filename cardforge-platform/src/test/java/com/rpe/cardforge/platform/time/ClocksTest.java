package com.rpe.cardforge.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.RepeatedTest;

class ClocksTest {

  @RepeatedTest(20)
  void instantsHaveAtMostMicrosecondPrecision() {
    Clock clock = Clocks.systemUtcMicros();
    assertThat(clock.instant().getNano() % 1_000).isZero();
  }
}
