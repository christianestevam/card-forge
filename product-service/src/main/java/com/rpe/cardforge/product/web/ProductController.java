package com.rpe.cardforge.product.web;

import static com.rpe.cardforge.platform.openapi.ProblemResponsesCustomizer.PROBLEM_SCHEMA;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.rpe.cardforge.platform.paging.PageMetadata;
import com.rpe.cardforge.product.application.ProductService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

  private static final String PROBLEM_JSON = APPLICATION_PROBLEM_JSON_VALUE;
  private static final String PROBLEM = PROBLEM_SCHEMA;

  private final ProductService products;

  ProductController(ProductService products) {
    this.products = products;
  }

  @ApiResponse(
      responseCode = "409",
      description = "bin-already-registered: o BIN já pertence a outro produto",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "validation-failed: bin fora de 8 dígitos numéricos ou nome ausente; ver invalidFields",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping
  ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse created =
        ProductResponse.from(products.create(request.name(), request.description(), request.bin()));
    return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
  }

  /** Lista produtos; padrão de 20 por página, máximo de 100 ({@code size} maior gera 400). */
  @GetMapping
  ProductPageResponse list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    ProductService.ProductPage result = products.list(page, size);
    return new ProductPageResponse(
        result.content().stream().map(ProductResponse::from).toList(),
        PageMetadata.of(result.page(), result.size(), result.totalElements()));
  }

  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: produto inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @GetMapping("/{productId}")
  ProductResponse get(@PathVariable UUID productId) {
    return ProductResponse.from(products.get(productId));
  }

  /**
   * Atualiza nome e descrição de produto ACTIVE. {@code bin} no corpo gera 422 bin-immutable;
   * produto CANCELED gera 409 product-canceled-read-only.
   */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: produto inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "product-canceled-read-only: produto cancelado não é editável",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "bin-immutable quando o corpo contém bin (nunca ignorado); validation-failed para nome ou descrição inválidos",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PatchMapping("/{productId}")
  ProductResponse update(@PathVariable UUID productId, @RequestBody JsonNode body) {
    ProductUpdate update = ProductUpdate.parse(body);
    return ProductResponse.from(products.update(productId, update.name(), update.description()));
  }

  /** ACTIVE -> CANCELED; 200 sem mudança se o produto já estiver CANCELED (contrato C1). */
  @ApiResponse(
      responseCode = "404",
      description = "resource-not-found: produto inexistente",
      content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM)))
  @PostMapping("/{productId}/cancel")
  ProductResponse cancel(@PathVariable UUID productId) {
    return ProductResponse.from(products.cancel(productId));
  }

  record ProductPageResponse(List<ProductResponse> content, PageMetadata page) {}
}
