package com.anssa8bu.ssafy.training.application.worker;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;

import com.anssa8bu.ssafy.common.lease.LeaseTokenService;
import com.anssa8bu.ssafy.episode.application.EpisodeDownloadManifestService;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingClaimService {
  private static final Set<TrainingJobStatus> ACTIVE = EnumSet.of(TrainingJobStatus.COLLECTING,
      TrainingJobStatus.PREPARING, TrainingJobStatus.RUNNING, TrainingJobStatus.MODEL_UPLOADING,
      TrainingJobStatus.MODEL_VALIDATING, TrainingJobStatus.CANCEL_REQUESTED);
  private final TrainingJobRepository jobs; private final TrainingProfileRepository profiles;
  private final SkillRepository skills; private final EpisodeDownloadManifestService manifests;
  private final LeaseTokenService tokens; private final Clock clock; private final Duration lease;
  private final int heartbeatSeconds;
  public TrainingClaimService(TrainingJobRepository jobs, TrainingProfileRepository profiles,
      SkillRepository skills, EpisodeDownloadManifestService manifests, LeaseTokenService tokens,
      Clock clock, @Value("${app.training-worker.lease-duration}") Duration lease,
      @Value("${app.training-worker.heartbeat-interval-seconds}") int heartbeatSeconds) {
    this.jobs=jobs; this.profiles=profiles; this.skills=skills; this.manifests=manifests;
    this.tokens=tokens; this.clock=clock; this.lease=lease; this.heartbeatSeconds=heartbeatSeconds;
  }
  @Transactional
  public Optional<ClaimResponse> claim(ClaimRequest request) {
    Instant now=clock.instant();
    Optional<TrainingJob> existing=jobs.findFirstByAssignedWorkerIdAndLeaseExpiresAtAfterAndStatusInOrderByCreatedAt(
        request.workerId(), now, ACTIVE);
    boolean resumed=existing.isPresent();
    TrainingJob job=existing.orElseGet(() -> jobs.lockNextClaimableId().stream().findFirst()
        .flatMap(jobs::findById).orElse(null));
    if (job==null) return Optional.empty();
    TrainingProfile profile=profiles.findById(job.getTrainingProfileId()).orElseThrow();
    if (!request.supportedAlgorithms().contains(profile.getAlgorithm())) return Optional.empty();
    String token=tokens.issue(); Instant expiry=now.plus(lease);
    if (resumed) job.rotateLease(tokens.hash(token), expiry);
    else job.claim(request.workerId(), request.storageNodeId(), request.gpuIndex(), request.gitRev(),
        tokens.hash(token), expiry);
    var skill=skills.findById(job.getSkillId()).orElseThrow();
    var profileData=new TrainingProfileData(profile.getProfileKey(), profile.getVersion(),
        profile.getAlgorithm(), profile.getHyperparameters(), profile.getConfigSha256());
    var data=new JobData(job.getId(), skill.getSkillKey(), job.getInputMode(), profileData,
        job.getDatasetQuery(), job.getInputSnapshotSha256(), manifests.create(job.getId()));
    return Optional.of(new ClaimResponse(data, token, expiry, heartbeatSeconds));
  }
}
