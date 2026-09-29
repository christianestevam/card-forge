package com.rpe.cardforge.cardholder.application;

import com.rpe.cardforge.cardholder.application.ProductCatalog.CatalogMisconfiguredException;
import com.rpe.cardforge.cardholder.application.ProductCatalog.CatalogUnavailableException;
import com.rpe.cardforge.cardholder.application.ProductCatalog.Found;
import com.rpe.cardforge.cardholder.application.ProductCatalog.NotFound;
import com.rpe.cardforge.cardholder.domain.Cardholder;
import com.rpe.cardforge.cardholder.domain.IssuanceRequest;
import com.rpe.cardforge.platform.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro do portador com solicitação de emissão.
 *
 * <ol>
 *   <li>Valida a entrada (todas as violações de uma vez).
 *   <li>Consulta o catálogo fora da transação: inexistente ou cancelado recusa (BR3.3); falha
 *       técnica ou de configuração aceita e deixa a validação para a emissão (BR3.4).
 *   <li>Grava portador ACTIVE, solicitação PENDING e evento no outbox na mesma transação.
 * </ol>
 *
 * Nem o catálogo nem a SQS indisponíveis impedem o cadastro (BR3.5).
 */
@Service
public class RegistrationService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

  private final CardholderRepository cardholders;
  private final IssuanceRequestRepository requests;
  private final ProductCatalog catalog;
  private final OutboxWriter outbox;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final CardholderProperties properties;

  public RegistrationService(
      CardholderRepository cardholders,
      IssuanceRequestRepository requests,
      ProductCatalog catalog,
      OutboxWriter outbox,
      TransactionTemplate transaction,
      Clock clock,
      CardholderProperties properties) {
    this.cardholders = cardholders;
    this.requests = requests;
    this.catalog = catalog;
    this.outbox = outbox;
    this.transaction = transaction;
    this.clock = clock;
    this.properties = properties;
  }

  public RegistrationReceipt register(RegisterCardholderCommand command) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    Cardholder cardholder =
        Cardholder.register(
            UUID.randomUUID(),
            command.cpf(),
            command.fullName(),
            command.birthDate(),
            command.productId(),
            today,
            now);

    checkProduct(cardholder.productId());

    IssuanceRequest request =
        IssuanceRequest.pending(UUID.randomUUID(), cardholder.id(), cardholder.productId(), now);
    transaction.executeWithoutResult(
        status -> {
          cardholders.insert(cardholder);
          requests.insert(request);
          outbox.write(
              properties.requestedQueue(),
              IssuanceRequestedEvent.EVENT_TYPE,
              IssuanceRequestedEvent.EVENT_VERSION,
              request.id(),
              new IssuanceRequestedEvent(request.id(), cardholder.id(), cardholder.productId()));
        });
    log.info(
        "Cardholder {} registered; issuance request {} pending", cardholder.id(), request.id());
    return new RegistrationReceipt(cardholder.id(), request.id());
  }

  private void checkProduct(UUID productId) {
    try {
      switch (catalog.lookup(productId)) {
        case NotFound notFound -> throw new ProductRejectedException(false);
        case Found found when found.isCanceled() -> throw new ProductRejectedException(true);
        case Found found -> {
          // ACTIVE: segue. A decisão final de emissão é do card-service (BR4.1).
        }
      }
    } catch (CatalogUnavailableException e) {
      log.warn(
          "Product catalog unavailable; registration accepted and product check deferred to"
              + " issuance: {}",
          e.getMessage());
    } catch (CatalogMisconfiguredException e) {
      log.error(
          "ALERT configuration: product catalog call rejected or out of contract; registration"
              + " accepted: {}",
          e.getMessage());
    }
  }
}
