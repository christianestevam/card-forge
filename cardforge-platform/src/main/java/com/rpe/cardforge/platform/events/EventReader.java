package com.rpe.cardforge.platform.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lê o envelope e o payload de uma mensagem, aceitando só o tipo e a versão esperados. */
public class EventReader {

  private final ObjectMapper objectMapper;

  public EventReader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> T read(String body, String expectedType, int expectedVersion, Class<T> payloadType) {
    EventEnvelope envelope;
    try {
      envelope = objectMapper.readValue(body, EventEnvelope.class);
    } catch (JsonProcessingException e) {
      throw new InvalidEventException("Message body is not a valid event envelope", e);
    }
    if (envelope == null) {
      throw new InvalidEventException("Message body is null");
    }
    if (!expectedType.equals(envelope.eventType())) {
      throw new InvalidEventException("Unexpected eventType " + envelope.eventType());
    }
    if (envelope.eventVersion() != expectedVersion) {
      throw new InvalidEventException("Unsupported eventVersion " + envelope.eventVersion());
    }
    if (envelope.payload() == null || envelope.payload().isNull()) {
      throw new InvalidEventException("Missing payload");
    }
    try {
      return objectMapper.treeToValue(envelope.payload(), payloadType);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new InvalidEventException("Payload does not match " + payloadType.getSimpleName(), e);
    }
  }
}
