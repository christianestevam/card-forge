package com.rpe.cardforge.card.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.security.ResourceServerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    ResourceServerSupport.apply(http, objectMapper)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(ResourceServerSupport.PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/cards/**")
                    .hasAuthority("SCOPE_cards:read")
                    .requestMatchers("/api/v1/cards/**")
                    .hasAuthority("SCOPE_cards:write")
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
