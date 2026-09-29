package com.rpe.cardforge.card.config;

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
        .info(new Info().title("CardForge card-service").version("1.0"))
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
                                                .addString("cards:read", "Read cards")
                                                .addString("cards:write", "Change cards"))))))
        .addSecurityItem(new SecurityRequirement().addList("oauth2"));
  }
}
