package com.rpe.cardforge.product.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateProductRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description,
    @NotNull @Pattern(regexp = "\\d{8}", message = "must have exactly 8 numeric digits")
        String bin) {}
