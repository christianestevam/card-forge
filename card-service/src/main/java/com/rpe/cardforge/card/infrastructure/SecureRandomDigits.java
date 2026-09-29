package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.domain.RandomDigits;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
class SecureRandomDigits implements RandomDigits {

  private final SecureRandom random = new SecureRandom();

  @Override
  public int nextDigit() {
    return random.nextInt(10);
  }
}
