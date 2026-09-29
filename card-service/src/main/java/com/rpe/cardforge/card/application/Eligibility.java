package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.FailureReason;
import java.time.Instant;

/** Resposta de "o produto pode emitir agora?". */
public sealed interface Eligibility {

  /**
   * Produto ACTIVE, observado em {@code validatedAt}. A idade é conferida de novo no ponto da
   * emissão, dentro da transação.
   */
  record Eligible(String bin, Instant validatedAt) implements Eligibility {}

  record Ineligible(FailureReason reason) implements Eligibility {}
}
