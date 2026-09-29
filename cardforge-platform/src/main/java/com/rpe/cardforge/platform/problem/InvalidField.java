package com.rpe.cardforge.platform.problem;

/** Violação de um campo da requisição, listada em {@code invalidFields}. */
public record InvalidField(String field, String rule, String message) {}
