package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import com.rpe.cardforge.cardholder.domain.IssuanceStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica o resultado da emissão, idempotente por estado (FR5.1). */
@Service
public class IssuanceResultService {

  private static final Logger log = LoggerFactory.getLogger(IssuanceResultService.class);

  private final IssuanceRequestRepository requests;
  private final Clock clock;

  public IssuanceResultService(IssuanceRequestRepository requests, Clock clock) {
    this.requests = requests;
    this.clock = clock;
  }

  /**
   * @throws UnprocessableResultException se a solicitação é desconhecida ou o resultado contradiz o
   *     desfecho já aplicado (nada é alterado)
   */
  @Transactional
  public void apply(IssuanceCompletedEvent event) {
    IssuanceRequest request =
        requests
            .findByIdForUpdate(event.issuanceRequestId())
            .orElseThrow(
                () ->
                    new UnprocessableResultException(
                        "Unknown issuance request " + event.issuanceRequestId()));

    IssuanceRequest.Outcome outcome =
        event.status() == IssuanceStatus.ISSUED
            ? request.applyIssued(event.cardId(), clock.instant())
            : request.applyFailed(event.failureReason(), clock.instant());

    switch (outcome) {
      case APPLIED -> {
        requests.update(request);
        log.info("Issuance request {} is now {}", request.id(), request.status());
      }
      case ALREADY_APPLIED ->
          log.info(
              "Issuance request {} already {}; duplicate ignored", request.id(), request.status());
      case CONTRADICTORY ->
          throw new UnprocessableResultException(
              "Contradictory result "
                  + event.status()
                  + " for issuance request "
                  + request.id()
                  + " already "
                  + request.status());
    }
  }
}
