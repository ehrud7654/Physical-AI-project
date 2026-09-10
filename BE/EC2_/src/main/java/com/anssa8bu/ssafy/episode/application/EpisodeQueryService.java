package com.anssa8bu.ssafy.episode.application;

import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.*;

import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.episode.entity.Episode;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchive;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchiveStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeQualityStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeStatus;
import com.anssa8bu.ssafy.episode.repository.EpisodeArchiveRepository;
import com.anssa8bu.ssafy.episode.repository.EpisodeRepository;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.storage.entity.RetentionPolicy;
import com.anssa8bu.ssafy.storage.repository.RetentionPolicyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EpisodeQueryService {
  private final EpisodeRepository episodes;
  private final EpisodeArchiveRepository archives;
  private final DeviceRepository devices;
  private final SkillRepository skills;
  private final RetentionPolicyRepository retentionPolicies;
  private final Clock clock;

  public EpisodeQueryService(
      EpisodeRepository episodes,
      EpisodeArchiveRepository archives,
      DeviceRepository devices,
      SkillRepository skills,
      RetentionPolicyRepository retentionPolicies,
      Clock clock) {
    this.episodes = episodes;
    this.archives = archives;
    this.devices = devices;
    this.skills = skills;
    this.retentionPolicies = retentionPolicies;
    this.clock = clock;
  }

  /** EPISODE-01. 소유자의 Episode를 createdAt DESC, id DESC로 반환한다. */
  public List<EpisodeResponse> list(UUID ownerId, ListQuery q) {
    // ponytail: 필터를 메모리에서 적용한다. 사용자당 Episode가 수천 건을 넘으면 Specification/QueryDSL로 전환.
    List<Episode> owned = episodes.findAllByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
    Map<UUID, EpisodeArchive> archiveByEpisode = archivesOf(owned);
    Map<UUID, Skill> skillById = skillsOf(owned);
    Map<UUID, Device> deviceById = devicesOf(owned);

    List<EpisodeResponse> result = new ArrayList<>();
    for (Episode e : owned) {
      if (!matches(e, q, skillById.get(e.getSkillId()))) continue;
      EpisodeResponse response =
          toResponse(
              e, archiveByEpisode.get(e.getId()), skillById.get(e.getSkillId()), deviceById.get(e.getUmiDeviceId()));
      if (q.availableForTraining() != null && q.availableForTraining() != response.trainable()) continue;
      result.add(response);
    }
    return result;
  }

  /** EPISODE-02. 다른 사용자 소유는 존재 여부가 드러나지 않도록 404. */
  public EpisodeResponse get(UUID ownerId, UUID episodeId) {
    Episode e =
        episodes
            .findByIdAndOwnerId(episodeId, ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Episode"));
    return toResponse(
        e,
        archives.findByEpisodeId(e.getId()).orElse(null),
        skills.findById(e.getSkillId()).orElse(null),
        devices.findById(e.getUmiDeviceId()).orElse(null));
  }

  /** EPISODE-06. 기본·활성 정책 한 건. seed(V2)로 항상 존재해야 한다. */
  public RetentionPolicyResponse retentionPolicy() {
    RetentionPolicy p =
        retentionPolicies
            .findFirstByDefaultPolicyTrueAndActiveTrue()
            .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy"));
    return new RetentionPolicyResponse(
        p.getName(),
        p.getDefaultRetentionDays(),
        p.getPostTrainingGraceDays(),
        p.getMaxRetentionDays(),
        p.isAllowManualExtension(),
        p.isAllowManualPurge());
  }

  private static boolean matches(Episode e, ListQuery q, Skill skill) {
    return (q.umiDeviceId() == null || q.umiDeviceId().equals(e.getUmiDeviceId()))
        && (q.skillId() == null || q.skillId().equals(e.getSkillId()))
        && (q.skillKey() == null || (skill != null && q.skillKey().equals(skill.getSkillKey())))
        && (q.source() == null || q.source() == e.getSource())
        && (q.labelStatus() == null || q.labelStatus() == e.getLabelStatus())
        && (q.success() == null || q.success().equals(e.getSuccess()))
        && (q.contractVersion() == null || q.contractVersion().equals(e.getContractVersion()))
        && (q.qualityStatus() == null || q.qualityStatus() == e.getQualityStatus())
        && (q.status() == null || q.status() == e.getStatus())
        && (q.startedFrom() == null || !e.getStartedAt().isBefore(q.startedFrom()))
        && (q.startedTo() == null || !e.getStartedAt().isAfter(q.startedTo()));
  }

  /**
   * 명세 EPISODE-01의 availableForTraining 조건: status=READY, quality VALID/WARNING, LABELED, success=true,
   * archive AVAILABLE, 삭제 미시작(=DELETING 아님). 어긋나는 조건마다 FE가 표시할 차단 사유를 만든다.
   */
  private List<BlockingReason> blockingReasons(Episode e, EpisodeArchive archive) {
    List<BlockingReason> reasons = new ArrayList<>();
    switch (e.getStatus()) {
      case UPLOADING ->
          reasons.add(new BlockingReason("UPLOAD_IN_PROGRESS", "데이터를 업로드하고 있어요.", RecoveryAction.WAIT));
      case VALIDATING ->
          reasons.add(new BlockingReason("EPISODE_VALIDATING", "데이터를 확인하고 있어요.", RecoveryAction.WAIT));
      case INVALID ->
          reasons.add(
              new BlockingReason(
                  Objects.requireNonNullElse(e.getErrorCode(), "EPISODE_INVALID"),
                  Objects.requireNonNullElse(e.getErrorMessage(), "데이터 확인에 실패했어요. 다시 수집해 주세요."),
                  RecoveryAction.RECORD_AGAIN));
      case DELETING, DELETED ->
          reasons.add(new BlockingReason("EPISODE_DELETED", "보관 기한이 지나 삭제된 데이터예요.", RecoveryAction.RECORD_AGAIN));
      case DELETE_FAILED ->
          reasons.add(new BlockingReason("EPISODE_DELETE_FAILED", "데이터 삭제 처리에 실패했어요.", RecoveryAction.CONTACT_SUPPORT));
      case READY -> {
        if (e.getQualityStatus() == EpisodeQualityStatus.PENDING)
          reasons.add(new BlockingReason("QUALITY_PENDING", "품질 검사를 기다리고 있어요.", RecoveryAction.WAIT));
        else if (e.getQualityStatus() == EpisodeQualityStatus.INVALID)
          reasons.add(new BlockingReason("QUALITY_INVALID", "데이터 품질이 기준에 미치지 못해요.", RecoveryAction.RECORD_AGAIN));
        if (e.getLabelStatus() != EpisodeLabelStatus.LABELED)
          reasons.add(new BlockingReason("EPISODE_NOT_LABELED", "성공 여부가 아직 표시되지 않았어요.", RecoveryAction.SET_LABEL));
        else if (!Boolean.TRUE.equals(e.getSuccess()))
          reasons.add(new BlockingReason("EPISODE_NOT_SUCCESSFUL", "실패로 표시된 데이터는 학습에 쓰지 않아요.", RecoveryAction.RECORD_AGAIN));
        if (archive == null || archive.getStatus() != EpisodeArchiveStatus.AVAILABLE)
          reasons.add(new BlockingReason("ARCHIVE_NOT_AVAILABLE", "데이터 파일을 아직 사용할 수 없어요.", RecoveryAction.WAIT));
        if (!e.getRetentionExpiresAt().isAfter(clock.instant()))
          reasons.add(new BlockingReason("RETENTION_EXPIRED", "보관 기한이 지났어요.", RecoveryAction.EXTEND_RETENTION));
      }
    }
    return reasons;
  }

  private EpisodeResponse toResponse(Episode e, EpisodeArchive archive, Skill skill, Device device) {
    List<BlockingReason> reasons = blockingReasons(e, archive);
    return new EpisodeResponse(
        e.getId(),
        e.getOwnerId(),
        e.getUmiDeviceId(),
        device == null ? null : device.getHandleNumber(),
        e.getSkillId(),
        skill == null ? null : skill.getSkillKey(),
        skill == null ? null : skill.getDisplayName(),
        e.getSessionId(),
        e.getSource(),
        e.getSuccess(),
        e.getLabelStatus(),
        e.getLabelSource(),
        e.getLabeledAt(),
        e.getContractVersion(),
        e.getMetaSchemaVersion(),
        e.getSummarySchemaVersion(),
        e.getStatus(),
        e.getStartedAt(),
        e.getEndedAt(),
        e.getStartMonotonicNs(),
        e.getTimestampTimebase(),
        e.getFirstTimestampNs(),
        e.getLastTimestampNs(),
        e.getDurationNs(),
        e.getFrameCount(),
        e.getPoseSampleCount(),
        e.getImuSampleCount(),
        e.getGripperSampleCount(),
        e.getMissingFrameCount(),
        e.getTrackingGapCount(),
        e.getTrackingLostDurationNs(),
        e.getQualityStatus(),
        e.getValidatedAt(),
        e.getLastUsedAt(),
        e.getRetentionExpiresAt(),
        e.getDeletedAt(),
        e.getCaptureMetadata(),
        e.getQualitySummary(),
        archive == null ? null : toArchive(archive),
        reasons.isEmpty(),
        reasons,
        reasons.isEmpty() ? RecoveryAction.NONE : reasons.get(0).recoveryAction(),
        e.getErrorCode() == null
            ? null
            : new ResourceError(
                e.getErrorCode(),
                e.getErrorMessage(),
                e.getStatus() == EpisodeStatus.INVALID ? RecoveryAction.RECORD_AGAIN : RecoveryAction.CONTACT_SUPPORT),
        e.getCreatedAt());
  }

  private static ArchiveResponse toArchive(EpisodeArchive a) {
    return new ArchiveResponse(
        a.getId(),
        a.getContentType(),
        a.getArchiveFormatVersion(),
        a.getCompressionMethod(),
        a.getSizeBytes(),
        a.getUncompressedSizeBytes(),
        a.getEntryCount(),
        a.getSha256(),
        a.getStatus(),
        a.getStoredAt(),
        a.getValidatedAt(),
        a.getDeletedAt());
  }

  private Map<UUID, EpisodeArchive> archivesOf(List<Episode> list) {
    if (list.isEmpty()) return Map.of();
    return archives.findAllByEpisodeIdIn(ids(list, Episode::getId)).stream()
        .collect(Collectors.toMap(EpisodeArchive::getEpisodeId, Function.identity(), (a, b) -> a));
  }

  private Map<UUID, Skill> skillsOf(List<Episode> list) {
    if (list.isEmpty()) return Map.of();
    return skills.findAllById(ids(list, Episode::getSkillId)).stream()
        .collect(Collectors.toMap(Skill::getId, Function.identity()));
  }

  private Map<UUID, Device> devicesOf(List<Episode> list) {
    if (list.isEmpty()) return Map.of();
    return devices.findAllById(ids(list, Episode::getUmiDeviceId)).stream()
        .collect(Collectors.toMap(Device::getId, Function.identity()));
  }

  private static Set<UUID> ids(List<Episode> list, Function<Episode, UUID> f) {
    return list.stream().map(f).collect(Collectors.toSet());
  }
}
