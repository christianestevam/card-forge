package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.domain.IssuanceDecision;
import java.util.Optional;
import java.util.UUID;

/** Resultados terminais das solicitações ({@code issuance_processing}). */
public interface IssuanceProcessingRepository {

  Optional<IssuanceDecision> find(UUID issuanceRequestId);

  /**
   * Grava o desfecho se a solicitação ainda não tem um ({@code ON CONFLICT DO NOTHING}). Se outra
   * transação estiver gravando a mesma solicitação, espera o commit dela.
   *
   * @return false se a solicitação já tinha desfecho
   */
  boolean insertIfAbsent(IssuanceDecision decision);
}
