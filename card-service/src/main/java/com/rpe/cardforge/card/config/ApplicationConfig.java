package com.rpe.cardforge.card.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.card.application.IssuanceProperties;
import com.rpe.cardforge.card.application.RetryBackoff;
import com.rpe.cardforge.card.domain.PanGenerator;
import com.rpe.cardforge.card.domain.RandomDigits;
import com.rpe.cardforge.card.infrastructure.HmacPanHasher;
import com.rpe.cardforge.platform.events.DeadLetterPublisher;
import com.rpe.cardforge.platform.events.EventReader;
import com.rpe.cardforge.platform.http.ClientCredentialsSupport;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
class ApplicationConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PanGenerator panGenerator(RandomDigits randomDigits) {
    return new PanGenerator(randomDigits);
  }

  /** A chave vem do arquivo montado (configtree); sem valor padrão. */
  @Bean
  HmacPanHasher panHasher(@Value("${cardforge.pan.hmac-key}") String key) {
    return new HmacPanHasher(key);
  }

  @Bean
  RetryBackoff retryBackoff(IssuanceProperties properties) {
    return new RetryBackoff(
        properties.retryInitialDelay().toSeconds(),
        properties.retryMaxDelay().toSeconds(),
        () -> ThreadLocalRandom.current().nextDouble());
  }

  @Bean
  EventReader eventReader(ObjectMapper objectMapper) {
    return new EventReader(objectMapper);
  }

  @Bean
  DeadLetterPublisher deadLetterPublisher(SqsAsyncClient sqs) {
    return new DeadLetterPublisher(sqs, Duration.ofSeconds(5));
  }

  @Bean
  OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService clientService,
      @Value("${cardforge.catalog.connect-timeout}") Duration connectTimeout,
      @Value("${cardforge.catalog.read-timeout}") Duration readTimeout) {
    return ClientCredentialsSupport.authorizedClientManager(
        registrations, clientService, connectTimeout, readTimeout);
  }

  @Bean
  RestClient catalogRestClient(
      RestClient.Builder builder,
      OAuth2AuthorizedClientManager manager,
      @Value("${cardforge.catalog.base-url}") String baseUrl,
      @Value("${cardforge.catalog.connect-timeout}") Duration connectTimeout,
      @Value("${cardforge.catalog.read-timeout}") Duration readTimeout) {
    return builder
        .baseUrl(baseUrl)
        .requestFactory(ClientCredentialsSupport.requestFactory(connectTimeout, readTimeout))
        .requestInterceptor(ClientCredentialsSupport.interceptor(manager, "cardforge"))
        .build();
  }
}
