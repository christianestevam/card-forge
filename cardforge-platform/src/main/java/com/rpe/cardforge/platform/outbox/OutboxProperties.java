package com.rpe.cardforge.platform.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parâmetros do outbox.
 *
 * @param enabled liga o relay neste serviço
 * @param batchSize até 10 eventos por lote (limite do SendMessageBatch)
 * @param pollIntervalMillis intervalo entre varreduras
 * @param sendTimeout timeout de cada chamada à SQS
 */
@Validated
@ConfigurationProperties("cardforge.outbox")
public record OutboxProperties(
    boolean enabled,
    @DefaultValue("10") @Min(1) @Max(10) int batchSize,
    @DefaultValue("500") @Min(50) long pollIntervalMillis,
    @DefaultValue("5s") Duration sendTimeout) {}
