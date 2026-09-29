package com.rpe.cardforge.product.web;

import com.rpe.cardforge.product.application.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  @GetMapping("/{productId}")
  ProductResponse get(@PathVariable UUID productId) {
    return ProductResponse.from(products.get(productId));
  }

  /** ACTIVE -> CANCELED; 200 sem mudança se o produto já estiver CANCELED (contrato C1). */
  @PostMapping("/{productId}/cancel")
  ProductResponse cancel(@PathVariable UUID productId) {
    return ProductResponse.from(products.cancel(productId));
  }
}
