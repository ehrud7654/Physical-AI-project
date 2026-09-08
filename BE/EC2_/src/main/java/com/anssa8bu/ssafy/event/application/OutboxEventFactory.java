package com.anssa8bu.ssafy.event.application;

import com.anssa8bu.ssafy.event.entity.OutboxEvent;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventFactory {
  private final ObjectMapper mapper;

  public OutboxEventFactory(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public OutboxEvent create(DomainEventPayload event, Long sequence) {
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = mapper.convertValue(event, Map.class);
    return new OutboxEvent(
        UUID.randomUUID(),
        event.eventId(),
        event.aggregateType(),
        event.aggregateId(),
        event.eventType(),
        payload,
        event.occurredAt(),
        sequence);
  }
}
