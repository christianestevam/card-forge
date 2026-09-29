package com.rpe.cardforge.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.correlation.CorrelationId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "cardforge.outbox.enabled=true",
      "cardforge.outbox.poll-interval-millis=3600000",
      "cardforge.outbox.send-timeout=2s",
      "spring.flyway.locations=classpath:db/platform"
    })
class OutboxRelayIT {

  private static final String QUEUE = "outbox-it";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

  @Container @ServiceConnection
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withServices(LocalStackContainer.Service.SQS);

  @Autowired OutboxWriter writer;
  @Autowired OutboxRelay relay;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcClient jdbc;
  @Autowired SqsAsyncClient sqs;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    jdbc.sql("DELETE FROM outbox_events").update();
  }

  @Test
  void publishesPendingEventWithEnvelopeAndCorrelationAttribute() throws Exception {
    String queueUrl = sqs.createQueue(b -> b.queueName(QUEUE)).get().queueUrl();
    UUID aggregateId = UUID.randomUUID();

    try (CorrelationId.Scope ignored = CorrelationId.open("corr-123")) {
      tx.executeWithoutResult(
          s -> writer.write(QUEUE, "SomethingHappened", 1, aggregateId, Map.of("id", aggregateId)));
    }

    assertThat(relay.publishBatch()).isEqualTo(1);

    List<Message> messages =
        sqs.receiveMessage(
                b ->
                    b.queueUrl(queueUrl)
                        .waitTimeSeconds(5)
                        .messageAttributeNames("All"))
            .get()
            .messages();
    assertThat(messages).hasSize(1);
    JsonNode envelope = objectMapper.readTree(messages.getFirst().body());
    assertThat(envelope.get("eventType").asText()).isEqualTo("SomethingHappened");
    assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
    assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-123");
    assertThat(envelope.get("payload").get("id").asText()).isEqualTo(aggregateId.toString());
    assertThat(messages.getFirst().messageAttributes().get("correlationId").stringValue())
        .isEqualTo("corr-123");
    assertThat(pending()).isZero();
  }

  @Test
  void keepsEventPendingWhenDestinationIsUnavailable() {
    tx.executeWithoutResult(
        s -> writer.write("missing-queue", "SomethingHappened", 1, UUID.randomUUID(), Map.of()));

    assertThat(relay.publishBatch()).isZero();
    assertThat(pending()).isEqualTo(1);
  }

  @Test
  void publishesOnceTheQueueBecomesAvailable() throws Exception {
    String queue = "late-queue";
    tx.executeWithoutResult(
        s -> writer.write(queue, "SomethingHappened", 1, UUID.randomUUID(), Map.of()));
    assertThat(relay.publishBatch()).isZero();

    String queueUrl = sqs.createQueue(b -> b.queueName(queue)).get().queueUrl();
    assertThat(relay.publishBatch()).isEqualTo(1);
    String visible =
        sqs.getQueueAttributes(
                b ->
                    b.queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
            .get()
            .attributes()
            .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
    assertThat(visible).isEqualTo("1");
  }

  @Test
  void refusesToWriteOutsideTransaction() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> writer.write(QUEUE, "SomethingHappened", 1, UUID.randomUUID(), Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private long pending() {
    return jdbc.sql("SELECT count(*) FROM outbox_events WHERE sent_at IS NULL")
        .query(Long.class)
        .single();
  }
}
