package com.rpe.cardforge.product.domain;
import java.time.Instant;
import java.util.UUID;
public record Product(UUID id,String name,String description,String bin,String status,Instant createdAt,Instant updatedAt) {}
