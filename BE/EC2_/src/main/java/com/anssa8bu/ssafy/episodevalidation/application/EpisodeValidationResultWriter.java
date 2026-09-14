package com.anssa8bu.ssafy.episodevalidation.application;

import com.anssa8bu.ssafy.episode.entity.ArchiveCompressionMethod;
import com.anssa8bu.ssafy.episode.entity.Episode;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchive;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchiveStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelSource;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeQualityStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeSource;
import com.anssa8bu.ssafy.episode.entity.EpisodeStatus;
import com.anssa8bu.ssafy.episode.repository.EpisodeArchiveRepository;
import com.anssa8bu.ssafy.episode.repository.EpisodeRepository;
import com.anssa8bu.ssafy.episode.vo.ArchiveManifestSummary;
import com.anssa8bu.ssafy.episode.vo.CaptureMetadata;
import com.anssa8bu.ssafy.episode.vo.EpisodeIdentity;
import com.anssa8bu.ssafy.episode.vo.EpisodeSampleSummary;
import com.anssa8bu.ssafy.episode.vo.EpisodeTiming;
import com.anssa8bu.ssafy.episode.vo.QualitySummary;
import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadStatus;
import com.anssa8bu.ssafy.episodeupload.repository.EpisodeUploadSessionRepository;
import com.anssa8bu.ssafy.episodevalidation.dto.EpisodeValidationResult;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.storage.entity.RetentionPolicy;
import com.anssa8bu.ssafy.storage.repository.RetentionPolicyRepository;
import com.anssa8bu.ssafy.storage.repository.StorageNodeRepository;
import com.anssa8bu.ssafy.training.entity.TrainingJobEpisode;
import com.anssa8bu.ssafy.training.entity.TrainingJobEpisodeId;
import com.anssa8bu.ssafy.training.repository.TrainingJobEpisodeRepository;
import com.anssa8bu.ssafy.training.repository.TrainingJobRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EpisodeValidationResultWriter {
  static final UUID EPISODE_S3_STORAGE_NODE_ID =
      UUID.fromString("18000000-0000-4000-8000-000000000001");
  private static final Set<String> MANIFEST_FILES =
      Set.of("meta.json", "frames.csv", "pose.csv", "imu.csv", "gripper.csv");

  private final EpisodeUploadSessionRepository sessions;
  private final EpisodeRepository episodes;
  private final EpisodeArchiveRepository archives;
  private final SkillRepository skills;
  private final RetentionPolicyRepository retentionPolicies;
  private final StorageNodeRepository storageNodes;
  private final TrainingJobRepository jobs;
  private final TrainingJobEpisodeRepository jobEpisodes;

  public EpisodeValidationResultWriter(
      EpisodeUploadSessionRepository sessions,
      EpisodeRepository episodes,
      EpisodeArchiveRepository archives,
      SkillRepository skills,
      RetentionPolicyRepository retentionPolicies,
      StorageNodeRepository storageNodes,
      TrainingJobRepository jobs,
      TrainingJobEpisodeRepository jobEpisodes) {
    this.sessions = sessions;
    this.episodes = episodes;
    this.archives = archives;
    this.skills = skills;
    this.retentionPolicies = retentionPolicies;
    this.storageNodes = storageNodes;
    this.jobs = jobs;
    this.jobEpisodes = jobEpisodes;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ready(UUID uploadSessionId, EpisodeValidationResult result) {
    EpisodeUploadSession session = validatingSession(uploadSessionId);
    RetentionPolicy retention =
        retentionPolicies
            .findFirstByDefaultPolicyTrueAndActiveTrue()
            .orElseThrow(() -> new IllegalStateException("active default retention policy is missing"));
    if (!storageNodes.existsById(EPISODE_S3_STORAGE_NODE_ID))
      throw new IllegalStateException("episode S3 storage node is missing");
    Skill skill =
        skills
            .findBySkillKeyAndVisibleToOwner(result.skillKey(), session.getOwnerId())
            .orElseThrow(() -> new IllegalStateException("episode skill is missing"));
    EpisodeLabelStatus labelStatus = EpisodeLabelStatus.valueOf(result.labelStatus());
    EpisodeQualityStatus qualityStatus = normalizedQuality(result.qualityStatus());
    Instant now = Instant.now();
    Episode episode =
        new Episode(
            session.getEpisodeId(),
            new EpisodeIdentity(
                session.getOwnerId(),
                session.getUmiDeviceId(),
                EPISODE_S3_STORAGE_NODE_ID,
                retention.getId(),
                skill.getId(),
                session.getSessionId().toString(),
                EpisodeSource.REAL),
            result.success(),
            labelStatus,
            labelStatus == EpisodeLabelStatus.LABELED ? EpisodeLabelSource.UMI : null,
            labelStatus == EpisodeLabelStatus.LABELED ? result.endedAt() : null,
            result.contractVersion(),
            result.metaSchemaVersion(),
            result.summarySchemaVersion(),
            EpisodeStatus.READY,
            new EpisodeTiming(
                result.startedAt(),
                result.endedAt(),
                result.startMonotonicNs(),
                result.firstTimestampNs(),
                result.lastTimestampNs()),
            new EpisodeSampleSummary(
                result.frameCount(),
                result.poseSampleCount(),
                result.imuSampleCount(),
                result.gripperSampleCount(),
                result.missingFrameCount(),
                result.trackingGapCount(),
                result.trackingLostDurationNs()),
            qualityStatus,
            new CaptureMetadata(
                result.device(), result.camera(), result.coordinateSystem(), result.units()),
            new QualitySummary(session.getSummary()),
            now.plus(retention.getDefaultRetentionDays(), ChronoUnit.DAYS));
    episode.confirmValidated(now);
    episodes.save(episode);

    Map<String, String> manifestHashes =
        result.fileSha256().entrySet().stream()
            .filter(entry -> MANIFEST_FILES.contains(entry.getKey()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, Object> manifestDetails = new LinkedHashMap<>();
    manifestDetails.put("bucket", session.getBucketName());
    manifestDetails.put("frameCount", result.frameCount());
    EpisodeArchive archive =
        new EpisodeArchive(
            UUID.randomUUID(),
            episode.getId(),
            EPISODE_S3_STORAGE_NODE_ID,
            session.getObjectKey(),
            session.getSessionId() + ".zip",
            result.contractVersion(),
            session.getSizeBytes(),
            result.uncompressedSizeBytes(),
            result.entryCount(),
            session.getSha256(),
            new ArchiveManifestSummary(manifestHashes, result.frameCount(), manifestDetails),
            EpisodeArchiveStatus.AVAILABLE);
    archive.confirmAvailable(session.getCompletedAt(), now);
    archives.save(archive);

    jobs.findByIdForUpdate(session.getTrainingJobId())
        .orElseThrow(() -> new IllegalStateException("training job is missing"));
    int inputOrder = Math.toIntExact(jobEpisodes.countByIdTrainingJobId(session.getTrainingJobId()) + 1);
    jobEpisodes.save(
        new TrainingJobEpisode(
            new TrainingJobEpisodeId(session.getTrainingJobId(), episode.getId()),
            inputOrder,
            session.getSha256(),
            result.contractVersion(),
            EpisodeSource.REAL,
            labelStatus,
            result.success(),
            qualityStatus,
            now));
    session.markReady(qualityStatus.name(), now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void invalid(UUID uploadSessionId, String code, String message) {
    EpisodeUploadSession session = validatingSession(uploadSessionId);
    session.markInvalid(code, message, Instant.now());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(UUID uploadSessionId, String code, String message) {
    EpisodeUploadSession session = validatingSession(uploadSessionId);
    session.markFailed(code, message, Instant.now());
  }

  private EpisodeUploadSession validatingSession(UUID id) {
    EpisodeUploadSession session =
        sessions.findForUpdateById(id).orElseThrow(() -> new IllegalStateException("upload session is missing"));
    if (session.getStatus() != EpisodeUploadStatus.VALIDATING)
      throw new IllegalStateException("upload session is not validating");
    return session;
  }

  private EpisodeQualityStatus normalizedQuality(String value) {
    EpisodeQualityStatus quality = EpisodeQualityStatus.valueOf(value);
    if (quality != EpisodeQualityStatus.VALID && quality != EpisodeQualityStatus.WARNING)
      throw new IllegalArgumentException("quality_status must be VALID or WARNING");
    return quality;
  }
}
