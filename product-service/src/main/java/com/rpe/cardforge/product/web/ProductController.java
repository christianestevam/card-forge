package com.rpe.cardforge.product.web;
import com.rpe.cardforge.product.application.ProductCatalog;
import com.rpe.cardforge.platform.problem.Problems;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
@RestController @RequestMapping("/api/v1/products")
public class ProductController {
 private final ProductCatalog catalog;
 public ProductController(ProductCatalog c) {catalog=c;}
 public record CreateProduct(@NotBlank @Size(max=120) String name,@Size(max=500) String description,@NotNull @Pattern(regexp="[0-9]{8}") String bin) {}
 @PostMapping public ResponseEntity<?> create(@Valid @RequestBody CreateProduct body) {
 try {var p=catalog.create(body.name(),body.description(),body.bin());return ResponseEntity.created(URI.create("/api/v1/products/"+p.id())).body(p);}
 catch(DataIntegrityViolationException e) {return ResponseEntity.status(409).body(Problems.of(HttpStatus.CONFLICT,"bin-already-registered","BIN already registered","Use another BIN."));} }
 @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable UUID id) {
 return catalog.find(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(()->ResponseEntity.status(404).body(Problems.notFound("Product not found."))); }
}
