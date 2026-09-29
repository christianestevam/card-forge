package com.rpe.cardforge.cardholder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfTest {

  @Test
  void acceptsDigitsAndFormattedValues() {
    assertThat(Cpf.rejectionRule("52998224725")).isEmpty();
    assertThat(Cpf.of("529.982.247-25").digits()).isEqualTo("52998224725");
  }

  @Test
  void rejectsWrongCheckDigitsRepeatedSequencesAndFormats() {
    assertThat(Cpf.rejectionRule("52998224724")).contains("CPF_CHECK_DIGITS");
    assertThat(Cpf.rejectionRule("11111111111")).contains("CPF_REPEATED_DIGITS");
    assertThat(Cpf.rejectionRule("529.98224725")).contains("CPF_FORMAT");
    assertThat(Cpf.rejectionRule("5299822472")).contains("CPF_FORMAT");
    assertThat(Cpf.rejectionRule(" ")).contains("REQUIRED");
  }

  @Test
  void masksAndNeverPrintsFullNumber() {
    Cpf cpf = Cpf.of("52998224725");
    assertThat(cpf.masked()).isEqualTo("***.982.247-**");
    assertThat(cpf.toString()).doesNotContain("52998224725");
  }
}
