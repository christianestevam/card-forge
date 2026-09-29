package com.rpe.cardforge.platform.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/** Registra o customizador de erros do OpenAPI quando o springdoc está presente. */
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
public class OpenApiAutoConfiguration {

  @Bean
  ProblemResponsesCustomizer problemResponsesCustomizer() {
    return new ProblemResponsesCustomizer();
  }
}
