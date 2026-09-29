package com.rpe.cardforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.problem.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 em ProblemDetail, mantendo o {@code WWW-Authenticate} da RFC 6750. */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final BearerTokenAuthenticationEntryPoint bearer =
      new BearerTokenAuthenticationEntryPoint();
  private final ObjectMapper objectMapper;

  public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    bearer.commence(request, response, ex);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        Problems.of(
            HttpStatus.UNAUTHORIZED,
            Problems.UNAUTHORIZED,
            "Unauthorized",
            "A valid bearer token is required."));
  }
}
