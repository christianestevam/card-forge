package com.rpe.cardforge.platform.problem;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Registra o handler base de ProblemDetail em serviços web. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProblemAutoConfiguration {

  @Bean
  PlatformExceptionHandler platformExceptionHandler() {
    return new PlatformExceptionHandler();
  }
}
