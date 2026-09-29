package com.rpe.cardforge.product.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.rpe.cardforge.platform.problem.InvalidField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Corpo do {@code PATCH}: campos ausentes mantêm o valor atual. A presença de {@code bin} é
 * detectada (inclusive {@code "bin": null}) e rejeitada, nunca ignorada em silêncio.
 */
record ProductUpdate(Optional<String> name, Optional<Optional<String>> description) {

  static final int MAX_NAME = 120;
  static final int MAX_DESCRIPTION = 500;

  static ProductUpdate parse(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw new MalformedUpdateException("Request body must be a JSON object");
    }
    if (body.has("bin")) {
      throw new BinImmutableException();
    }
    List<InvalidField> invalid = new ArrayList<>();
    Optional<String> name = Optional.empty();
    if (body.has("name")) {
      JsonNode node = body.get("name");
      if (!node.isNull() && !node.isTextual()) {
        throw new MalformedUpdateException("name must be a string");
      }
      String value = node.isNull() ? null : node.asText();
      if (value == null || value.isBlank()) {
        invalid.add(new InvalidField("name", "NOT_BLANK", "must not be blank"));
      } else if (value.length() > MAX_NAME) {
        invalid.add(new InvalidField("name", "SIZE", "size must be between 1 and 120"));
      } else {
        name = Optional.of(value);
      }
    }
    Optional<Optional<String>> description = Optional.empty();
    if (body.has("description")) {
      JsonNode node = body.get("description");
      if (!node.isNull() && !node.isTextual()) {
        throw new MalformedUpdateException("description must be a string");
      }
      String value = node.isNull() ? null : node.asText();
      if (value != null && value.length() > MAX_DESCRIPTION) {
        invalid.add(new InvalidField("description", "SIZE", "size must be at most 500"));
      } else {
        description = Optional.of(Optional.ofNullable(value));
      }
    }
    if (!invalid.isEmpty()) {
      throw new InvalidUpdateException(invalid);
    }
    return new ProductUpdate(name, description);
  }

  static class BinImmutableException extends RuntimeException {
    BinImmutableException() {
      super("The BIN of a product is immutable");
    }
  }

  static class MalformedUpdateException extends RuntimeException {
    MalformedUpdateException(String message) {
      super(message);
    }
  }

  static class InvalidUpdateException extends RuntimeException {
    private final List<InvalidField> fields;

    InvalidUpdateException(List<InvalidField> fields) {
      super("Invalid product update");
      this.fields = List.copyOf(fields);
    }

    List<InvalidField> fields() {
      return fields;
    }
  }
}
