package com.rpe.cardforge.platform.correlation;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Propaga o correlationId atual nas chamadas HTTP entre serviços. */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    request.getHeaders().set(CorrelationId.HEADER, CorrelationId.current());
    return execution.execute(request, body);
  }
}
