package com.rpe.cardforge.card;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.rpe.cardforge.card.domain.RandomDigits;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Emissão ponta a ponta dentro do card-service: SQS (LocalStack), PostgreSQL, Redis e o catálogo
 * simulado com WireMock. O backoff inicial é reduzido para 2 s para observar a reentrega.
 */
@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "JWT_ISSUER_URI=http://issuer.test/realms/cardforge",
      "JWT_JWK_SET_URI=http://issuer.test/certs",
      "OAUTH_CLIENT_ID=card-service",
      "OAUTH_CLIENT_SECRET=test-secret",
      "cardforge.issuance.retry-initial-delay=2s",
      "cardforge.catalog.read-timeout=1s",
      "cardforge.outbox.poll-interval-millis=200"
    })
class IssuanceIT {

  static final String REQUESTED = "card-issuance-requested";

  @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @ServiceConnection(name = "redis")
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:8.8.3-alpine").withExposedPorts(6379);

  @ServiceConnection
  static final LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withServices(LocalStackContainer.Service.SQS);

  static final WireMockServer catalog = new WireMockServer(options().dynamicPort());

  static {
    Startables.deepStart(postgres, redis, localstack).join();
    catalog.start();
    catalog.stubFor(
        post("/token")
            .willReturn(
                okJson(
                    """
                    {"access_token":"test-token","token_type":"Bearer","expires_in":300}""")));
    for (String queue :
        List.of(REQUESTED, "card-issuance-requested-dlq", "card-issuance-completed")) {
      try {
        localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", queue);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("CATALOG_BASE_URL", catalog::baseUrl);
    registry.add("OAUTH_TOKEN_URI", () -> catalog.baseUrl() + "/token");
    registry.add("pan-hmac-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  /** Fonte de dígitos roteirizável, para forçar colisões de PAN (TC-PAN). */
  @TestConfiguration
  static class ScriptedDigitsConfig {
    @Bean
    @Primary
    ScriptedDigits scriptedDigits() {
      return new ScriptedDigits();
    }
  }

  static class ScriptedDigits implements RandomDigits {
    private final Deque<Integer> script = new ConcurrentLinkedDeque<>();

    void enqueue(String digits) {
      digits.chars().forEach(c -> script.add(c - '0'));
    }

    @Override
    public int nextDigit() {
      Integer next = script.poll();
      return next != null ? next : ThreadLocalRandom.current().nextInt(10);
    }
  }

  @Autowired ScriptedDigits digits;
  @Autowired com.rpe.cardforge.card.infrastructure.BinOccupancyMetrics binOccupancy;
  @Autowired io.micrometer.core.instrument.MeterRegistry meters;
  @Autowired SqsAsyncClient sqs;
  @Autowired JdbcClient jdbc;
  @Autowired StringRedisTemplate redisTemplate;
  @Autowired ObjectMapper objectMapper;
  @Autowired MockMvc mvc;

  // ---------------------------------------------------------------------------------------------

  @Test
  void issuesCardValidatingProductThroughCacheAndPublishesResult() throws Exception {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");

    send(r.body(), "corr-happy");

    UUID cardId = awaitDecision(r, "ISSUED");
    Map<String, Object> card =
        jdbc.sql("SELECT * FROM cards WHERE id = ?").param(cardId).query().singleRow();
    assertThat((String) card.get("pan_last_four")).matches("\\d{4}");
    assertThat((String) card.get("pan_hmac")).matches("[0-9a-f]{64}");
    assertThat(card.get("cardholder_id")).isEqualTo(r.cardholderId());

    JsonNode event = awaitCompletedEvents(r, 1).getFirst();
    assertThat(event.get("status").asText()).isEqualTo("ISSUED");
    assertThat(event.get("cardId").asText()).isEqualTo(cardId.toString());
    assertThat(correlationOfCompletedEvent(r)).isEqualTo("corr-happy");

    JsonNode cached =
        objectMapper.readTree(
            redisTemplate.opsForValue().get("cardforge:product:v1:" + r.productId()));
    assertThat(cached.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(cached.get("validatedAt").asText()).isNotBlank();

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/cards/" + cardId)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_cards:read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.panLastFour").value(card.get("pan_last_four")))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.pan").doesNotExist())
        .andExpect(jsonPath("$.panHmac").doesNotExist());
  }

  /** (a) Catálogo com 5xx: a mensagem não é confirmada, nenhum cartão; volta após o backoff. */
  @Test
  void catalogServerErrorLeavesMessageUnacknowledgedAndRetriesAfterBackoff() {
    Request r = Request.random();
    String path = productPath(r.productId());
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs(STARTED)
            .willReturn(serverError())
            .willSetStateTo("recovered"));
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs("recovered")
            .willReturn(okJson(product(r.productId(), "ACTIVE"))));

    send(r.body(), "corr-5xx");

    await().atMost(Duration.ofSeconds(10)).until(() -> catalogCalls(path).size() >= 1);
    assertThat(decisionCount(r)).isZero();
    assertThat(cardCount(r)).isZero();

    UUID cardId = awaitDecision(r, "ISSUED");
    List<LoggedRequest> calls = catalogCalls(path);
    assertThat(calls).hasSize(2);
    long gapMillis =
        calls.get(1).getLoggedDate().getTime() - calls.get(0).getLoggedDate().getTime();
    assertThat(gapMillis).as("redelivery honours the backoff").isGreaterThanOrEqualTo(1500);
    assertThat(cardId).isNotNull();
  }

  /** (a) Catálogo com timeout: mesma classe de falha, sem cartão até a recuperação. */
  @Test
  void catalogTimeoutLeavesMessageUnacknowledged() {
    Request r = Request.random();
    String path = productPath(r.productId());
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs(STARTED)
            .willReturn(okJson(product(r.productId(), "ACTIVE")).withFixedDelay(2500))
            .willSetStateTo("fast"));
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs("fast")
            .willReturn(okJson(product(r.productId(), "ACTIVE"))));

    send(r.body(), "corr-timeout");

    await().atMost(Duration.ofSeconds(10)).until(() -> catalogCalls(path).size() >= 1);
    assertThat(decisionCount(r)).isZero();
    assertThat(cardCount(r)).isZero();

    awaitDecision(r, "ISSUED");
    assertThat(catalogCalls(path)).hasSize(2);
  }

  /** (b) Produto CANCELED: FAILED gravado, resultado publicado e nenhuma retentativa. */
  @Test
  void canceledProductFailsWithoutRetry() throws Exception {
    Request r = Request.random();
    stubProduct(r.productId(), "CANCELED");

    send(r.body(), "corr-canceled");

    awaitDecision(r, "FAILED");
    assertThat(failureReason(r)).isEqualTo("PRODUCT_CANCELED");
    JsonNode event = awaitCompletedEvents(r, 1).getFirst();
    assertThat(event.get("failureReason").asText()).isEqualTo("PRODUCT_CANCELED");

    Thread.sleep(6000); // mais que duas janelas de backoff: uma reentrega já teria acontecido
    assertThat(catalogCalls(productPath(r.productId()))).hasSize(1);
    assertThat(cardCount(r)).isZero();
    assertThat(completedEvents(r)).hasSize(1);
  }

  @Test
  void unknownProductFailsAsProductNotFound() {
    Request r = Request.random();
    catalog.stubFor(get(productPath(r.productId())).willReturn(aResponse().withStatus(404)));

    send(r.body(), "corr-404");

    awaitDecision(r, "FAILED");
    assertThat(failureReason(r)).isEqualTo("PRODUCT_NOT_FOUND");
  }

  /**
   * TC-PAN: o segundo cartão sorteia o mesmo PAN do primeiro; o ON CONFLICT (pan_hmac) detecta a
   * colisão, um novo candidato é gerado e cartão, resultado e outbox são gravados juntos.
   */
  @Test
  void panCollisionIsResolvedWithANewCandidate() {
    UUID productId = UUID.randomUUID();
    stubProduct(productId, "ACTIVE");
    Request first = new Request(UUID.randomUUID(), UUID.randomUUID(), productId);
    Request second = new Request(UUID.randomUUID(), UUID.randomUUID(), productId);

    digits.enqueue("1111111");
    send(first.body(), "corr-pan-1");
    UUID firstCard = awaitDecision(first, "ISSUED");

    digits.enqueue("1111111"); // colide com o primeiro cartão
    digits.enqueue("2222222");
    send(second.body(), "corr-pan-2");
    UUID secondCard = awaitDecision(second, "ISSUED");

    Map<String, Object> a =
        jdbc.sql("SELECT * FROM cards WHERE id = ?").param(firstCard).query().singleRow();
    Map<String, Object> b =
        jdbc.sql("SELECT * FROM cards WHERE id = ?").param(secondCard).query().singleRow();
    assertThat((String) a.get("pan_last_four")).startsWith("111");
    assertThat((String) b.get("pan_last_four")).startsWith("222");
    assertThat(b.get("pan_hmac")).isNotEqualTo(a.get("pan_hmac"));
    assertThat(b.get("issuance_request_id")).isEqualTo(second.issuanceRequestId());
    assertThat(awaitCompletedEvents(second, 1).getFirst().get("cardId").asText())
        .isEqualTo(secondCard.toString());
  }

  /** (c) A mesma mensagem entregue duas vezes gera um único cartão; o resultado é republicado. */
  @Test
  void duplicateDeliveryIssuesSingleCard() {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");

    CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> send(r.body(), "corr-dup")),
            CompletableFuture.runAsync(() -> send(r.body(), "corr-dup")))
        .join();

    List<JsonNode> events = awaitCompletedEvents(r, 2);
    assertThat(cardCount(r)).isEqualTo(1);
    assertThat(decisionCount(r)).isEqualTo(1);
    assertThat(events)
        .extracting(e -> e.get("cardId").asText())
        .containsOnly(events.getFirst().get("cardId").asText());
  }

  /**
   * TC2: a mensagem volta depois que o desfecho foi gravado (falha antes do ACK). A reentrega não
   * reavalia nem gera outro cartão: republica o mesmo resultado.
   */
  @Test
  void redeliveryAfterCommitRepublishesWithoutNewEffect() {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");
    send(r.body(), "corr-tc2");
    UUID cardId = awaitDecision(r, "ISSUED");
    awaitCompletedEvents(r, 1);

    send(r.body(), "corr-tc2-redelivery");

    List<JsonNode> events = awaitCompletedEvents(r, 2);
    assertThat(events).extracting(e -> e.get("cardId").asText()).containsOnly(cardId.toString());
    assertThat(cardCount(r)).isEqualTo(1);
    assertThat(catalogCalls(productPath(r.productId()))).hasSize(1);
  }

  /**
   * TC4: recusa de negócio persistida e reentregue sem mudar o desfecho, mesmo com o produto ativo.
   */
  @Test
  void redeliveredBusinessRefusalKeepsItsOutcome() {
    Request r = Request.random();
    stubProduct(r.productId(), "CANCELED");
    send(r.body(), "corr-tc4");
    awaitDecision(r, "FAILED");

    stubProduct(r.productId(), "ACTIVE"); // o catálogo muda, mas a solicitação já foi decidida
    send(r.body(), "corr-tc4-redelivery");

    List<JsonNode> events = awaitCompletedEvents(r, 2);
    assertThat(events)
        .extracting(e -> e.get("failureReason").asText())
        .containsOnly("PRODUCT_CANCELED");
    assertThat(failureReason(r)).isEqualTo("PRODUCT_CANCELED");
    assertThat(cardCount(r)).isZero();
  }

  /** TC5: duas solicitações concorrentes para o mesmo portador e produto: só uma emite. */
  @Test
  void concurrentRequestsForSameCardholderAndProductIssueOnlyOneCard() {
    Request first = Request.random();
    Request second = new Request(UUID.randomUUID(), first.cardholderId(), first.productId());
    stubProduct(first.productId(), "ACTIVE");

    CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> send(first.body(), "corr-tc5-a")),
            CompletableFuture.runAsync(() -> send(second.body(), "corr-tc5-b")))
        .join();

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> decisionCount(first) == 1 && decisionCount(second) == 1);
    List<String> outcomes =
        List.of(
            jdbc.sql("SELECT status FROM issuance_processing WHERE issuance_request_id = ?")
                .param(first.issuanceRequestId())
                .query(String.class)
                .single(),
            jdbc.sql("SELECT status FROM issuance_processing WHERE issuance_request_id = ?")
                .param(second.issuanceRequestId())
                .query(String.class)
                .single());
    assertThat(outcomes).containsExactlyInAnyOrder("ISSUED", "FAILED");
    assertThat(cardCount(first)).isEqualTo(1);
  }

  /**
   * TC6: observação vencida no cache e catálogo fora: nenhuma emissão, a mensagem volta para
   * retentativa e emite quando o catálogo se recupera.
   */
  @Test
  void staleCacheWithCatalogDownDoesNotIssueAndRetries() throws Exception {
    Request r = Request.random();
    String path = productPath(r.productId());
    cache(r.productId(), "ACTIVE", Instant.now().minus(Duration.ofMinutes(6)));
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs(STARTED)
            .willReturn(serverError())
            .willSetStateTo("up"));
    catalog.stubFor(
        get(path)
            .inScenario(path)
            .whenScenarioStateIs("up")
            .willReturn(okJson(product(r.productId(), "ACTIVE"))));

    send(r.body(), "corr-tc6");

    await().atMost(Duration.ofSeconds(10)).until(() -> catalogCalls(path).size() >= 1);
    assertThat(decisionCount(r)).isZero();
    assertThat(cardCount(r)).isZero();
    awaitDecision(r, "ISSUED");
    assertThat(catalogCalls(path)).hasSize(2);
  }

  /** Um segundo pedido para o mesmo portador e produto é recusado pela regra de unicidade. */
  @Test
  void secondRequestForSameCardholderAndProductFails() {
    Request first = Request.random();
    Request second = new Request(UUID.randomUUID(), first.cardholderId(), first.productId());
    stubProduct(first.productId(), "ACTIVE");

    send(first.body(), "corr-first");
    awaitDecision(first, "ISSUED");
    send(second.body(), "corr-second");

    awaitDecision(second, "FAILED");
    assertThat(failureReason(second)).isEqualTo("NON_CANCELED_CARD_ALREADY_EXISTS");
  }

  /** Observação ACTIVE com menos de 5 minutos: emite sem consultar o catálogo. */
  @Test
  void freshCachedObservationAuthorizesWithoutCatalog() throws Exception {
    Request r = Request.random();
    cache(r.productId(), "ACTIVE", Instant.now().minus(Duration.ofMinutes(1)));

    send(r.body(), "corr-fresh");

    awaitDecision(r, "ISSUED");
    assertThat(catalogCalls(productPath(r.productId()))).isEmpty();
  }

  /** Observação com mais de 5 minutos não autoriza: o catálogo é consultado e decide. */
  @Test
  void staleCachedObservationIsRecheckedInCatalog() throws Exception {
    Request r = Request.random();
    cache(r.productId(), "ACTIVE", Instant.now().minus(Duration.ofMinutes(6)));
    stubProduct(r.productId(), "CANCELED");

    send(r.body(), "corr-stale");

    awaitDecision(r, "FAILED");
    assertThat(failureReason(r)).isEqualTo("PRODUCT_CANCELED");
    assertThat(catalogCalls(productPath(r.productId()))).hasSize(1);
    JsonNode tombstone =
        objectMapper.readTree(
            redisTemplate.opsForValue().get("cardforge:product:v1:" + r.productId()));
    assertThat(tombstone.get("status").asText()).isEqualTo("CANCELED");
  }

  /** TC7: cancelamento conhecido (lápide) recusa a emissão sem consultar o catálogo. */
  @Test
  void knownCancellationRefusesIssuanceWithoutCatalog() throws Exception {
    Request r = Request.random();
    cache(r.productId(), "CANCELED", Instant.now().minus(Duration.ofMinutes(1)));
    stubProduct(r.productId(), "ACTIVE");

    send(r.body(), "corr-tombstone");

    awaitDecision(r, "FAILED");
    assertThat(failureReason(r)).isEqualTo("PRODUCT_CANCELED");
    assertThat(catalogCalls(productPath(r.productId()))).isEmpty();
  }

  @Test
  void invalidMessageGoesToDeadLetterQueue() throws Exception {
    String marker = "garbage-" + UUID.randomUUID();
    send("{\"eventType\":\"" + marker + "\"}", "corr-invalid");

    String dlqUrl =
        sqs.getQueueUrl(b -> b.queueName("card-issuance-requested-dlq")).get().queueUrl();
    await()
        .atMost(Duration.ofSeconds(15))
        .until(
            () ->
                sqs
                    .receiveMessage(
                        b -> b.queueUrl(dlqUrl).waitTimeSeconds(1).maxNumberOfMessages(10))
                    .get()
                    .messages()
                    .stream()
                    .anyMatch(m -> m.body().contains(marker)));
  }

  @Test
  void cardStatusTransitionsAreIdempotentAndCanceledIsTerminal() throws Exception {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");
    send(r.body(), "corr-status");
    UUID cardId = awaitDecision(r, "ISSUED");

    changeStatus(cardId, "block")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"));
    changeStatus(cardId, "block")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"));
    changeStatus(cardId, "unblock")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    changeStatus(cardId, "unblock")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    changeStatus(cardId, "cancel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"));
    changeStatus(cardId, "cancel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"));
    changeStatus(cardId, "block")
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type")
                .value("https://cardforge.rpe.com.br/problems/invalid-status-transition"));
    changeStatus(cardId, "unblock").andExpect(status().isConflict());

    Map<String, Object> row =
        jdbc.sql("SELECT status, created_at, updated_at FROM cards WHERE id = ?")
            .param(cardId)
            .query()
            .singleRow();
    assertThat(row.get("status")).isEqualTo("CANCELED");
    assertThat(((java.sql.Timestamp) row.get("updated_at")))
        .isAfter((java.sql.Timestamp) row.get("created_at"));
  }

  /** Cancelar libera o índice parcial: o portador pode receber um novo cartão do produto. */
  @Test
  void canceledCardAllowsNewCardForSameCardholderAndProduct() throws Exception {
    Request first = Request.random();
    stubProduct(first.productId(), "ACTIVE");
    send(first.body(), "corr-first-card");
    UUID firstCard = awaitDecision(first, "ISSUED");
    changeStatus(firstCard, "cancel").andExpect(status().isOk());

    Request second = new Request(UUID.randomUUID(), first.cardholderId(), first.productId());
    send(second.body(), "corr-second-card");
    UUID secondCard = awaitDecision(second, "ISSUED");

    assertThat(secondCard).isNotEqualTo(firstCard);
  }

  @Test
  void cardStatusChangeRequiresWriteScopeAndExistingCard() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/cards/" + UUID.randomUUID() + "/block")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_cards:read"))))
        .andExpect(status().isForbidden());
    changeStatus(UUID.randomUUID(), "block").andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions changeStatus(
      UUID cardId, String action) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/cards/" + cardId + "/" + action)
            .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_cards:write"))));
  }

  @Test
  void listsCardsOfCardholderWithPagination() throws Exception {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");
    send(r.body(), "corr-list");
    UUID cardId = awaitDecision(r, "ISSUED");

    listCards(r.cardholderId().toString(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(cardId.toString()))
        .andExpect(jsonPath("$.content[0].panLastFour").exists())
        .andExpect(jsonPath("$.page.size").value(20))
        .andExpect(jsonPath("$.page.totalElements").value(1))
        .andExpect(jsonPath("$.page.totalPages").value(1));

    listCards(UUID.randomUUID().toString(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.page.totalPages").value(0));
  }

  @Test
  void cardListingRejectsOversizedPageAndMissingCardholder() throws Exception {
    listCards(UUID.randomUUID().toString(), "101").andExpect(status().isBadRequest());
    listCards(null, null).andExpect(status().isBadRequest());
  }

  private org.springframework.test.web.servlet.ResultActions listCards(
      String cardholderId, String size) throws Exception {
    var request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/cards")
            .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_cards:read")));
    if (cardholderId != null) {
      request.param("cardholderId", cardholderId);
    }
    if (size != null) {
      request.param("size", size);
    }
    return mvc.perform(request);
  }

  @Test
  void publishesBinOccupancyForIssuedCards() {
    Request r = Request.random();
    stubProduct(r.productId(), "ACTIVE");
    send(r.body(), "corr-bin");
    UUID cardId = awaitDecision(r, "ISSUED");
    String bin =
        jdbc.sql("SELECT bin FROM cards WHERE id = ?").param(cardId).query(String.class).single();

    binOccupancy.refresh();

    assertThat(bin).matches("\\d{8}");
    assertThat(meters.get("cardforge.bin.occupancy.ratio").tag("bin", bin).gauge().value())
        .isEqualTo(1.0 / 10_000_000);
  }

  @Test
  void unknownCardIsNotFound() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/cards/" + UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_cards:read"))))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------------------------

  record Request(UUID issuanceRequestId, UUID cardholderId, UUID productId) {
    static Request random() {
      return new Request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    String body() {
      return """
          {"eventId":"%s","eventType":"IssuanceRequested","eventVersion":1,
           "occurredAt":"2026-09-29T12:00:00Z","correlationId":"it",
           "payload":{"issuanceRequestId":"%s","cardholderId":"%s","productId":"%s"}}"""
          .formatted(UUID.randomUUID(), issuanceRequestId, cardholderId, productId);
    }
  }

  private void send(String body, String correlationId) {
    try {
      String url = sqs.getQueueUrl(b -> b.queueName(REQUESTED)).get().queueUrl();
      sqs.sendMessage(
              b ->
                  b.queueUrl(url)
                      .messageBody(body)
                      .messageAttributes(
                          Map.of(
                              "correlationId",
                              MessageAttributeValue.builder()
                                  .dataType("String")
                                  .stringValue(correlationId)
                                  .build())))
          .get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String productPath(UUID productId) {
    return "/api/v1/products/" + productId;
  }

  private static String product(UUID productId, String status) {
    String bin = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    return """
        {"id":"%s","name":"Gold","bin":"%s","status":"%s"}"""
        .formatted(productId, bin, status);
  }

  private void stubProduct(UUID productId, String status) {
    catalog.stubFor(
        get(urlEqualTo(productPath(productId))).willReturn(okJson(product(productId, status))));
  }

  private void cache(UUID productId, String status, Instant validatedAt) throws Exception {
    redisTemplate
        .opsForValue()
        .set(
            "cardforge:product:v1:" + productId,
            objectMapper.writeValueAsString(
                Map.of(
                    "productId",
                    productId,
                    "name",
                    "Gold",
                    "bin",
                    "12345678",
                    "status",
                    status,
                    "validatedAt",
                    validatedAt.toString())));
  }

  private List<LoggedRequest> catalogCalls(String path) {
    return catalog.findAll(getRequestedFor(urlEqualTo(path)));
  }

  private UUID awaitDecision(Request r, String expectedStatus) {
    await()
        .atMost(Duration.ofSeconds(20))
        .until(
            () ->
                jdbc.sql("SELECT status FROM issuance_processing WHERE issuance_request_id = ?")
                    .param(r.issuanceRequestId())
                    .query(String.class)
                    .optional()
                    .filter(expectedStatus::equals)
                    .isPresent());
    return jdbc.sql("SELECT card_id FROM issuance_processing WHERE issuance_request_id = ?")
        .param(r.issuanceRequestId())
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  private String failureReason(Request r) {
    return jdbc.sql("SELECT failure_reason FROM issuance_processing WHERE issuance_request_id = ?")
        .param(r.issuanceRequestId())
        .query(String.class)
        .single();
  }

  private long decisionCount(Request r) {
    return jdbc.sql("SELECT count(*) FROM issuance_processing WHERE issuance_request_id = ?")
        .param(r.issuanceRequestId())
        .query(Long.class)
        .single();
  }

  private long cardCount(Request r) {
    return jdbc.sql("SELECT count(*) FROM cards WHERE cardholder_id = ?")
        .param(r.cardholderId())
        .query(Long.class)
        .single();
  }

  private List<JsonNode> completedEvents(Request r) {
    return jdbc
        .sql(
            """
            SELECT payload::text FROM outbox_events
            WHERE event_type = 'IssuanceCompleted' AND payload->>'issuanceRequestId' = ?
            """)
        .param(r.issuanceRequestId().toString())
        .query(String.class)
        .list()
        .stream()
        .map(this::readTree)
        .toList();
  }

  private String correlationOfCompletedEvent(Request r) {
    return jdbc.sql(
            """
            SELECT correlation_id FROM outbox_events
            WHERE event_type = 'IssuanceCompleted' AND payload->>'issuanceRequestId' = ?
            """)
        .param(r.issuanceRequestId().toString())
        .query(String.class)
        .list()
        .getFirst();
  }

  private List<JsonNode> awaitCompletedEvents(Request r, int atLeast) {
    await().atMost(Duration.ofSeconds(20)).until(() -> completedEvents(r).size() >= atLeast);
    return completedEvents(r);
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
