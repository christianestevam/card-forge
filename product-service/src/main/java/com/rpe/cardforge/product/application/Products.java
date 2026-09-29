package com.rpe.cardforge.product.application;
import com.rpe.cardforge.product.domain.Product;
import java.util.*;
public interface Products { Product save(Product product); Optional<Product> find(UUID id); }
