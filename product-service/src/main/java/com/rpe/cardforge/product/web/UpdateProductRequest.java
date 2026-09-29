package com.rpe.cardforge.product.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Esquema documentado do {@code PATCH}. O corpo é lido como JSON bruto ({@link ProductUpdate}),
 * para distinguir campo ausente de {@code null} e detectar a presença de {@code bin}.
 */
@Schema(name = "UpdateProductRequest", description = "Atualização parcial de produto ACTIVE")
record UpdateProductRequest(
    @Schema(description = "Novo nome; ausente mantém o atual", minLength = 1, maxLength = 120)
        String name,
    @Schema(
            description = "Nova descrição; ausente mantém a atual, null limpa",
            maxLength = 500,
            nullable = true)
        String description) {}
