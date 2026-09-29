package com.rpe.cardforge.platform.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope comum dos eventos (C6). O {@code eventId} identifica a linha de outbox e muda a cada
 * publicação; a identidade de negócio fica no payload.
 */
public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String correlationId,
    JsonNode payload) {}
