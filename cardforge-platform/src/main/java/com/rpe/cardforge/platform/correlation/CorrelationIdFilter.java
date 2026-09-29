package com.rpe.cardforge.platform.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lê ou gera o {@code X-Correlation-Id}, coloca no MDC e ecoa na resposta. */
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try (CorrelationId.Scope ignored =
        CorrelationId.open(request.getHeader(CorrelationId.HEADER))) {
      response.setHeader(CorrelationId.HEADER, CorrelationId.current());
      chain.doFilter(request, response);
    }
  }
}
