package com.rpe.cardforge.platform.http;

import java.time.Duration;
import java.util.List;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Chamadas entre serviços com token de client credentials. Toda chamada, inclusive a do endpoint
 * de token, tem connect timeout e read timeout explícitos.
 */
public final class ClientCredentialsSupport {

  private ClientCredentialsSupport() {}

  public static ClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return factory;
  }

  public static OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService clientService,
      Duration connect,
      Duration read) {
    RestClient tokenRestClient =
        RestClient.builder()
            .requestFactory(requestFactory(connect, read))
            .messageConverters(
                converters -> {
                  converters.clear();
                  converters.addAll(
                      List.of(
                          new FormHttpMessageConverter(),
                          new OAuth2AccessTokenResponseHttpMessageConverter()));
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build();
    RestClientClientCredentialsTokenResponseClient tokenClient =
        new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(tokenRestClient);

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(c -> c.accessTokenResponseClient(tokenClient))
            .build());
    return manager;
  }

  /**
   * Interceptor que anexa o token do registro informado. O principal é fixo (o próprio serviço),
   * para que o token não dependa do chamador da requisição original.
   */
  public static OAuth2ClientHttpRequestInterceptor interceptor(
      OAuth2AuthorizedClientManager manager, String registrationId) {
    Authentication servicePrincipal =
        new AnonymousAuthenticationToken(
            registrationId, registrationId, AuthorityUtils.createAuthorityList("ROLE_SERVICE"));
    OAuth2ClientHttpRequestInterceptor interceptor =
        new OAuth2ClientHttpRequestInterceptor(manager);
    interceptor.setClientRegistrationIdResolver(request -> registrationId);
    interceptor.setPrincipalResolver(request -> servicePrincipal);
    return interceptor;
  }
}
