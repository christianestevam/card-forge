package com.rpe.cardforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

/** Configuração comum de Resource Server: stateless, JWT e erros em ProblemDetail. */
public final class ResourceServerSupport {

  /** Endpoints abertos: health, info, prometheus e a documentação OpenAPI. */
  public static final String[] PUBLIC_PATHS = {
    "/actuator/health/**",
    "/actuator/info",
    "/actuator/prometheus",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  private ResourceServerSupport() {}

  public static HttpSecurity apply(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
    ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(objectMapper);
    ProblemAccessDeniedHandler deniedHandler = new ProblemAccessDeniedHandler(objectMapper);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .oauth2ResourceServer(
            rs ->
                rs.jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler));
  }
}
