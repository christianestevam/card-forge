package com.rpe.cardforge.cardholder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardholderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

  private static Cardholder register(String cpf, String name, LocalDate birthDate) {
    return Cardholder.register(
        UUID.randomUUID(), cpf, name, birthDate, UUID.randomUUID(), TODAY, NOW);
  }

  @Test
  void registersActiveCardholderWithNormalizedData() {
    Cardholder c = register("529.982.247-25", "  Maria   da Silva ", LocalDate.of(2000, 1, 1));
    assertThat(c.status()).isEqualTo(CardholderStatus.ACTIVE);
    assertThat(c.cpf().digits()).isEqualTo("52998224725");
    assertThat(c.fullName()).isEqualTo("Maria da Silva");
    assertThat(c.toString()).doesNotContain("52998224725").doesNotContain("2000");
  }

  @Test
  void eighteenthBirthdayIsTheMinimum() {
    assertThat(register("52998224725", "Ana Souza", TODAY.minusYears(18))).isNotNull();
    assertThatThrownBy(() -> register("52998224725", "Ana Souza", TODAY.minusYears(18).plusDays(1)))
        .isInstanceOf(CardholderValidationException.class);
  }

  @Test
  void listsEveryViolationAtOnce() {
    assertThatThrownBy(() -> register("12345678900", "Ana", TODAY.plusDays(1)))
        .isInstanceOfSatisfying(
            CardholderValidationException.class,
            e ->
                assertThat(e.violations())
                    .extracting(Violation::field, Violation::rule)
                    .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("cpf", "CPF_CHECK_DIGITS"),
                        org.assertj.core.groups.Tuple.tuple("fullName", "NAME_REQUIRES_SURNAME"),
                        org.assertj.core.groups.Tuple.tuple("birthDate", "BIRTH_DATE_IN_FUTURE")));
  }

  @Test
  void rejectsTooOldAndTooLongNames() {
    assertThatThrownBy(() -> register("52998224725", "Ana Souza", TODAY.minusYears(121)))
        .isInstanceOfSatisfying(
            CardholderValidationException.class,
            e -> assertThat(e.violations().getFirst().rule()).isEqualTo("MAXIMUM_AGE"));
    assertThatThrownBy(() -> register("52998224725", "A ".repeat(61) + "B", TODAY.minusYears(30)))
        .isInstanceOfSatisfying(
            CardholderValidationException.class,
            e -> assertThat(e.violations().getFirst().rule()).isEqualTo("NAME_LENGTH"));
  }
}
