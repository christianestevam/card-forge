package com.rpe.cardforge.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventReaderTest {

  record Payload(UUID id) {}

  private final EventReader reader =
      new EventReader(new ObjectMapper().registerModule(new JavaTimeModule()));

  private static String envelope(String type, int version, String payload) {
    return """
        {"eventId":"%s","eventType":"%s","eventVersion":%d,
         "occurredAt":"2026-09-29T10:00:00Z","correlationId":"c","payload":%s}
        """
        .formatted(UUID.randomUUID(), type, version, payload);
  }

  @Test
  void readsExpectedPayload() {
    UUID id = UUID.randomUUID();
    Payload p = reader.read(envelope("T", 1, "{\"id\":\"" + id + "\"}"), "T", 1, Payload.class);
    assertThat(p.id()).isEqualTo(id);
  }

  @Test
  void rejectsUnknownVersionTypeAndGarbage() {
    assertThatThrownBy(() -> reader.read(envelope("T", 2, "{}"), "T", 1, Payload.class))
        .isInstanceOf(InvalidEventException.class);
    assertThatThrownBy(() -> reader.read(envelope("X", 1, "{}"), "T", 1, Payload.class))
        .isInstanceOf(InvalidEventException.class);
    assertThatThrownBy(() -> reader.read("not json", "T", 1, Payload.class))
        .isInstanceOf(InvalidEventException.class);
    assertThatThrownBy(() -> reader.read(envelope("T", 1, "null"), "T", 1, Payload.class))
        .isInstanceOf(InvalidEventException.class);
  }
}
