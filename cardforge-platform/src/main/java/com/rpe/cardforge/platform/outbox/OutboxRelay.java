package com.rpe.cardforge.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.correlation.CorrelationId;
import com.rpe.cardforge.platform.events.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;

/**
 * Publica os eventos pendentes do outbox. Cada lote é selecionado com {@code FOR UPDATE SKIP
 * LOCKED}, enviado por {@code SendMessageBatch} com timeout curto mantendo o lock, e só os eventos
 * aceitos individualmente são marcados como enviados. Resposta perdida pode gerar republicação;
 * os consumidores são idempotentes.
 */
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final JdbcClient jdbc;
  private final TransactionTemplate transactionTemplate;
  private final SqsAsyncClient sqs;
  private final ObjectMapper objectMapper;
  private final OutboxProperties properties;
  private final Map<String, String> queueUrls = new ConcurrentHashMap<>();

  public OutboxRelay(
      JdbcClient jdbc,
      TransactionTemplate transactionTemplate,
      SqsAsyncClient sqs,
      ObjectMapper objectMapper,
      OutboxProperties properties) {
    this.jdbc = jdbc;
    this.transactionTemplate = transactionTemplate;
    this.sqs = sqs;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${cardforge.outbox.poll-interval-millis:500}")
  public void publishPending() {
    // Esvazia a fila enquanto os lotes vierem cheios e forem totalmente aceitos.
    while (publishBatch() == properties.batchSize()) {
      // próximo lote
    }
  }

  /**
   * Publica um lote.
   *
   * @return quantos eventos foram marcados como enviados
   */
  public int publishBatch() {
    Integer sent = transactionTemplate.execute(status -> publishLockedBatch());
    return sent == null ? 0 : sent;
  }

  private int publishLockedBatch() {
    List<PendingEvent> batch =
        jdbc.sql(
                """
                SELECT event_id, destination, event_type, event_version, occurred_at,
                       correlation_id, payload::text AS payload
                FROM outbox_events
                WHERE sent_at IS NULL
                ORDER BY occurred_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """)
            .param(properties.batchSize())
            .query(
                (rs, rowNum) ->
                    new PendingEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("destination"),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("correlation_id"),
                        rs.getString("payload")))
            .list();
    if (batch.isEmpty()) {
      return 0;
    }

    Map<String, List<PendingEvent>> byDestination = new LinkedHashMap<>();
    batch.forEach(e -> byDestination.computeIfAbsent(e.destination(), d -> new ArrayList<>()).add(e));

    List<UUID> accepted = new ArrayList<>();
    byDestination.forEach((destination, events) -> accepted.addAll(send(destination, events)));

    if (!accepted.isEmpty()) {
      jdbc.sql("UPDATE outbox_events SET sent_at = now() WHERE event_id IN (:ids)")
          .param("ids", accepted)
          .update();
    }
    return accepted.size();
  }

  private List<UUID> send(String destination, List<PendingEvent> events) {
    try {
      String queueUrl = queueUrl(destination);
      List<SendMessageBatchRequestEntry> entries = events.stream().map(this::toEntry).toList();
      SendMessageBatchResponse response =
          sqs.sendMessageBatch(
                  b ->
                      b.queueUrl(queueUrl)
                          .entries(entries)
                          .overrideConfiguration(
                              o -> o.apiCallTimeout(properties.sendTimeout())))
              .get(properties.sendTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS);
      if (response.hasFailed() && !response.failed().isEmpty()) {
        log.warn(
            "Outbox: {} of {} events rejected by SQS for {}; they stay pending",
            response.failed().size(),
            events.size(),
            destination);
      }
      return response.successful().stream()
          .map(SendMessageBatchResultEntry::id)
          .map(UUID::fromString)
          .toList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      log.warn(
          "Outbox: SQS unavailable for {}; {} events stay pending: {}",
          destination,
          events.size(),
          e.toString());
      return List.of();
    }
  }

  private String queueUrl(String destination)
      throws ExecutionException, InterruptedException, TimeoutException {
    String cached = queueUrls.get(destination);
    if (cached != null) {
      return cached;
    }
    String url =
        sqs.getQueueUrl(
                b ->
                    b.queueName(destination)
                        .overrideConfiguration(o -> o.apiCallTimeout(properties.sendTimeout())))
            .get(properties.sendTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS)
            .queueUrl();
    queueUrls.put(destination, url);
    return url;
  }

  private SendMessageBatchRequestEntry toEntry(PendingEvent event) {
    try {
      EventEnvelope envelope =
          new EventEnvelope(
              event.eventId(),
              event.eventType(),
              event.eventVersion(),
              event.occurredAt(),
              event.correlationId(),
              objectMapper.readTree(event.payload()));
      return SendMessageBatchRequestEntry.builder()
          .id(event.eventId().toString())
          .messageBody(objectMapper.writeValueAsString(envelope))
          .messageAttributes(
              Map.of(
                  CorrelationId.MESSAGE_ATTRIBUTE, stringAttribute(event.correlationId()),
                  "eventType", stringAttribute(event.eventType())))
          .build();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored outbox payload is not valid JSON", e);
    }
  }

  private static MessageAttributeValue stringAttribute(String value) {
    return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
  }

  private record PendingEvent(
      UUID eventId,
      String destination,
      String eventType,
      int eventVersion,
      java.time.Instant occurredAt,
      String correlationId,
      String payload) {}
}
