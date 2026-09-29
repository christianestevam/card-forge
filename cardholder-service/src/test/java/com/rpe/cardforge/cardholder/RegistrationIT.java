package com.rpe.cardforge.cardholder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.jayway.jsonpath.JsonPath;
import com.rpe.cardforge.platform.outbox.OutboxRelay;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Cadastro, publicação pelo outbox, aplicação do resultado e consulta consolidada, com SQS
 * (LocalStack) e PostgreSQL reais; catálogo e card-service simulados com WireMock. O relay do
 * outbox é acionado manualmente para controlar o momento da publicação.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    properties = {
      "JWT_ISSUER_URI=http://issuer.test/realms/cardforge",
      "JWT_JWK_SET_URI=http://issuer.test/certs",
      "OAUTH_CLIENT_ID=cardholder-service",
      "OAUTH_CLIENT_SECRET=test-secret",
      "cardforge.outbox.poll-interval-millis=3600000",
      "cardforge.outbox.send-timeout=1s"
    })
class RegistrationIT {

  static final String REQUESTED = "card-issuance-requested";
  static final String COMPLETED = "card-issuance-completed";
  static final String COMPLETED_DLQ = "card-issuance-completed-dlq";

  @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @ServiceConnection
  static final LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withServices(LocalStackContainer.Service.SQS);

  static final WireMockServer remote = new WireMockServer(options().dynamicPort());

  static {
    Startables.deepStart(postgres, localstack).join();
    remote.start();
    remote.stubFor(
        post("/token")
            .willReturn(
                okJson(
                    """
                    {"access_token":"test-token","token_type":"Bearer","expires_in":300}""")));
    for (String queue : List.of(REQUESTED, COMPLETED, COMPLETED_DLQ)) {
      try {
        localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", queue);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("CATALOG_BASE_URL", remote::baseUrl);
    registry.add("CARDS_BASE_URL", remote::baseUrl);
    registry.add("OAUTH_TOKEN_URI", () -> remote.baseUrl() + "/token");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired OutboxRelay relay;
  @Autowired SqsAsyncClient sqs;
  @Autowired ObjectMapper objectMapper;

  // ---------------------------------------------------------------------------------------------

  @Test
  void registersCardholderAndQueuesIssuanceRequestWithoutPersonalData() throws Exception {
    UUID productId = activeProduct();
    String cpf = TestCpfs.random();

    String body =
        register(cpf, "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", startsWith("/api/v1/cardholders/")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID cardholderId = UUID.fromString(JsonPath.read(body, "$.cardholderId"));
    UUID requestId = UUID.fromString(JsonPath.read(body, "$.issuanceRequestId"));

    assertThat(requestStatus(requestId)).isEqualTo("PENDING");
    String outboxPayload = outboxPayload(requestId);
    assertThat(outboxPayload).doesNotContain(cpf).doesNotContain("1990-05-20");

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> relay.publishBatch() == 0 && pending(requestId) == 0);
    JsonNode published = awaitPublished(requestId);
    assertThat(published.get("eventType").asText()).isEqualTo("IssuanceRequested");
    assertThat(published.get("payload").get("cardholderId").asText())
        .isEqualTo(cardholderId.toString());
    assertThat(published.toString()).doesNotContain(cpf);

    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/cardholders/" + cardholderId)
                .with(scopes("cardholders:read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.maskedCpf")
                .value("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"))
        .andExpect(jsonPath("$.birthDate").doesNotExist())
        .andExpect(jsonPath("$.cpf").doesNotExist())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  /**
   * R2: o cadastro duplicado responde 409 sem que o CPF completo apareça no log da aplicação nem no
   * log do PostgreSQL.
   */
  @Test
  void duplicateCpfNeverLeaksTheCpfToLogs(CapturedOutput output) throws Exception {
    UUID productId = activeProduct();
    String cpf = TestCpfs.random();
    register(cpf, "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
        .andExpect(status().isAccepted());

    register(cpf, "Outra Pessoa", LocalDate.of(1985, 1, 1), productId)
        .andExpect(status().isConflict());

    assertThat(output.getAll()).doesNotContain(cpf);
    assertThat(postgres.getLogs()).doesNotContain(cpf);
  }

  @Test
  void invalidRegistrationListsEveryFieldAndCreatesNothing() throws Exception {
    UUID productId = UUID.randomUUID();

    register("12345678900", "Ana", LocalDate.now().minusYears(17), productId)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/validation-failed"))
        .andExpect(jsonPath("$.invalidFields.length()").value(3))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("12345678900"))));

    assertThat(cardholdersFor(productId)).isZero();
    assertThat(remote.findAll(getRequestedFor(urlEqualTo(productPath(productId))))).isEmpty();
  }

  @Test
  void duplicateCpfIsConflict() throws Exception {
    UUID productId = activeProduct();
    String cpf = TestCpfs.random();
    register(cpf, "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
        .andExpect(status().isAccepted());

    register(cpf, "Outra Pessoa", LocalDate.of(1985, 1, 1), productId)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://cardforge.rpe.com.br/problems/cpf-already-registered"));
    assertThat(cardholdersFor(productId)).isEqualTo(1);
  }

  @Test
  void unknownOrCanceledProductIsRejectedAndNothingIsCreated() throws Exception {
    UUID missing = UUID.randomUUID();
    remote.stubFor(get(productPath(missing)).willReturn(aResponse().withStatus(404)));
    register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), missing)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/product-not-found"));
    assertThat(cardholdersFor(missing)).isZero();

    UUID canceled = UUID.randomUUID();
    stubProduct(canceled, "CANCELED");
    register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), canceled)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.type").value("https://cardforge.rpe.com.br/problems/product-canceled"));
    assertThat(cardholdersFor(canceled)).isZero();
  }

  @Test
  void catalogOfflineStillAcceptsRegistration() throws Exception {
    UUID productId = UUID.randomUUID();
    remote.stubFor(get(productPath(productId)).willReturn(serviceUnavailable()));

    register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
        .andExpect(status().isAccepted());
    assertThat(cardholdersFor(productId)).isEqualTo(1);
  }

  /**
   * R5: catálogo com status nulo é erro de contrato: o cadastro é aceito com alerta, e a consulta
   * consolidada degrada em vez de responder 500.
   */
  @Test
  void catalogWithNullStatusIsAContractErrorNotAServerError() throws Exception {
    UUID productId = UUID.randomUUID();
    remote.stubFor(
        get(productPath(productId))
            .willReturn(
                okJson(
                    """
                    {"id":"%s","name":"Gold","bin":"12345678","status":null}"""
                        .formatted(productId))));

    String receipt =
        register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    overview(UUID.fromString(JsonPath.read(receipt, "$.cardholderId")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.availability").value("UNAVAILABLE"));
  }

  /** R6: corpo JSON null na fila de resultados vai direto para a DLQ. */
  @Test
  void nullResultBodyGoesToDeadLetterQueue() throws Exception {
    sendCompleted("null");

    String url = sqs.getQueueUrl(b -> b.queueName(COMPLETED_DLQ)).get().queueUrl();
    await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () ->
                sqs
                    .receiveMessage(b -> b.queueUrl(url).waitTimeSeconds(1).maxNumberOfMessages(10))
                    .get()
                    .messages()
                    .stream()
                    .anyMatch(m -> m.body().equals("null")));
  }

  /** (d) SQS fora no cadastro: 202, o evento fica no outbox e sai quando a SQS volta. */
  @Test
  void sqsUnavailableKeepsEventInOutboxUntilItRecovers() throws Exception {
    UUID productId = activeProduct();
    var docker = DockerClientFactory.instance().client();
    docker.pauseContainerCmd(localstack.getContainerId()).exec();
    UUID requestId;
    try {
      String body =
          register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
              .andExpect(status().isAccepted())
              .andReturn()
              .getResponse()
              .getContentAsString();
      requestId = UUID.fromString(JsonPath.read(body, "$.issuanceRequestId"));

      relay.publishBatch();
      assertThat(pending(requestId)).as("event stays pending while SQS is down").isEqualTo(1);
    } finally {
      docker.unpauseContainerCmd(localstack.getContainerId()).exec();
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              relay.publishBatch();
              return pending(requestId) == 0;
            });
    assertThat(awaitPublished(requestId).get("payload").get("issuanceRequestId").asText())
        .isEqualTo(requestId.toString());
  }

  @Test
  void appliesIssuanceResultIdempotentlyAndSendsContradictionsToDlq() throws Exception {
    Registered r = registerActive();
    UUID cardId = UUID.randomUUID();
    String issued = completed(r.requestId(), "ISSUED", cardId, null);

    sendCompleted(issued);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> "ISSUED".equals(requestStatus(r.requestId())));

    sendCompleted(issued);
    String contradictory = completed(r.requestId(), "FAILED", null, "PRODUCT_CANCELED");
    sendCompleted(contradictory);

    await().atMost(Duration.ofSeconds(15)).until(() -> dlqContains(r.requestId().toString()));
    assertThat(requestStatus(r.requestId())).isEqualTo("ISSUED");
    assertThat(
            jdbc.sql("SELECT card_id FROM issuance_requests WHERE id = ?")
                .param(r.requestId())
                .query(UUID.class)
                .single())
        .isEqualTo(cardId);
  }

  @Test
  void overviewShowsIssuedCardAndCurrentProduct() throws Exception {
    Registered r = registerActive();
    UUID cardId = UUID.randomUUID();
    remote.stubFor(
        get("/api/v1/cards/" + cardId)
            .willReturn(
                okJson(
                    """
                    {"id":"%s","panLastFour":"4242","expirationDate":"2031-09","status":"ACTIVE"}"""
                        .formatted(cardId))));
    sendCompleted(completed(r.requestId(), "ISSUED", cardId, null));
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> "ISSUED".equals(requestStatus(r.requestId())));

    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("ISSUED"))
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.card.data.panLastFour").value("4242"))
        .andExpect(jsonPath("$.product.availability").value("CURRENT"))
        .andExpect(jsonPath("$.product.observedAt").isNotEmpty())
        .andExpect(jsonPath("$.cardholder.maskedCpf").isNotEmpty());
  }

  @Test
  void overviewSurvivesDependencyFailures() throws Exception {
    Registered r = registerActive();

    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("PENDING"))
        .andExpect(jsonPath("$.card.availability").value("NOT_APPLICABLE"));

    UUID cardId = UUID.randomUUID();
    remote.stubFor(get("/api/v1/cards/" + cardId).willReturn(aResponse().withStatus(500)));
    remote.stubFor(get(productPath(r.productId())).willReturn(serviceUnavailable()));
    sendCompleted(completed(r.requestId(), "ISSUED", cardId, null));
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> "ISSUED".equals(requestStatus(r.requestId())));

    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("ISSUED"))
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.card.data").doesNotExist())
        .andExpect(jsonPath("$.product.availability").value("STALE"));
  }

  /** Catálogo offline: produto STALE (observação do cadastro), o restante segue completo. */
  @Test
  void overviewWithCatalogOfflineKeepsCardAndMarksProductStale() throws Exception {
    Registered r = registerActive();
    UUID cardId = issueWithCard(r, id -> okJson(cardJson(id)));
    remote.stubFor(get(productPath(r.productId())).willReturn(serviceUnavailable()));

    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("ISSUED"))
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("AVAILABLE"))
        .andExpect(jsonPath("$.product.availability").value("STALE"))
        .andExpect(jsonPath("$.product.observedAt").isNotEmpty())
        .andExpect(jsonPath("$.product.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.cardholder.id").value(r.cardholderId().toString()));
  }

  /** Catálogo com timeout: mesma degradação, dentro do orçamento de latência. */
  @Test
  void overviewWithCatalogTimeoutMarksProductStale() throws Exception {
    Registered r = registerActive();
    remote.stubFor(get(productPath(r.productId())).willReturn(okJson("{}").withFixedDelay(3000)));

    long started = System.nanoTime();
    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("PENDING"))
        .andExpect(jsonPath("$.product.availability").value("STALE"));
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
  }

  /** card-service offline: situação ISSUED com cardId preservado, só os detalhes indisponíveis. */
  @Test
  void overviewWithCardServiceOfflineKeepsIssuedStatusAndProduct() throws Exception {
    Registered r = registerActive();
    UUID cardId = issueWithCard(r, id -> aResponse().withStatus(503));

    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("ISSUED"))
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.card.data").doesNotExist())
        .andExpect(jsonPath("$.product.availability").value("CURRENT"))
        .andExpect(jsonPath("$.product.data.status").value("ACTIVE"));
  }

  /** card-service lento: timeout vira UNAVAILABLE, sem derrubar a consulta. */
  @Test
  void overviewWithCardServiceTimeoutMarksCardUnavailable() throws Exception {
    Registered r = registerActive();
    UUID cardId = issueWithCard(r, id -> okJson(cardJson(id)).withFixedDelay(3000));

    long started = System.nanoTime();
    overview(r.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.product.availability").value("CURRENT"));
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
  }

  /** Configura a resposta do card-service para o cartão e aplica um resultado ISSUED. */
  private UUID issueWithCard(Registered r, Function<UUID, ResponseDefinitionBuilder> cardResponse)
      throws Exception {
    UUID cardId = UUID.randomUUID();
    remote.stubFor(get("/api/v1/cards/" + cardId).willReturn(cardResponse.apply(cardId)));
    sendCompleted(completed(r.requestId(), "ISSUED", cardId, null));
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> "ISSUED".equals(requestStatus(r.requestId())));
    return cardId;
  }

  private static String cardJson(UUID cardId) {
    return """
        {"id":"%s","panLastFour":"4242","expirationDate":"2031-09","status":"ACTIVE"}"""
        .formatted(cardId);
  }

  @Test
  void cardholderStatusTransitionsAreIdempotentAndCanceledIsTerminal() throws Exception {
    Registered r = registerActive();

    changeStatus(r.cardholderId(), "block")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"));
    changeStatus(r.cardholderId(), "block")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"));
    changeStatus(r.cardholderId(), "unblock")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    changeStatus(r.cardholderId(), "cancel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.maskedCpf").isNotEmpty());
    changeStatus(r.cardholderId(), "cancel").andExpect(status().isOk());
    changeStatus(r.cardholderId(), "unblock")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://cardforge.rpe.com.br/problems/invalid-status-transition"));

    mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/cardholders/" + r.cardholderId())
                .with(scopes("cardholders:read")))
        .andExpect(jsonPath("$.status").value("CANCELED"));
  }

  @Test
  void cardholderStatusChangeRequiresWriteScopeAndExistingCardholder() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/cardholders/" + UUID.randomUUID() + "/block")
                .with(scopes("cardholders:read")))
        .andExpect(status().isForbidden());
    changeStatus(UUID.randomUUID(), "block").andExpect(status().isNotFound());
  }

  private ResultActions changeStatus(UUID cardholderId, String action) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/cardholders/" + cardholderId + "/" + action)
            .with(scopes("cardholders:write")));
  }

  /**
   * TC8: a consulta consolidada distingue ausência esperada, desfecho de negócio, indisponibilidade
   * e dado desatualizado, sempre com 200 e por campos estruturados.
   */
  @Test
  void consolidatedViewDistinguishesTheFiveCases() throws Exception {
    // 1. Pendente: ausência esperada do cartão; produto atual.
    Registered pending = registerActive();
    overview(pending.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("PENDING"))
        .andExpect(jsonPath("$.issuance.requestedAt").isNotEmpty())
        .andExpect(jsonPath("$.card.availability").value("NOT_APPLICABLE"))
        .andExpect(jsonPath("$.product.availability").value("CURRENT"));

    // 2. Falha de negócio: FAILED com motivo; o card-service não é chamado.
    Registered failed = registerActive();
    sendCompleted(completed(failed.requestId(), "FAILED", null, "PRODUCT_CANCELED"));
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> "FAILED".equals(requestStatus(failed.requestId())));
    overview(failed.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("FAILED"))
        .andExpect(jsonPath("$.issuance.failureReason").value("PRODUCT_CANCELED"))
        .andExpect(jsonPath("$.issuance.decidedAt").isNotEmpty())
        .andExpect(jsonPath("$.card.availability").value("NOT_APPLICABLE"));

    // 3. Emitido com o card-service fora: ISSUED e cardId mantidos, detalhes indisponíveis.
    Registered issued = registerActive();
    UUID cardId = issueWithCard(issued, id -> aResponse().withStatus(503));
    overview(issued.cardholderId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuance.status").value("ISSUED"))
        .andExpect(jsonPath("$.issuance.cardId").value(cardId.toString()))
        .andExpect(jsonPath("$.card.availability").value("UNAVAILABLE"));

    // 4. Produto desatualizado: catálogo fora, última observação sinalizada com o instante dela.
    Registered stale = registerActive();
    java.time.Instant observedAtRegistration =
        jdbc.sql("SELECT observed_at FROM product_observations WHERE product_id = ?")
            .param(stale.productId())
            .query(java.sql.Timestamp.class)
            .single()
            .toInstant();
    remote.stubFor(get(productPath(stale.productId())).willReturn(serviceUnavailable()));
    String body =
        overview(stale.cardholderId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.availability").value("STALE"))
            .andExpect(jsonPath("$.product.data.id").value(stale.productId().toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(java.time.Instant.parse(JsonPath.read(body, "$.product.observedAt")))
        .isEqualTo(observedAtRegistration);

    // 5. Produto sem observação: cadastro e consulta com o catálogo fora.
    UUID unknownProduct = UUID.randomUUID();
    remote.stubFor(get(productPath(unknownProduct)).willReturn(serviceUnavailable()));
    String receipt =
        register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), unknownProduct)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    overview(UUID.fromString(JsonPath.read(receipt, "$.cardholderId")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.product.availability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.product.observedAt").doesNotExist())
        .andExpect(jsonPath("$.product.data").doesNotExist());
  }

  /** Uma consulta bem-sucedida atualiza a observação; uma falha posterior mostra a mais recente. */
  @Test
  void successfulOverviewRefreshesTheStoredObservation() throws Exception {
    Registered r = registerActive();
    java.time.Instant atRegistration = observedAt(r.productId());

    overview(r.cardholderId()).andExpect(jsonPath("$.product.availability").value("CURRENT"));

    assertThat(observedAt(r.productId())).isAfter(atRegistration);
  }

  private java.time.Instant observedAt(UUID productId) {
    return jdbc.sql("SELECT observed_at FROM product_observations WHERE product_id = ?")
        .param(productId)
        .query(java.sql.Timestamp.class)
        .single()
        .toInstant();
  }

  @Test
  void openApiDocumentsRegistrationErrors() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/cardholders'].post.responses['202']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/cardholders'].post.responses['200']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/cardholders'].post.responses['401']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/cardholders'].post.responses['409'].description")
                .value(org.hamcrest.Matchers.containsString("cpf-already-registered")))
        .andExpect(
            jsonPath("$.paths['/api/v1/cardholders'].post.responses['422'].description")
                .value(org.hamcrest.Matchers.containsString("product-canceled")));
  }

  @Test
  void unknownCardholderIsNotFound() throws Exception {
    overview(UUID.randomUUID()).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------------------------

  record Registered(UUID cardholderId, UUID requestId, UUID productId) {}

  private Registered registerActive() throws Exception {
    UUID productId = activeProduct();
    String body =
        register(TestCpfs.random(), "Maria da Silva", LocalDate.of(1990, 5, 20), productId)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Registered(
        UUID.fromString(JsonPath.read(body, "$.cardholderId")),
        UUID.fromString(JsonPath.read(body, "$.issuanceRequestId")),
        productId);
  }

  private ResultActions register(String cpf, String name, LocalDate birthDate, UUID productId)
      throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post("/api/v1/cardholders")
            .with(scopes("cardholders:write"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of(
                        "cpf",
                        cpf,
                        "fullName",
                        name,
                        "birthDate",
                        birthDate.toString(),
                        "productId",
                        productId.toString()))));
  }

  private ResultActions overview(UUID cardholderId) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.get("/api/v1/cardholders/" + cardholderId + "/overview")
            .with(scopes("cardholders:read")));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor scopes(
      String scope) {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
  }

  private UUID activeProduct() {
    UUID productId = UUID.randomUUID();
    stubProduct(productId, "ACTIVE");
    return productId;
  }

  private static String productPath(UUID productId) {
    return "/api/v1/products/" + productId;
  }

  private static void stubProduct(UUID productId, String status) {
    remote.stubFor(
        get(productPath(productId))
            .willReturn(
                okJson(
                    """
                    {"id":"%s","name":"Gold","bin":"12345678","status":"%s"}"""
                        .formatted(productId, status))));
  }

  private static String completed(UUID requestId, String status, UUID cardId, String reason) {
    String payload =
        cardId != null
            ? "{\"issuanceRequestId\":\"%s\",\"status\":\"%s\",\"cardId\":\"%s\"}"
                .formatted(requestId, status, cardId)
            : "{\"issuanceRequestId\":\"%s\",\"status\":\"%s\",\"failureReason\":\"%s\"}"
                .formatted(requestId, status, reason);
    return """
        {"eventId":"%s","eventType":"IssuanceCompleted","eventVersion":1,
         "occurredAt":"2026-09-29T12:00:00Z","correlationId":"it","payload":%s}"""
        .formatted(UUID.randomUUID(), payload);
  }

  private void sendCompleted(String body) throws Exception {
    String url = sqs.getQueueUrl(b -> b.queueName(COMPLETED)).get().queueUrl();
    sqs.sendMessage(b -> b.queueUrl(url).messageBody(body)).get();
  }

  private boolean dlqContains(String text) throws Exception {
    String url = sqs.getQueueUrl(b -> b.queueName(COMPLETED_DLQ)).get().queueUrl();
    return sqs
        .receiveMessage(b -> b.queueUrl(url).waitTimeSeconds(1).maxNumberOfMessages(10))
        .get()
        .messages()
        .stream()
        .anyMatch(m -> m.body().contains(text));
  }

  private JsonNode awaitPublished(UUID requestId) throws Exception {
    String url = sqs.getQueueUrl(b -> b.queueName(REQUESTED)).get().queueUrl();
    JsonNode[] found = new JsonNode[1];
    await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () -> {
              for (Message m :
                  sqs.receiveMessage(
                          b -> b.queueUrl(url).waitTimeSeconds(1).maxNumberOfMessages(10))
                      .get()
                      .messages()) {
                if (m.body().contains(requestId.toString())) {
                  found[0] = objectMapper.readTree(m.body());
                  return true;
                }
              }
              return false;
            });
    return found[0];
  }

  private String requestStatus(UUID requestId) {
    return jdbc.sql("SELECT status FROM issuance_requests WHERE id = ?")
        .param(requestId)
        .query(String.class)
        .single();
  }

  private String outboxPayload(UUID requestId) {
    return jdbc.sql("SELECT payload::text FROM outbox_events WHERE aggregate_id = ?")
        .param(requestId)
        .query(String.class)
        .single();
  }

  private long pending(UUID requestId) {
    return jdbc.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND sent_at IS NULL")
        .param(requestId)
        .query(Long.class)
        .single();
  }

  private long cardholdersFor(UUID productId) {
    return jdbc.sql("SELECT count(*) FROM cardholders WHERE product_id = ?")
        .param(productId)
        .query(Long.class)
        .single();
  }
}
