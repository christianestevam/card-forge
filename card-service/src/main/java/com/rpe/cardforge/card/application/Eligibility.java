package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.FailureReason;

/** Resposta de "o produto pode emitir agora?". */
public sealed interface Eligibility {

  record Eligible(String bin) implements Eligibility {}

  record Ineligible(FailureReason reason) implements Eligibility {}
}
