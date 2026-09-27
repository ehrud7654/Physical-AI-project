package com.anssa8bu.ssafy.event.application;

import java.time.Instant;
import java.util.*;

public record DomainEventPayload(
    int schemaVersion,
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    UUID ownerId,
    Instant occurredAt,
    Map<String, Object> data) {
  public DomainEventPayload {
    if (schemaVersion < 1) throw new IllegalArgumentException();
    Objects.requireNonNull(eventId);
    Objects.requireNonNull(occurredAt);
    data = Map.copyOf(data);
  }
}
