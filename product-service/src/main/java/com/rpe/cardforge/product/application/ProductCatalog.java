package com.rpe.cardforge.product.application;
import com.rpe.cardforge.product.domain.Product;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProductCatalog {
 private final Products products;private final Clock clock;
 public ProductCatalog(Products p,Clock c) { products=p;clock=c; }
 @Transactional public Product create(String name,String description,String bin) {
 var now=clock.instant();return products.save(new Product(UUID.randomUUID(),name,description,bin,"ACTIVE",now,now)); }
 @Transactional(readOnly=true) public Optional<Product> find(UUID id) { return products.find(id); }
}
