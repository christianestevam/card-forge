package com.rpe.cardforge.cardholder.infrastructure;

import com.rpe.cardforge.cardholder.application.ProductCatalog;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente do product-service: 404 e CANCELED são fatos de negócio; timeout, 5xx e conexão
 * recusada são indisponibilidade; 401, 403 e contrato inválido são configuração.
 */
@Component
class HttpProductCatalog implements ProductCatalog {

  private static final Set<String> STATUSES = Set.of("ACTIVE", "CANCELED");
  private static final Set<String> CONFIGURATION_ERRORS =
      Set.of("invalid_client", "unauthorized_client", "invalid_scope");

  private final RestClient catalog;

  HttpProductCatalog(@Qualifier("catalogRestClient") RestClient catalog) {
    this.catalog = catalog;
  }

  @Override
  public Lookup lookup(UUID productId) {
    try {
      return catalog
          .get()
          .uri("/api/v1/products/{id}", productId)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 200) {
                  ProductDto dto = response.bodyTo(ProductDto.class);
                  if (dto == null
                      || !productId.equals(dto.id())
                      || dto.bin() == null
                      || !STATUSES.contains(dto.status())) {
                    throw new CatalogMisconfiguredException("Catalog response out of contract", null);
                  }
                  return new Found(dto.id(), dto.name(), dto.bin(), dto.status());
                }
                if (status == 404) {
                  return new NotFound(productId);
                }
                if (status >= 500) {
                  throw new CatalogUnavailableException("Catalog answered " + status, null);
                }
                throw new CatalogMisconfiguredException("Catalog answered " + status, null);
              });
    } catch (CatalogUnavailableException | CatalogMisconfiguredException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CatalogUnavailableException("Catalog unreachable: " + e.getMessage(), e);
    } catch (OAuth2AuthorizationException e) {
      String code = e.getError().getErrorCode();
      if (CONFIGURATION_ERRORS.contains(code)) {
        throw new CatalogMisconfiguredException("Token request rejected: " + code, e);
      }
      throw new CatalogUnavailableException("Token endpoint unavailable: " + code, e);
    } catch (RestClientException e) {
      throw new CatalogMisconfiguredException("Catalog response out of contract", e);
    }
  }

  record ProductDto(UUID id, String name, String bin, String status) {}
}
