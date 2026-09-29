package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.application.ProductCatalog;
import com.rpe.cardforge.card.domain.ProductState;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente do product-service. Classifica as respostas: 404 e CANCELED são fatos de negócio;
 * timeout, 5xx e conexão recusada são indisponibilidade; 401, 403 e contrato inválido são
 * configuração.
 */
@Component
class HttpProductCatalog implements ProductCatalog {

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
                  return toFound(productId, response.bodyTo(ProductDto.class));
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
      if ("invalid_client".equals(code)
          || "unauthorized_client".equals(code)
          || "invalid_scope".equals(code)) {
        throw new CatalogMisconfiguredException("Token request rejected: " + code, e);
      }
      throw new CatalogUnavailableException("Token endpoint unavailable: " + code, e);
    } catch (RestClientException e) {
      throw new CatalogMisconfiguredException("Catalog response out of contract", e);
    }
  }

  /**
   * Contrato remoto: status só ACTIVE ou CANCELED e BIN com 8 dígitos. O status é lido como texto,
   * para que estados internos do cache (como NOT_FOUND) ou valores desconhecidos não passem por
   * fato de negócio: qualquer desvio é erro de contrato.
   */
  private static Found toFound(UUID productId, ProductDto dto) {
    if (dto == null
        || !productId.equals(dto.id())
        || dto.bin() == null
        || !dto.bin().matches("\\d{8}")
        || !REMOTE_STATUSES.contains(String.valueOf(dto.status()))) {
      throw new CatalogMisconfiguredException("Catalog response out of contract", null);
    }
    return new Found(dto.id(), dto.name(), dto.bin(), ProductState.valueOf(dto.status()));
  }

  private static final Set<String> REMOTE_STATUSES = Set.of("ACTIVE", "CANCELED");

  record ProductDto(UUID id, String name, String bin, String status) {}
}
