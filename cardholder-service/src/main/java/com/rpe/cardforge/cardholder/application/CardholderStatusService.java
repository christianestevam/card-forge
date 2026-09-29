package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.Cardholder;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transições de status do portador (FR7.1). Sem histórico de transições nesta release (desvio D5):
 * a mudança só atualiza {@code updatedAt}. O status do portador não afeta a emissão pendente na R1
 * (FR4.8) nem os cartões já emitidos (sem cascata).
 */
@Service
public class CardholderStatusService {

  private final CardholderRepository cardholders;
  private final Clock clock;

  public CardholderStatusService(CardholderRepository cardholders, Clock clock) {
    this.cardholders = cardholders;
    this.clock = clock;
  }

  @Transactional
  public Cardholder block(UUID id) {
    return change(id, (c, now) -> c.block(now));
  }

  @Transactional
  public Cardholder unblock(UUID id) {
    return change(id, (c, now) -> c.unblock(now));
  }

  @Transactional
  public Cardholder cancel(UUID id) {
    return change(id, (c, now) -> c.cancel(now));
  }

  private Cardholder change(UUID id, BiFunction<Cardholder, Instant, Cardholder> transition) {
    Cardholder current =
        cardholders.findByIdForUpdate(id).orElseThrow(() -> new CardholderNotFoundException(id));
    Cardholder next = transition.apply(current, clock.instant());
    if (next != current) {
      cardholders.updateStatus(next);
    }
    return next;
  }
}
