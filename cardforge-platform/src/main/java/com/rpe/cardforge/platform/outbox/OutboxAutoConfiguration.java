package com.rpe.cardforge.platform.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.time.Clocks;
import io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Ativa o outbox só nos serviços que publicam ({@code cardforge.outbox.enabled=true}): no
 * cardholder-service e no card-service. O product-service não o ativa.
 */
@AutoConfiguration(
    after = {
      DataSourceAutoConfiguration.class,
      JdbcClientAutoConfiguration.class,
      TransactionAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      SqsAutoConfiguration.class
    })
@ConditionalOnClass({SqsAsyncClient.class, JdbcClient.class})
@ConditionalOnProperty(prefix = "cardforge.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

  @Bean
  OutboxWriter outboxWriter(
      JdbcClient jdbc, ObjectMapper objectMapper, ObjectProvider<Clock> clock) {
    return new OutboxWriter(jdbc, objectMapper, clock.getIfAvailable(Clocks::systemUtcMicros));
  }

  @Bean
  OutboxRelay outboxRelay(
      JdbcClient jdbc,
      TransactionTemplate transactionTemplate,
      SqsAsyncClient sqs,
      ObjectMapper objectMapper,
      OutboxProperties properties) {
    return new OutboxRelay(jdbc, transactionTemplate, sqs, objectMapper, properties);
  }

  @Bean
  OutboxMetrics outboxMetrics(JdbcClient jdbc) {
    return new OutboxMetrics(jdbc);
  }
}
