package com.anssa8bu.ssafy.event.application;

import com.anssa8bu.ssafy.event.entity.OutboxEvent;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalOutboxService {
  private final OutboxEventRepository repository;
  private final OutboxEventFactory factory;

  public TransactionalOutboxService(OutboxEventRepository r, OutboxEventFactory f) {
    repository = r;
    factory = f;
  }

  @Transactional
  public <T> T execute(Supplier<T> businessChange, DomainEventPayload event, Long sequence) {
    T result = businessChange.get();
    OutboxEvent outbox = factory.create(event, sequence);
    repository.save(outbox);
    return result;
  }
}
