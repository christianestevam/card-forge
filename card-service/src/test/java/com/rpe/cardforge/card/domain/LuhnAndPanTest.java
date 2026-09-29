package com.rpe.cardforge.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LuhnAndPanTest {

  @Test
  void computesKnownCheckDigits() {
    assertThat(Luhn.checkDigit("7992739871")).isEqualTo(3);
    assertThat(Luhn.isValid("79927398713")).isTrue();
    assertThat(Luhn.isValid("4111111111111111")).isTrue();
    assertThat(Luhn.isValid("4111111111111112")).isFalse();
    assertThat(Luhn.isValid("41111111a1111111")).isFalse();
  }

  @Test
  void generatesBinPlusSevenDigitsPlusLuhn() {
    AtomicInteger next = new AtomicInteger();
    PanGenerator generator = new PanGenerator(() -> next.getAndIncrement() % 10);

    Pan pan = generator.generate("12345678");

    assertThat(pan.digits()).hasSize(16).startsWith("12345678").contains("0123456");
    assertThat(Luhn.isValid(pan.digits())).isTrue();
    assertThat(pan.lastFour()).isEqualTo(pan.digits().substring(12));
  }

  @Test
  void panNeverPrintsFullNumber() {
    Pan pan = new Pan("4111111111111111");
    assertThat(pan.toString()).doesNotContain("4111111111111111").endsWith("1111]");
  }

  @Test
  void rejectsInvalidBinAndPan() {
    PanGenerator generator = new PanGenerator(() -> 1);
    assertThatThrownBy(() -> generator.generate("1234")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pan("4111111111111112"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cardIsActiveAndValidForFiveYearsWithoutFullPan() {
    Instant now = Instant.parse("2026-09-29T23:30:00Z");
    Card card =
        Card.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "hmac",
            new Pan("4111111111111111"),
            now);
    assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
    assertThat(card.expirationDate()).isEqualTo(YearMonth.of(2031, 9));
    assertThat(card.panLastFour()).isEqualTo("1111");
    assertThat(card.toString()).doesNotContain("4111111111111111");
  }

  @Test
  void decisionRequiresConsistentFields() {
    Instant now = Instant.now();
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> new IssuanceDecision(id, IssuanceStatus.ISSUED, null, null, now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new IssuanceDecision(id, IssuanceStatus.FAILED, UUID.randomUUID(), null, now))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
