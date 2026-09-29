package com.rpe.cardforge.platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.outbox.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class OutboxIT {
 @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.11-alpine");
 @Container static LocalStackContainer aws = new LocalStackContainer(org.testcontainers.utility.DockerImageName.parse("localstack/localstack:4.14.0")).withServices(LocalStackContainer.Service.SQS);
 @Test void rollbackDoesNotPublishAndCommittedEventIsDelivered() throws Exception {
 var ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
 Flyway.configure().dataSource(ds).locations("classpath:db/platform").load().migrate();
 var jdbc=JdbcClient.create(ds); var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
 var mapper=new ObjectMapper().findAndRegisterModules();
 var writer=new OutboxWriter(jdbc,mapper,Clock.systemUTC());
 try(var sqs=SqsAsyncClient.builder().endpointOverride(aws.getEndpointOverride(LocalStackContainer.Service.SQS)).region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test","test"))).build()) {
 String url=sqs.createQueue(b->b.queueName("outbox-test")).get().queueUrl();
 tx.executeWithoutResult(s->{writer.write("outbox-test","Test",1,UUID.randomUUID(),Map.of("id","safe"));s.setRollbackOnly();});
 assertThat(jdbc.sql("select count(*) from outbox_events").query(Long.class).single()).isZero();
 tx.executeWithoutResult(s->writer.write("outbox-test","Test",1,UUID.randomUUID(),Map.of("id","safe")));
 var relay=new OutboxRelay(jdbc,tx,sqs,mapper,new OutboxProperties(true,10,500,Duration.ofSeconds(5)));
 assertThat(relay.publishBatch()).isEqualTo(1);
 assertThat(sqs.receiveMessage(b->b.queueUrl(url).waitTimeSeconds(2)).get().messages()).hasSize(1);
 assertThat(relay.publishBatch()).isZero();
 }
 }
}
