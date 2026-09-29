package com.rpe.cardforge.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardStatusTransitionTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-09-29T10:00:00Z");
  private static final Instant LATER = ISSUED_AT.plusSeconds(60);

  private static Card activeCard() {
    return Card.issue(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "hmac",
        new Pan("4111111111111111"),
        ISSUED_AT);
  }

  @Test
  void blockUnblockAndCancelFollowTheLifecycle() {
    Card blocked = activeCard().block(LATER);
    assertThat(blocked.status()).isEqualTo(CardStatus.BLOCKED);
    assertThat(blocked.updatedAt()).isEqualTo(LATER);
    assertThat(blocked.createdAt()).isEqualTo(ISSUED_AT);

    assertThat(blocked.unblock(LATER).status()).isEqualTo(CardStatus.ACTIVE);
    assertThat(blocked.cancel(LATER).status()).isEqualTo(CardStatus.CANCELED);
    assertThat(activeCard().cancel(LATER).status()).isEqualTo(CardStatus.CANCELED);
  }

  @Test
  void requestForCurrentStatusChangesNothing() {
    Card active = activeCard();
    assertThat(active.unblock(LATER)).isSameAs(active);
    Card blocked = active.block(LATER);
    assertThat(blocked.block(LATER.plusSeconds(1))).isSameAs(blocked);
    Card canceled = active.cancel(LATER);
    assertThat(canceled.cancel(LATER.plusSeconds(1))).isSameAs(canceled);
  }

  @Test
  void canceledIsTerminal() {
    Card canceled = activeCard().cancel(LATER);
    assertThatThrownBy(() -> canceled.block(LATER))
        .isInstanceOf(InvalidStatusTransitionException.class);
    assertThatThrownBy(() -> canceled.unblock(LATER))
        .isInstanceOf(InvalidStatusTransitionException.class);
  }
}
