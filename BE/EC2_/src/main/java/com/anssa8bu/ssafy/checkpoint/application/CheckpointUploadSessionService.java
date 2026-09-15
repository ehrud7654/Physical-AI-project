package com.anssa8bu.ssafy.checkpoint.application;

import static com.anssa8bu.ssafy.checkpoint.dto.CheckpointApiModels.*;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.storage.PresignedUploadService;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.*;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.application.worker.TrainingLeaseValidator;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckpointUploadSessionService {
  private final TrainingJobRepository jobs; private final SkillVersionRepository versions;
  private final SkillRepository skills; private final RobotProfileRepository robots;
  private final TrainingProfileRepository profiles; private final TrainingLeaseValidator leases;
  private final PresignedUploadService uploads; private final CheckpointStorageKeyFactory keys;
  private final Clock clock; private final String bucket; private final Duration ttl;
  private final IdempotencyGuard idempotency;
  public CheckpointUploadSessionService(TrainingJobRepository jobs, SkillVersionRepository versions,
      SkillRepository skills, RobotProfileRepository robots, TrainingProfileRepository profiles,
      TrainingLeaseValidator leases, PresignedUploadService uploads, CheckpointStorageKeyFactory keys,
      Clock clock, @Value("${app.checkpoint-storage.bucket}") String bucket,
      @Value("${app.checkpoint-storage.presign-ttl}") Duration ttl, IdempotencyGuard idempotency) {
    this.jobs=jobs;this.versions=versions;this.skills=skills;this.robots=robots;this.profiles=profiles;
    this.leases=leases;this.uploads=uploads;this.keys=keys;this.clock=clock;this.bucket=bucket;this.ttl=ttl;
    this.idempotency=idempotency;
  }
  @Transactional
  public UploadSessionResponse issue(UUID jobId, String idempotencyKey, UploadSessionRequest request) {
    String path="/internal/training-jobs/"+jobId+"/checkpoint-upload-session";
    UUID actor=jobId;
    var claim=idempotency.claim("GPU",actor,"POST",path,idempotencyKey,request);
    TrainingJob job=jobs.findByIdForUpdate(jobId).orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    leases.validate(job, null, request.attempt(), request.leaseToken(), clock.instant());
    Optional<SkillVersion> reserved=versions.findByTrainingJobId(jobId);
    SkillVersion version=reserved.orElseGet(() -> create(job, request));
    assertReservation(version, request);
    var signed=uploads.issue(version.getCheckpointBucket(), version.getCheckpointKey(),
        request.contentType(), request.sizeBytes(), request.sha256(), ttl);
    if (claim.existingResourceId()==null)
      idempotency.complete("GPU",actor,"POST",path,claim,201,"SKILL_VERSION",version.getId());
    return new UploadSessionResponse(version.getId(), version.getVersion(), "PUT", signed.url(),
        signed.expiresAt(), Map.of("Content-Type", request.contentType()));
  }
  private SkillVersion create(TrainingJob job, UploadSessionRequest request) {
    var skill=skills.findByIdForUpdate(job.getSkillId()).orElseThrow();
    var profile=profiles.findById(job.getTrainingProfileId()).orElseThrow();
    Metadata m=request.metadata();
    if (!profile.getProfileKey().equals(m.trainingProfileKey()) || profile.getVersion()!=m.trainingProfileVersion()
        || !job.getTrainingConfigSha256().equals(m.trainingConfigSha256()))
      throw new BusinessConflictException("TRAINING_CONFIG_MISMATCH", "학습 프로필 메타데이터가 작업과 다릅니다.");
    robots.findById(m.robotProfileId()).orElseThrow(() -> new ResourceNotFoundException("RobotProfile"));
    int next=versions.findFirstBySkillIdOrderByVersionDesc(job.getSkillId()).map(v->v.getVersion()+1).orElse(1);
    String key=keys.create(job.getOwnerId(), skill.getSkillKey(), next, request.modelCompletedAt());
    SkillVersion value=new SkillVersion(UUID.randomUUID(), job.getSkillId(), job.getId(),
        job.getTrainingProfileId(), job.getTrainingConfigSha256(), m.robotProfileId(), next, bucket,
        key, request.sha256(), request.sizeBytes(), m.architecture(), m.framework(),
        m.frameworkVersion(), m.contractVersion(), m.actionSpace(), m.nParams(), m.controlRateHz(),
        m.cameraNames(), m.inputSchema(), m.outputSchema());
    job.markModelUploading(request.modelCompletedAt());
    return versions.save(value);
  }
  private void assertReservation(SkillVersion v, UploadSessionRequest r) {
    Metadata m=r.metadata();
    if (v.getCheckpointBytes()!=r.sizeBytes() || !v.getCheckpointSha256().equals(r.sha256())
        || !v.getRobotProfileId().equals(m.robotProfileId())
        || !v.getContractVersion().equals(m.contractVersion()))
      throw new BusinessConflictException("CHECKPOINT_RESERVATION_CONFLICT", "기존 CKPT 예약 정보와 다릅니다.");
  }
}
