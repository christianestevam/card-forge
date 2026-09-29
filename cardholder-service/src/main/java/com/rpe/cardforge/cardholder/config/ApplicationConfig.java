package com.rpe.cardforge.cardholder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.events.DeadLetterPublisher;
import com.rpe.cardforge.platform.events.EventReader;
import com.rpe.cardforge.platform.http.ClientCredentialsSupport;
import java.time.Clock;
import java.time.Duration;
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
      @Value("${cardforge.http.connect-timeout}") Duration connectTimeout,
      @Value("${cardforge.http.read-timeout}") Duration readTimeout) {
    return ClientCredentialsSupport.authorizedClientManager(
        registrations, clientService, connectTimeout, readTimeout);
  }

  @Bean
  RestClient catalogRestClient(
      RestClient.Builder builder,
      OAuth2AuthorizedClientManager manager,
      @Value("${cardforge.catalog.base-url}") String baseUrl,
      @Value("${cardforge.http.connect-timeout}") Duration connectTimeout,
      @Value("${cardforge.http.read-timeout}") Duration readTimeout) {
    return serviceClient(builder, manager, baseUrl, connectTimeout, readTimeout);
  }

  @Bean
  RestClient cardRestClient(
      RestClient.Builder builder,
      OAuth2AuthorizedClientManager manager,
      @Value("${cardforge.cards.base-url}") String baseUrl,
      @Value("${cardforge.http.connect-timeout}") Duration connectTimeout,
      @Value("${cardforge.http.read-timeout}") Duration readTimeout) {
    return serviceClient(builder, manager, baseUrl, connectTimeout, readTimeout);
  }

  private static RestClient serviceClient(
      RestClient.Builder builder,
      OAuth2AuthorizedClientManager manager,
      String baseUrl,
      Duration connectTimeout,
      Duration readTimeout) {
    // O builder é um protótipo por injeção; clone() evita compartilhar configuração.
    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(ClientCredentialsSupport.requestFactory(connectTimeout, readTimeout))
        .requestInterceptor(ClientCredentialsSupport.interceptor(manager, "cardforge"))
        .build();
  }
}
