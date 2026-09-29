package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.Cardholder;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardholderQueryService {

  private final CardholderRepository cardholders;

  public CardholderQueryService(CardholderRepository cardholders) {
    this.cardholders = cardholders;
  }

  @Transactional(readOnly = true)
  public Cardholder get(UUID id) {
    return cardholders.findById(id).orElseThrow(() -> new CardholderNotFoundException(id));
  }
}
