package com.anssa8bu.ssafy.training.application.worker;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;

import com.anssa8bu.ssafy.common.lease.LeaseTokenService;
import com.anssa8bu.ssafy.common.redis.RedisTtlProperties;
import com.anssa8bu.ssafy.common.redis.dto.TrainingLeaseCache;
import com.anssa8bu.ssafy.common.redis.repository.TrainingLeaseCacheRepository;
import com.anssa8bu.ssafy.episode.application.EpisodeDownloadManifestService;
import com.anssa8bu.ssafy.common.storage.PresignedDownloadService;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import com.anssa8bu.ssafy.training.application.claim.TrainingAllocationGate;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingClaimService {
  private final TrainingJobRepository jobs;
  private final SkillRepository skills; private final EpisodeDownloadManifestService manifests;
  private final LeaseTokenService tokens; private final Clock clock; private final Duration lease;
  private final int heartbeatSeconds;
  private final TrainingJobClaimRepository candidates; private final TrainingAllocationGate gate;
  private final TrainingLeaseCacheRepository leaseCache; private final RedisTtlProperties redisTtl;
  private final SkillVersionRepository versions; private final PresignedDownloadService downloads;
  private final Duration checkpointDownloadTtl;
  public TrainingClaimService(TrainingJobRepository jobs,
      SkillRepository skills, EpisodeDownloadManifestService manifests, LeaseTokenService tokens,
      Clock clock, @Value("${app.training-worker.lease-duration}") Duration lease,
      @Value("${app.training-worker.heartbeat-interval-seconds}") int heartbeatSeconds,
      TrainingJobClaimRepository candidates, TrainingAllocationGate gate,
      TrainingLeaseCacheRepository leaseCache, RedisTtlProperties redisTtl,
      SkillVersionRepository versions, PresignedDownloadService downloads,
      @Value("${app.checkpoint-storage.presign-ttl}") Duration checkpointDownloadTtl) {
    this.jobs=jobs; this.skills=skills; this.manifests=manifests;
    this.tokens=tokens; this.clock=clock; this.lease=lease; this.heartbeatSeconds=heartbeatSeconds;
    this.candidates=candidates;this.gate=gate;this.leaseCache=leaseCache;this.redisTtl=redisTtl;
    this.versions=versions;this.downloads=downloads;this.checkpointDownloadTtl=checkpointDownloadTtl;
  }
  @Transactional
  public Optional<ClaimResponse> claim(ClaimRequest request) {
    Instant now=clock.instant();
    Optional<TrainingJob> existing=
        jobs.findFirstByAssignedWorkerIdAndGpuIndexAndLeaseExpiresAtAfterAndStatusInOrderByCreatedAt(
            request.workerId(), request.gpuIndex(), now, TrainingAllocationGate.ACTIVE);
    boolean resumed=existing.isPresent();
    gate.validate(request,resumed);
    TrainingJob job=existing.orElseGet(() -> candidates.lockNextCandidate()
        .flatMap(jobs::findById).orElse(null));
    if (job==null) return Optional.empty();
    String token=tokens.issue(); Instant expiry=now.plus(lease);
    if (resumed) job.rotateLease(tokens.hash(token), expiry);
    else job.claim(request.workerId(), request.storageNodeId(), request.gpuIndex(), request.gitRev(),
        tokens.hash(token), expiry);
    var skill=skills.findById(job.getSkillId()).orElseThrow();
    var data=new JobData(job.getId(), skill.getSkillKey(), job.getInputMode(), job.getTrainingMode(),
        baseModel(job), job.getDatasetQuery(), job.getInputSnapshotSha256(), manifests.create(job.getId()));
    cache(job,expiry);
    return Optional.of(new ClaimResponse(data,job.getAttempt(),token,expiry,heartbeatSeconds));
  }
  private BaseModelData baseModel(TrainingJob job) {
    if (job.getTrainingMode() == TrainingMode.FROM_SCRATCH) return null;
    SkillVersion version=versions.findById(job.getBaseSkillVersionId()).orElseThrow();
    var signed=downloads.issue(version.getCheckpointBucket(),version.getCheckpointKey(),checkpointDownloadTtl);
    return new BaseModelData(version.getId(),version.getVersion(),signed.url(),signed.expiresAt(),
        version.getCheckpointKey().toLowerCase(java.util.Locale.ROOT).endsWith(".tgz")
            ? "application/x-tar"
            : "application/octet-stream",
        version.getCheckpointBytes(),version.getCheckpointSha256(),
        version.getArchitecture(),version.getFramework(),version.getFrameworkVersion(),
        version.getContractVersion());
  }
  private void cache(TrainingJob job,Instant expiry) {
    try {
      leaseCache.save(job.getId(),new TrainingLeaseCache(1,job.getId(),job.getAttempt(),
          job.getAssignedWorkerId(),job.getLeaseTokenHash(),expiry),redisTtl.trainingLease());
    } catch (RuntimeException ignored) { }
  }
}
