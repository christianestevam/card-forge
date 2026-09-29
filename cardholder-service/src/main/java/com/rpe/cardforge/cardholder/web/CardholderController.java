package com.rpe.cardforge.cardholder.web;

import static com.rpe.cardforge.platform.openapi.ProblemResponsesCustomizer.PROBLEM_SCHEMA;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.rpe.cardforge.cardholder.application.CardholderQueryService;
import com.rpe.cardforge.cardholder.application.CardholderStatusService;
import com.rpe.cardforge.cardholder.application.OverviewService;
import com.rpe.cardforge.cardholder.application.RegisterCardholderCommand;
import com.rpe.cardforge.cardholder.application.RegistrationReceipt;
import com.rpe.cardforge.cardholder.application.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

  private static final String PROBLEM_JSON = APPLICATION_PROBLEM_JSON_VALUE;
  private static final String PROBLEM = PROBLEM_SCHEMA;

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
  @Operation(
      description =
          "Cadastra o portador e solicita a emissão. 202 significa aceito e rastreável, não cartão emitido: o desfecho aparece em /overview, para onde aponta o Location.")
  @ApiResponse(
      responseCode = "202",
      description = "Aceito e rastreável",
      headers =
          @Header(
              name = "Location",
              description = "/api/v1/cardholders/{cardholderId}/overview",
              schema = @Schema(type = "string")),
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = RegistrationReceipt.class)))
  @ApiResponse(
      responseCode = "409",
      description = "cpf-already-registered: CPF já cadastrado; não repetir",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "validation-failed (CPF, idade de 18 a 120 anos, nome com sobrenome; ver invalidFields), product-not-found ou product-canceled",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
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

  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: portador inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @GetMapping("/{cardholderId}")
  CardholderResponse get(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(cardholders.get(cardholderId));
  }

  @Operation(
      description =
          "Consulta consolidada: sempre 200 quando o portador existe; os cinco casos são distinguíveis por issuance.status, card.availability e product.availability.")
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: portador inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @GetMapping("/{cardholderId}/overview")
  OverviewResponse overview(@PathVariable UUID cardholderId) {
    return OverviewResponse.from(overview.get(cardholderId));
  }

  /** ACTIVE -> BLOCKED; 200 sem mudança se já estiver BLOCKED; 409 a partir de CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "invalid-status-transition: a partir de CANCELED",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardholderId}/block")
  CardholderResponse block(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.block(cardholderId));
  }

  /** BLOCKED -> ACTIVE; 200 sem mudança se já estiver ACTIVE; 409 a partir de CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "invalid-status-transition: a partir de CANCELED",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardholderId}/unblock")
  CardholderResponse unblock(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.unblock(cardholderId));
  }

  /** ACTIVE ou BLOCKED -> CANCELED (terminal); 200 sem mudança se já estiver CANCELED. */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{cardholderId}/cancel")
  CardholderResponse cancel(@PathVariable UUID cardholderId) {
    return CardholderResponse.from(status.cancel(cardholderId));
  }
}
