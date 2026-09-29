package com.rpe.cardforge.cardholder.domain;

/** Regra de cadastro violada por um campo. */
public record Violation(String field, String rule, String message) {}
