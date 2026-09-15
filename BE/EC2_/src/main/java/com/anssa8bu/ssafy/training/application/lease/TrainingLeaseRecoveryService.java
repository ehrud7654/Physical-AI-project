package com.anssa8bu.ssafy.training.application.lease;

import com.anssa8bu.ssafy.common.config.TrainingAllocationProperties;
import com.anssa8bu.ssafy.common.redis.repository.TrainingLeaseCacheRepository;
import com.anssa8bu.ssafy.event.application.*;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingLeaseRecoveryService {
  private final TrainingJobRepository jobs; private final TrainingAllocationProperties policy;
  private final TrainingLeaseCacheRepository cache; private final OutboxEventFactory factory;
  private final OutboxEventRepository outbox; private final Clock clock;
  public TrainingLeaseRecoveryService(TrainingJobRepository jobs, TrainingAllocationProperties policy,
      TrainingLeaseCacheRepository cache, OutboxEventFactory factory,
      OutboxEventRepository outbox, Clock clock) {
    this.jobs=jobs;this.policy=policy;this.cache=cache;this.factory=factory;this.outbox=outbox;this.clock=clock;
  }
  @Transactional
  public int recoverBatch() {
    Instant now=clock.instant();
    List<UUID> ids=jobs.lockExpiredLeaseIds(now,50);
    for (UUID id:ids) {
      var job=jobs.findById(id).orElseThrow();
      job.recoverExpiredLease(policy.maxAttempts(),now);
      try { cache.delete(id); } catch (RuntimeException ignored) { }
      outbox.save(factory.create(new DomainEventPayload(1,UUID.randomUUID().toString(),
          "TRAINING_LEASE_RECOVERED","TRAINING_JOB",id.toString(),job.getOwnerId(),now,
          Map.of("status",job.getStatus().name(),"attempt",job.getAttempt())),null));
    }
    return ids.size();
  }
}
