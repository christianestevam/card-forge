package com.rpe.cardforge.cardholder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardholderStatusTransitionTest {

  private static final Instant CREATED = Instant.parse("2026-09-29T10:00:00Z");
  private static final Instant LATER = CREATED.plusSeconds(60);

  private static Cardholder active() {
    return Cardholder.register(
        UUID.randomUUID(),
        "52998224725",
        "Maria da Silva",
        LocalDate.of(1990, 1, 1),
        UUID.randomUUID(),
        LocalDate.of(2026, 9, 29),
        CREATED);
  }

  @Test
  void followsTheLifecycleAndUpdatesOnlyStatusAndUpdatedAt() {
    Cardholder blocked = active().block(LATER);
    assertThat(blocked.status()).isEqualTo(CardholderStatus.BLOCKED);
    assertThat(blocked.updatedAt()).isEqualTo(LATER);
    assertThat(blocked.createdAt()).isEqualTo(CREATED);
    assertThat(blocked.unblock(LATER).status()).isEqualTo(CardholderStatus.ACTIVE);
    assertThat(blocked.cancel(LATER).status()).isEqualTo(CardholderStatus.CANCELED);
  }

  @Test
  void requestForCurrentStatusChangesNothing() {
    Cardholder active = active();
    assertThat(active.unblock(LATER)).isSameAs(active);
    Cardholder canceled = active.cancel(LATER);
    assertThat(canceled.cancel(LATER.plusSeconds(1))).isSameAs(canceled);
  }

  @Test
  void canceledIsTerminal() {
    Cardholder canceled = active().cancel(LATER);
    assertThatThrownBy(() -> canceled.block(LATER))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThatThrownBy(() -> canceled.unblock(LATER))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }
}
