package com.rpe.cardforge.product.web;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpe.cardforge.platform.security.ResourceServerSupport;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
@Configuration
public class ServiceConfiguration {
 @Bean Clock clock() { return Clock.systemUTC(); }
 @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper mapper) throws Exception {
 return ResourceServerSupport.apply(http,mapper).authorizeHttpRequests(a->a
 .requestMatchers(ResourceServerSupport.PUBLIC_PATHS).permitAll()
 .requestMatchers(HttpMethod.GET,"/api/v1/products/**").hasAuthority("SCOPE_products:read")
 .requestMatchers(HttpMethod.POST,"/api/v1/products/**").hasAuthority("SCOPE_products:write")
 .anyRequest().denyAll()).build();
 }
 @Bean OpenAPI openApi() { return new OpenAPI().info(new Info().title("CardForge product-service").version("1.0"))
 .components(new Components().addSecuritySchemes("bearer",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
 .addSecurityItem(new SecurityRequirement().addList("bearer")); }
}
