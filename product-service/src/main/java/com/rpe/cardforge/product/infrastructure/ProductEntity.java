package com.rpe.cardforge.product.infrastructure;
import com.rpe.cardforge.product.domain.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="products")
public class ProductEntity {
 @Id UUID id;
 String name,description,bin,status;
 Instant createdAt,updatedAt;
 @Version long version;
 protected ProductEntity() {}
 ProductEntity(Product p) { id=p.id();name=p.name();description=p.description();bin=p.bin();status=p.status();createdAt=p.createdAt();updatedAt=p.updatedAt(); }
 Product toDomain() { return new Product(id,name,description,bin,status,createdAt,updatedAt); }
}
