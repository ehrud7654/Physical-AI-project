package com.anssa8bu.ssafy.checkpoint.application;

import com.anssa8bu.ssafy.common.storage.ObjectVerificationService;
import com.anssa8bu.ssafy.event.application.*;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import com.anssa8bu.ssafy.common.redis.repository.TrainingLeaseCacheRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckpointValidationService {
  private final SkillVersionRepository versions; private final TrainingJobRepository jobs;
  private final ObjectVerificationService objects; private final OutboxEventFactory factory;
  private final OutboxEventRepository outbox; private final Clock clock;
  private final TrainingLeaseCacheRepository leaseCache;
  public CheckpointValidationService(SkillVersionRepository versions, TrainingJobRepository jobs,
      ObjectVerificationService objects, OutboxEventFactory factory, OutboxEventRepository outbox,
      Clock clock, TrainingLeaseCacheRepository leaseCache) { this.versions=versions;this.jobs=jobs;this.objects=objects;this.factory=factory;
    this.outbox=outbox;this.clock=clock;this.leaseCache=leaseCache; }
  @Transactional
  public void validate(UUID id) {
    SkillVersion version=versions.findById(id).orElseThrow();
    TrainingJob job=jobs.findByIdForUpdate(version.getTrainingJobId()).orElseThrow();
    Instant now=clock.instant();
    try {
      objects.verify(version.getCheckpointBucket(), version.getCheckpointKey(),
          version.getCheckpointBytes(), version.getCheckpointSha256());
      version.markReady(now); job.complete(version.getId(), now);
      outbox.save(factory.create(new DomainEventPayload(1, UUID.randomUUID().toString(),
          "TRAINING_SUCCEEDED", "TRAINING_JOB", job.getId().toString(), job.getOwnerId(), now,
          Map.of("skillVersionId", version.getId().toString())), null));
    } catch (RuntimeException e) {
      version.markFailed(now); job.fail("CHECKPOINT_VALIDATION_FAILED", e.getMessage(), now);
      outbox.save(factory.create(new DomainEventPayload(1, UUID.randomUUID().toString(),
          "TRAINING_FAILED", "TRAINING_JOB", job.getId().toString(), job.getOwnerId(), now,
          Map.of("code", "CHECKPOINT_VALIDATION_FAILED")), null));
    }
    job.releaseAssignment();
    try { leaseCache.delete(job.getId()); } catch (RuntimeException ignored) { }
  }
}
