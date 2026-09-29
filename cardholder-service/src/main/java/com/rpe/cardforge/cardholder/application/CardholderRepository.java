package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.Cardholder;
import java.util.Optional;
import java.util.UUID;

public interface CardholderRepository {

  /**
   * @throws CpfAlreadyRegisteredException se o CPF já existe (constraint no banco)
   */
  void insert(Cardholder cardholder);

  Optional<Cardholder> findById(UUID id);

  /** Lê com lock de escrita, para aplicar transições concorrentes em série. */
  Optional<Cardholder> findByIdForUpdate(UUID id);

  void updateStatus(Cardholder cardholder);
}
