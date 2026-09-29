package com.rpe.cardforge.cardholder.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Portador. Dados pessoais (CPF, data de nascimento) nunca aparecem em {@link #toString()}. */
public final class Cardholder {

  public static final int MIN_AGE = 18;
  public static final int MAX_AGE = 120;
  public static final int MIN_NAME_LENGTH = 3;
  public static final int MAX_NAME_LENGTH = 120;

  private final UUID id;
  private final Cpf cpf;
  private final String fullName;
  private final LocalDate birthDate;
  private final UUID productId;
  private final CardholderStatus status;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Long version;

  public Cardholder(
      UUID id,
      Cpf cpf,
      String fullName,
      LocalDate birthDate,
      UUID productId,
      CardholderStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = Objects.requireNonNull(id);
    this.cpf = Objects.requireNonNull(cpf);
    this.fullName = Objects.requireNonNull(fullName);
    this.birthDate = Objects.requireNonNull(birthDate);
    this.productId = Objects.requireNonNull(productId);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
    this.version = version;
  }

  /**
   * Valida as regras de cadastro (BR2.1, BR2.3, BR2.4, BR2.6) e cria o portador ACTIVE (BR2.5).
   *
   * @throws CardholderValidationException com todas as violações encontradas
   */
  public static Cardholder register(
      UUID id,
      String cpf,
      String fullName,
      LocalDate birthDate,
      UUID productId,
      LocalDate today,
      Instant now) {
    List<Violation> violations = new ArrayList<>();
    Cpf.rejectionRule(cpf)
        .ifPresent(rule -> violations.add(new Violation("cpf", rule, "CPF is invalid")));
    nameRule(fullName)
        .ifPresent(rule -> violations.add(new Violation("fullName", rule, nameMessage())));
    birthDateRule(birthDate, today)
        .ifPresent(
            rule ->
                violations.add(
                    new Violation(
                        "birthDate", rule, "Cardholder must be between 18 and 120 years old")));
    if (productId == null) {
      violations.add(new Violation("productId", "REQUIRED", "productId is required"));
    }
    if (!violations.isEmpty()) {
      throw new CardholderValidationException(violations);
    }
    return new Cardholder(
        id,
        Cpf.of(cpf),
        normalizeName(fullName),
        birthDate,
        productId,
        CardholderStatus.ACTIVE,
        now,
        now,
        null);
  }

  private static java.util.Optional<String> nameRule(String fullName) {
    if (fullName == null || fullName.isBlank()) {
      return java.util.Optional.of("REQUIRED");
    }
    String normalized = normalizeName(fullName);
    if (normalized.length() < MIN_NAME_LENGTH || normalized.length() > MAX_NAME_LENGTH) {
      return java.util.Optional.of("NAME_LENGTH");
    }
    if (normalized.split(" ").length < 2) {
      return java.util.Optional.of("NAME_REQUIRES_SURNAME");
    }
    return java.util.Optional.empty();
  }

  private static String nameMessage() {
    return "Full name must have 3 to 120 characters, with first name and surname";
  }

  private static java.util.Optional<String> birthDateRule(LocalDate birthDate, LocalDate today) {
    if (birthDate == null) {
      return java.util.Optional.of("REQUIRED");
    }
    if (birthDate.isAfter(today)) {
      return java.util.Optional.of("BIRTH_DATE_IN_FUTURE");
    }
    int age = Period.between(birthDate, today).getYears();
    if (age < MIN_AGE) {
      return java.util.Optional.of("MINIMUM_AGE");
    }
    if (age > MAX_AGE) {
      return java.util.Optional.of("MAXIMUM_AGE");
    }
    return java.util.Optional.empty();
  }

  private static String normalizeName(String fullName) {
    return fullName.strip().replaceAll("\\s+", " ");
  }

  public UUID id() {
    return id;
  }

  public Cpf cpf() {
    return cpf;
  }

  public String fullName() {
    return fullName;
  }

  public LocalDate birthDate() {
    return birthDate;
  }

  public UUID productId() {
    return productId;
  }

  public CardholderStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Long version() {
    return version;
  }

  @Override
  public String toString() {
    return "Cardholder[id=" + id + ", status=" + status + "]";
  }
}
