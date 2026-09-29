package com.rpe.cardforge.cardholder.web;

import com.rpe.cardforge.cardholder.application.CardholderQueryService;
import com.rpe.cardforge.cardholder.application.CardholderStatusService;
import com.rpe.cardforge.cardholder.application.OverviewService;
import com.rpe.cardforge.cardholder.application.RegisterCardholderCommand;
import com.rpe.cardforge.cardholder.application.RegistrationReceipt;
import com.rpe.cardforge.cardholder.application.RegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cardholders")
class CardholderController {

  private final RegistrationService registration;
  private final CardholderQueryService cardholders;
  private final OverviewService overview;
  private final CardholderStatusService status;

  CardholderController(
      RegistrationService registration,
      CardholderQueryService cardholders,
      OverviewService overview,
      CardholderStatusService status) {
    this.registration = registration;
    this.cardholders = cardholders;
    this.overview = overview;
    this.status = status;
  }

  /** 202: aceito e rastreável; o desfecho da emissão aparece no {@code /overview}. */
  @PostMapping
  ResponseEntity<RegistrationReceipt> register(
      @Valid @RequestBody RegisterCardholderRequest request) {
    RegistrationReceipt receipt =
        registration.register(
            new RegisterCardholderCommand(
                request.cpf(), request.fullName(), request.birthDate(), request.productId()));
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/cardholders/" + receipt.cardholderId() + "/overview"))
        .body(receipt);
  }

  @GetMapping("/{cardholderId}")
  CardholderResponse get(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(cardholders.get(cardholderId));
  }

  @GetMapping("/{cardholderId}/overview")
  OverviewResponse overview(@PathVariable UUID cardholderId) {
    return OverviewResponse.from(overview.get(cardholderId));
  }

  /** ACTIVE -> BLOCKED; 200 sem mudança se já estiver BLOCKED; 409 a partir de CANCELED. */
  @PostMapping("/{cardholderId}/block")
  CardholderResponse block(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.block(cardholderId));
  }

  /** BLOCKED -> ACTIVE; 200 sem mudança se já estiver ACTIVE; 409 a partir de CANCELED. */
  @PostMapping("/{cardholderId}/unblock")
  CardholderResponse unblock(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.unblock(cardholderId));
  }

  /** ACTIVE ou BLOCKED -> CANCELED (terminal); 200 sem mudança se já estiver CANCELED. */
  @PostMapping("/{cardholderId}/cancel")
  CardholderResponse cancel(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.cancel(cardholderId));
  }
}
