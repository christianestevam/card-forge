package com.rpe.cardforge.product.infrastructure;
import com.rpe.cardforge.product.application.Products;
import com.rpe.cardforge.product.domain.Product;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class JpaProducts implements Products {
 private final EntityManager em;
 public JpaProducts(EntityManager em) { this.em=em; }
 public Product save(Product p) { em.persist(new ProductEntity(p));em.flush();return p; }
 public Optional<Product> find(UUID id) { return Optional.ofNullable(em.find(ProductEntity.class,id)).map(ProductEntity::toDomain); }
}
