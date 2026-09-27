package com.anssa8bu.ssafy.checkpoint.application;

import static com.anssa8bu.ssafy.checkpoint.dto.CheckpointApiModels.*;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.model.entity.*;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.training.application.worker.TrainingLeaseValidator;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.entity.TrainingJobStatus;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckpointCompleteService {
  private final TrainingJobRepository jobs; private final SkillVersionRepository versions;
  private final TrainingLeaseValidator leases; private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final IdempotencyGuard idempotency;
  private final CheckpointMetadataValidator metadataValidator;
  private final com.anssa8bu.ssafy.model.repository.RobotProfileRepository robotProfiles;
  public CheckpointCompleteService(TrainingJobRepository jobs, SkillVersionRepository versions,
      TrainingLeaseValidator leases, ApplicationEventPublisher publisher, Clock clock,
      IdempotencyGuard idempotency, CheckpointMetadataValidator metadataValidator,
      com.anssa8bu.ssafy.model.repository.RobotProfileRepository robotProfiles) {
    this.jobs=jobs;this.versions=versions;this.leases=leases;this.publisher=publisher;this.clock=clock;
    this.idempotency=idempotency;
    this.metadataValidator=metadataValidator;this.robotProfiles=robotProfiles;
  }
  @Transactional
  public CompleteResponse complete(UUID jobId, String idempotencyKey, CompleteRequest request) {
    String path="/internal/training-jobs/"+jobId+"/checkpoint-complete";
    UUID actor=jobId;
    var claim=idempotency.claim("GPU",actor,"POST",path,idempotencyKey,request);
    TrainingJob job=jobs.findByIdForUpdate(jobId).orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    leases.validate(job, null, request.attempt(), request.leaseToken(), clock.instant());
    if (claim.existingResourceId()!=null) {
      SkillVersion existing=versions.findById(claim.existingResourceId())
          .orElseThrow(() -> new ResourceNotFoundException("SkillVersion"));
      return new CompleteResponse(existing.getId(), existing.getStatus().name());
    }
    if (job.getStatus() == TrainingJobStatus.CANCEL_REQUESTED
        || job.getStatus() == TrainingJobStatus.CANCELED)
      throw new BusinessConflictException("TRAINING_CANCELED", "취소된 학습의 모델을 등록할 수 없습니다.");
    SkillVersion version=versions.findById(request.skillVersionId())
        .filter(v -> v.getTrainingJobId().equals(jobId))
        .orElseThrow(() -> new ResourceNotFoundException("SkillVersion"));
    var robot=robotProfiles.findById(request.metadata().robotProfileId())
        .orElseThrow(() -> new ResourceNotFoundException("RobotProfile"));
    metadataValidator.validate(request.metadata(),robot);
    if (version.getCheckpointBytes()!=request.checkpoint().sizeBytes()
        || !version.getCheckpointSha256().equals(request.checkpoint().sha256())
        || !metadataValidator.matches(version,request.metadata()))
      throw new BusinessConflictException("CHECKPOINT_RESERVATION_CONFLICT", "CKPT 완료 정보가 예약값과 다릅니다.");
    version.beginValidation(); job.markModelValidating();
    publisher.publishEvent(new CheckpointValidationRequested(version.getId()));
    if (claim.existingResourceId()==null)
      idempotency.complete("GPU",actor,"POST",path,claim,202,"SKILL_VERSION",version.getId());
    return new CompleteResponse(version.getId(), version.getStatus().name());
  }
}
