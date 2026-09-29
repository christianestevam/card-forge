package com.rpe.cardforge.card.application;

import com.rpe.cardforge.card.application.CardRepository.NonCanceledCardConflictException;
import com.rpe.cardforge.card.domain.Card;
import com.rpe.cardforge.card.domain.FailureReason;
import com.rpe.cardforge.card.domain.IssuanceDecision;
import com.rpe.cardforge.card.domain.Pan;
import com.rpe.cardforge.card.domain.PanGenerator;
import com.rpe.cardforge.platform.outbox.OutboxWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decide cada solicitação de emissão no máximo uma vez (BR4.2).
 *
 * <ul>
 *   <li>Solicitação já decidida: nunca reavalia; recoloca o resultado persistido no outbox.
 *   <li>Elegibilidade do produto: fora da transação (nenhuma chamada remota dentro dela).
 *   <li>Recusa de negócio: resultado FAILED e outbox na mesma transação, sem retentativa.
 *   <li>Emissão: resultado ISSUED, cartão e outbox na mesma transação.
 *   <li>Falha técnica: rollback, nada gravado; a SQS entrega de novo.
 * </ul>
 */
@Service
public class IssuanceProcessor {

  private static final Logger log = LoggerFactory.getLogger(IssuanceProcessor.class);

  private final IssuanceProcessingRepository processing;
  private final CardRepository cards;
  private final ProductEligibility eligibility;
  private final PanGenerator panGenerator;
  private final PanHasher panHasher;
  private final OutboxWriter outbox;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final IssuanceProperties properties;
  private final MeterRegistry meters;

  public IssuanceProcessor(
      IssuanceProcessingRepository processing,
      CardRepository cards,
      ProductEligibility eligibility,
      PanGenerator panGenerator,
      PanHasher panHasher,
      OutboxWriter outbox,
      TransactionTemplate transaction,
      Clock clock,
      IssuanceProperties properties,
      MeterRegistry meters) {
    this.processing = processing;
    this.cards = cards;
    this.eligibility = eligibility;
    this.panGenerator = panGenerator;
    this.panHasher = panHasher;
    this.outbox = outbox;
    this.transaction = transaction;
    this.clock = clock;
    this.properties = properties;
    this.meters = meters;
  }

  public void process(IssuanceRequested request) {
    try {
      decide(request);
    } catch (NonCanceledCardConflictException e) {
      // Outra solicitação criou um cartão para o mesmo portador e produto entre a verificação e a
      // inserção. A transação foi desfeita; a decisão recomeça do início e vê o cartão existente.
      log.info("Unique card conflict for request {}; deciding again", request.issuanceRequestId());
      decide(request);
    }
  }

  private void decide(IssuanceRequested request) {
    Optional<IssuanceDecision> existing = processing.find(request.issuanceRequestId());
    if (existing.isPresent()) {
      republish(existing.get());
      return;
    }

    Eligibility result = eligibility.check(request.productId());

    transaction.executeWithoutResult(
        status -> {
          Instant now = clock.instant();
          if (result instanceof Eligibility.Ineligible ineligible) {
            record(IssuanceDecision.failed(request.issuanceRequestId(), ineligible.reason(), now));
            return;
          }
          if (cards.existsNonCanceled(request.cardholderId(), request.productId())) {
            record(
                IssuanceDecision.failed(
                    request.issuanceRequestId(),
                    FailureReason.NON_CANCELED_CARD_ALREADY_EXISTS,
                    now));
            return;
          }
          UUID cardId = UUID.randomUUID();
          IssuanceDecision decision =
              IssuanceDecision.issued(request.issuanceRequestId(), cardId, now);
          if (!processing.insertIfAbsent(decision)) {
            republishExisting(request.issuanceRequestId());
            return;
          }
          issueCard(cardId, request, ((Eligibility.Eligible) result).bin(), now);
          publish(decision);
          countDecision(decision);
        });
  }

  /** Grava o desfecho de recusa ou, se outra entrega já decidiu, republica o existente. */
  private void record(IssuanceDecision decision) {
    if (processing.insertIfAbsent(decision)) {
      publish(decision);
      countDecision(decision);
    } else {
      republishExisting(decision.issuanceRequestId());
    }
  }

  private void issueCard(UUID cardId, IssuanceRequested request, String bin, Instant now) {
    for (int attempt = 1; attempt <= properties.maxPanAttempts(); attempt++) {
      Pan pan = panGenerator.generate(bin);
      Card card =
          Card.issue(
              cardId,
              request.cardholderId(),
              request.productId(),
              request.issuanceRequestId(),
              panHasher.hmac(pan),
              pan,
              now);
      if (cards.insertIfPanFree(card)) {
        return;
      }
      meters.counter("cardforge.pan.collisions").increment();
      log.warn("PAN collision on attempt {} for BIN {}", attempt, bin);
    }
    log.error(
        "ALERT PAN space: {} consecutive collisions for BIN {}", properties.maxPanAttempts(), bin);
    throw new TransientIssuanceException("Could not generate a free PAN for BIN " + bin);
  }

  private void republishExisting(UUID issuanceRequestId) {
    IssuanceDecision existing =
        processing
            .find(issuanceRequestId)
            .orElseThrow(
                () -> new IllegalStateException("Decision vanished for " + issuanceRequestId));
    publish(existing);
  }

  private void republish(IssuanceDecision decision) {
    log.info("Request {} already decided; republishing result", decision.issuanceRequestId());
    transaction.executeWithoutResult(status -> publish(decision));
  }

  private void publish(IssuanceDecision decision) {
    outbox.write(
        properties.completedQueue(),
        IssuanceCompleted.EVENT_TYPE,
        IssuanceCompleted.EVENT_VERSION,
        decision.issuanceRequestId(),
        IssuanceCompleted.from(decision));
  }

  private void countDecision(IssuanceDecision decision) {
    log.info(
        "Issuance request {} decided {}{}",
        decision.issuanceRequestId(),
        decision.status(),
        decision.failureReason() == null ? "" : " (" + decision.failureReason() + ")");
    meters
        .counter(
            "cardforge.issuance.decisions",
            "status",
            decision.status().name(),
            "reason",
            decision.failureReason() == null ? "none" : decision.failureReason().name())
        .increment();
  }
}
