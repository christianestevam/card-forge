package com.rpe.cardforge.cardholder.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI openApi(@Value("${cardforge.openapi.token-url}") String tokenUrl) {
    return new OpenAPI()
        .info(
            new Info()
                .title("CardForge cardholder-service")
                .version("1.0")
                .description(
                    "202 no cadastro significa aceito e rastreável, não cartão emitido: o desfecho"
                        + " aparece na consulta consolidada."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "oauth2",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                            new OAuthFlows()
                                .clientCredentials(
                                    new OAuthFlow()
                                        .tokenUrl(tokenUrl)
                                        .scopes(
                                            new Scopes()
                                                .addString("cardholders:read", "Read cardholders")
                                                .addString(
                                                    "cardholders:write",
                                                    "Register cardholders"))))))
        .addSecurityItem(new SecurityRequirement().addList("oauth2"));
  }
}
