package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import java.util.Optional;
import java.util.UUID;

public interface IssuanceRequestRepository {

  void insert(IssuanceRequest request);

  /** Lê com lock de escrita, para aplicar resultados concorrentes em série. */
  Optional<IssuanceRequest> findByIdForUpdate(UUID id);

  Optional<IssuanceRequest> findByCardholderId(UUID cardholderId);

  void update(IssuanceRequest request);
}
