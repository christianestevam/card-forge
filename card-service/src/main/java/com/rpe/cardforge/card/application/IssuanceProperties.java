package com.rpe.cardforge.card.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param eligibilityWindow idade máxima da observação ACTIVE que autoriza a emissão
 * @param retryInitialDelay primeira retentativa após falha técnica
 * @param retryMaxDelay teto rígido do backoff, aplicado depois do jitter
 * @param maxPanAttempts tentativas de PAN antes de declarar falha técnica
 */
@Validated
@ConfigurationProperties("cardforge.issuance")
public record IssuanceProperties(
    @DefaultValue("5m") @NotNull Duration eligibilityWindow,
    @DefaultValue("30s") @NotNull Duration retryInitialDelay,
    @DefaultValue("5m") @NotNull Duration retryMaxDelay,
    @DefaultValue("20") int maxPanAttempts,
    @NotBlank String requestedQueue,
    @NotBlank String requestedDeadLetterQueue,
    @NotBlank String completedQueue) {}
