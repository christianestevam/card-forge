package com.rpe.cardforge.product.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rpe.cardforge.platform.paging.PageMetadata;
import com.rpe.cardforge.product.application.ProductService;
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

  private final ProductService products;

  ProductController(ProductService products) {
    this.products = products;
  }

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

  @GetMapping("/{productId}")
  ProductResponse get(@PathVariable UUID productId) {
    return ProductResponse.from(products.get(productId));
  }

  /**
   * Atualiza nome e descrição de produto ACTIVE. {@code bin} no corpo gera 422 bin-immutable;
   * produto CANCELED gera 409 product-canceled-read-only.
   */
  @PatchMapping("/{productId}")
  ProductResponse update(@PathVariable UUID productId, @RequestBody JsonNode body) {
    ProductUpdate update = ProductUpdate.parse(body);
    return ProductResponse.from(products.update(productId, update.name(), update.description()));
  }

  /** ACTIVE -> CANCELED; 200 sem mudança se o produto já estiver CANCELED (contrato C1). */
  @PostMapping("/{productId}/cancel")
  ProductResponse cancel(@PathVariable UUID productId) {
    return ProductResponse.from(products.cancel(productId));
  }

  record ProductPageResponse(List<ProductResponse> content, PageMetadata page) {}
}
