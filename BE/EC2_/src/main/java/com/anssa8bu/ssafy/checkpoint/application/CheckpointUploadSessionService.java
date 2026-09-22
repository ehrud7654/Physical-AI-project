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
  private final TrainingLeaseValidator leases;
  private final PresignedUploadService uploads; private final CheckpointStorageKeyFactory keys;
  private final Clock clock; private final String bucket; private final Duration ttl;
  private final IdempotencyGuard idempotency;
  private final CheckpointMetadataValidator metadataValidator;
  public CheckpointUploadSessionService(TrainingJobRepository jobs, SkillVersionRepository versions,
      SkillRepository skills, RobotProfileRepository robots,
      TrainingLeaseValidator leases, PresignedUploadService uploads, CheckpointStorageKeyFactory keys,
      Clock clock, @Value("${app.checkpoint-storage.bucket}") String bucket,
      @Value("${app.checkpoint-storage.presign-ttl}") Duration ttl, IdempotencyGuard idempotency,
      CheckpointMetadataValidator metadataValidator) {
    this.jobs=jobs;this.versions=versions;this.skills=skills;this.robots=robots;
    this.leases=leases;this.uploads=uploads;this.keys=keys;this.clock=clock;this.bucket=bucket;this.ttl=ttl;
    this.idempotency=idempotency;
    this.metadataValidator=metadataValidator;
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
    assertReservation(job, version, request);
    var signed=uploads.issue(version.getCheckpointBucket(), version.getCheckpointKey(),
        request.contentType(), request.sizeBytes(), request.sha256(), ttl);
    String checksum=Base64.getEncoder().encodeToString(HexFormat.of().parseHex(request.sha256()));
    if (claim.existingResourceId()==null)
      idempotency.complete("GPU",actor,"POST",path,claim,201,"SKILL_VERSION",version.getId());
    return new UploadSessionResponse(version.getId(), version.getVersion(), "PUT", signed.url(),
        signed.expiresAt(), Map.of(
            "Content-Type", request.contentType(),
            "x-amz-checksum-sha256", checksum,
            "If-None-Match", "*"));
  }
  private SkillVersion create(TrainingJob job, UploadSessionRequest request) {
    var skill=skills.findByIdForUpdate(job.getSkillId()).orElseThrow();
    Metadata m=request.metadata();
    var robot=robots.findById(m.robotProfileId()).orElseThrow(() -> new ResourceNotFoundException("RobotProfile"));
    metadataValidator.validate(m, robot);
    validateFile(request);
    int next=versions.findFirstBySkillIdOrderByVersionDesc(job.getSkillId()).map(v->v.getVersion()+1).orElse(1);
    String key=keys.create(job.getOwnerId(), skill.getSkillKey(), next, request.modelCompletedAt());
    SkillVersion value=new SkillVersion(UUID.randomUUID(), job.getSkillId(), job.getId(),
        m.robotProfileId(), next, bucket,
        key, request.sha256(), request.sizeBytes(), m.architecture(), m.framework(),
        m.frameworkVersion(), m.contractVersion(), m.actionSpace(), m.actionSpec(), m.runtimeSpec(),
        m.nParams(), m.controlRateHz(),
        m.cameraNames(), m.inputSchema(), m.outputSchema(), job.getBaseSkillVersionId());
    job.markModelUploading(request.modelCompletedAt());
    return versions.save(value);
  }
  private void assertReservation(TrainingJob job, SkillVersion v, UploadSessionRequest r) {
    Metadata m=r.metadata();
    validateFile(r);
    var robot=robots.findById(m.robotProfileId()).orElseThrow(() -> new ResourceNotFoundException("RobotProfile"));
    metadataValidator.validate(m, robot);
    if (job.getModelCompletedAt()==null || !job.getModelCompletedAt().equals(r.modelCompletedAt())
        || !metadataValidator.matches(v,m)
        || v.getCheckpointBytes()!=r.sizeBytes() || !v.getCheckpointSha256().equals(r.sha256()))
      throw new BusinessConflictException("CHECKPOINT_RESERVATION_CONFLICT", "기존 CKPT 예약 정보와 다릅니다.");
  }
  private void validateFile(UploadSessionRequest request) {
    if (!request.filename().toLowerCase(Locale.ROOT).endsWith(".ckpt")
        || !"application/octet-stream".equalsIgnoreCase(request.contentType()))
      throw new BusinessConflictException("INVALID_CHECKPOINT_FILE", "CKPT 파일명과 contentType이 올바르지 않습니다.");
    Instant now=clock.instant();
    if (request.modelCompletedAt().isAfter(now.plus(Duration.ofMinutes(5))))
      throw new BusinessConflictException("INVALID_MODEL_COMPLETED_AT", "모델 완료 시각이 허용 범위를 벗어났습니다.");
  }
}
