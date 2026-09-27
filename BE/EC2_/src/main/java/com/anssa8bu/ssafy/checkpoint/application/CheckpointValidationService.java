package com.anssa8bu.ssafy.checkpoint.application;

import com.anssa8bu.ssafy.common.storage.ObjectVerificationService;
import com.anssa8bu.ssafy.event.application.*;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.entity.TrainingJobStatus;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import com.anssa8bu.ssafy.training.repository.TrainingJobEpisodeRepository;
import com.anssa8bu.ssafy.episode.repository.EpisodeRepository;
import com.anssa8bu.ssafy.storage.repository.RetentionPolicyRepository;
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
  private final TrainingJobEpisodeRepository inputs; private final EpisodeRepository episodes;
  private final RetentionPolicyRepository retentionPolicies;
  private final CheckpointPackageValidator packageValidator;
  public CheckpointValidationService(SkillVersionRepository versions, TrainingJobRepository jobs,
      ObjectVerificationService objects, OutboxEventFactory factory, OutboxEventRepository outbox,
      Clock clock, TrainingLeaseCacheRepository leaseCache, TrainingJobEpisodeRepository inputs,
      EpisodeRepository episodes, RetentionPolicyRepository retentionPolicies,
      CheckpointPackageValidator packageValidator) { this.versions=versions;this.jobs=jobs;this.objects=objects;this.factory=factory;
    this.outbox=outbox;this.clock=clock;this.leaseCache=leaseCache;this.inputs=inputs;
    this.episodes=episodes;this.retentionPolicies=retentionPolicies;this.packageValidator=packageValidator; }
  @Transactional
  public void validate(UUID id) {
    SkillVersion version=versions.findById(id).orElseThrow();
    TrainingJob job=jobs.findByIdForUpdate(version.getTrainingJobId()).orElseThrow();
    Instant now=clock.instant();
    if (job.getStatus() == TrainingJobStatus.CANCEL_REQUESTED
        || job.getStatus() == TrainingJobStatus.CANCELED) {
      version.markFailed(now);
      job.cancel(now);
      job.releaseAssignment();
      try { leaseCache.delete(job.getId()); } catch (RuntimeException ignored) { }
      outbox.save(factory.create(new DomainEventPayload(1, UUID.randomUUID().toString(),
          "TRAINING_CANCELED", "TRAINING_JOB", job.getId().toString(), job.getOwnerId(), now,
          Map.of()), null));
      return;
    }
    try {
      objects.verify(version.getCheckpointBucket(), version.getCheckpointKey(),
          version.getCheckpointBytes(), version.getCheckpointSha256());
      packageValidator.validate(version.getCheckpointBucket(), version.getCheckpointKey());
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
    updateEpisodeRetention(job.getId(),now);
    job.releaseAssignment();
    try { leaseCache.delete(job.getId()); } catch (RuntimeException ignored) { }
  }

  private void updateEpisodeRetention(UUID jobId,Instant now) {
    var policy=retentionPolicies.findFirstByDefaultPolicyTrueAndActiveTrue()
        .orElseThrow(() -> new IllegalStateException("활성 기본 보관 정책이 없습니다."));
    var ids=inputs.findAllByIdTrainingJobIdOrderByInputOrder(jobId).stream()
        .map(input->input.getId().episodeId()).toList();
    episodes.findAllByIdIn(ids).forEach(episode->episode.markUsedByTraining(now,
        policy.getPostTrainingGraceDays(),policy.getMaxRetentionDays()));
  }
}
