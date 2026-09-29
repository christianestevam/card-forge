package com.rpe.cardforge.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpe.cardforge.card.domain.Pan;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HmacPanHasherTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void isDeterministicAndDoesNotContainPan() {
    HmacPanHasher hasher = new HmacPanHasher(KEY + "\n");
    Pan pan = new Pan("4111111111111111");
    assertThat(hasher.hmac(pan)).isEqualTo(hasher.hmac(pan)).hasSize(64).doesNotContain("4111");
  }

  @Test
  void differentKeysGiveDifferentHmacs() {
    byte[] other = new byte[32];
    other[0] = 1;
    Pan pan = new Pan("4111111111111111");
    assertThat(new HmacPanHasher(KEY).hmac(pan))
        .isNotEqualTo(new HmacPanHasher(Base64.getEncoder().encodeToString(other)).hmac(pan));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"not base64 !!", "c2hvcnQ="})
  void refusesMissingOrMalformedKey(String key) {
    assertThatThrownBy(() -> new HmacPanHasher(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PAN HMAC key");
  }
}
