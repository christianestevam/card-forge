package com.rpe.cardforge.cardholder.application;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardforge.issuance")
public record CardholderProperties(
    @NotBlank String requestedQueue,
    @NotBlank String completedQueue,
    @NotBlank String completedDeadLetterQueue) {}
