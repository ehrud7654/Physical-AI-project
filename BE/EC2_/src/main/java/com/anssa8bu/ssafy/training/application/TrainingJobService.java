package com.anssa8bu.ssafy.training.application;

import static com.anssa8bu.ssafy.training.dto.TrainingJobApiModels.*;

import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.device.entity.*;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.episode.entity.*;
import com.anssa8bu.ssafy.episode.repository.*;
import com.anssa8bu.ssafy.model.entity.*;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.skill.application.SkillService;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingJobService {
  private final TrainingJobRepository jobs;
  private final TrainingJobEpisodeRepository inputs;
  private final SkillRepository skills;
  private final SkillService skillService;
  private final DeviceRepository devices;
  private final EpisodeRepository episodes;
  private final EpisodeArchiveRepository archives;
  private final SkillVersionRepository skillVersions;
  private final IdempotencyGuard idempotency;
  private final Clock clock;

  public TrainingJobService(
      TrainingJobRepository jobs,
      TrainingJobEpisodeRepository inputs,
      SkillRepository skills,
      DeviceRepository devices,
      EpisodeRepository episodes,
      EpisodeArchiveRepository archives,
      SkillVersionRepository skillVersions,
      IdempotencyGuard idempotency,
      Clock clock,
      SkillService skillService) {
    this.jobs = jobs;
    this.inputs = inputs;
    this.skills = skills;
    this.devices = devices;
    this.episodes = episodes;
    this.archives = archives;
    this.skillVersions = skillVersions;
    this.idempotency = idempotency;
    this.clock = clock;
    this.skillService = skillService;
  }

  @Transactional
  public JobResponse create(UUID owner, String key, CreateRequest request) {
    var claim = idempotency.claim(owner, "POST", "/api/v1/training-jobs", key, request);
    if (claim.existingResourceId() != null) return get(owner, claim.existingResourceId());
    boolean hasSkillKey = request.skillKey() != null && !request.skillKey().isBlank();
    boolean hasNewSkill = request.newSkill() != null;
    if (hasSkillKey == hasNewSkill)
      throw new BusinessConflictException(
          "INVALID_SKILL_SELECTION", "skillKey와 newSkill 중 하나만 입력해야 합니다.");
    Skill skill =
        hasNewSkill
            ? skillService.createCustom(owner, request.newSkill())
            : skillService.requireVisible(owner, request.skillKey().trim());
    Device device =
        devices
            .findByIdAndOwnerId(request.umiDeviceId(), owner)
            .filter(d -> d.getDeviceType() == DeviceType.UMI_CAMERA)
            .orElseThrow(() -> new ResourceNotFoundException("UMI Device"));
    List<Episode> selected = selectEpisodes(owner, skill, request);
    if (selected.stream().anyMatch(episode -> !episode.getUmiDeviceId().equals(device.getId())))
      throw new BusinessConflictException(
          "EPISODE_DEVICE_MISMATCH", "선택한 Episode와 UMI 장치가 일치하지 않습니다.");
    List<TrainingEpisodeInput> trainingInputs = resolveTrainingInputs(selected);
    TrainingMode trainingMode =
        request.trainingMode() == null ? TrainingMode.FROM_SCRATCH : request.trainingMode();
    SkillVersion baseVersion = validateBaseVersion(skill, trainingMode, request.baseSkillVersionId());
    int expected = trainingInputs.size();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("skillKey", skill.getSkillKey());
    query.put("inputMode", TrainingInputMode.EXISTING_EPISODES.name());
    query.put("trainingMode", trainingMode.name());
    if (baseVersion != null) query.put("baseSkillVersionId", baseVersion.getId().toString());
    TrainingJob job =
        jobs.save(
            new TrainingJob(
                UUID.randomUUID(),
                owner,
                skill.getId(),
                device.getId(),
                TrainingInputMode.EXISTING_EPISODES,
                query,
                expected,
                trainingMode,
                baseVersion == null ? null : baseVersion.getId()));
    job.planInput(expected, true);
    for (int i = 0; i < trainingInputs.size(); i++) {
      TrainingEpisodeInput input = trainingInputs.get(i);
      attach(job, input.episode(), i + 1, input.role());
    }
    job.fixInputSnapshotSha256(inputSnapshotHash(trainingInputs));
    idempotency.complete(
        owner, "POST", "/api/v1/training-jobs", claim, 201, "TRAINING_JOB", job.getId());
    return map(job, skill);
  }

  @Transactional(readOnly = true)
  public List<JobResponse> list(
      UUID owner, UUID skillId, String skillKey, UUID umiDeviceId, String status) {
    return jobs.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(owner).stream()
        .filter(j -> skillId == null || j.getSkillId().equals(skillId))
        .filter(j -> umiDeviceId == null || j.getUmiDeviceId().equals(umiDeviceId))
        .filter(
            j ->
                skillKey == null
                    || skillKey.isBlank()
                    || skill(j.getSkillId()).getSkillKey().equals(skillKey))
        .filter(j -> status == null || status.isBlank() || j.getStatus().name().equals(status))
        .map(j -> map(j, skill(j.getSkillId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public JobResponse get(UUID owner, UUID id) {
    TrainingJob job = owned(owner, id);
    return map(job, skill(job.getSkillId()));
  }

  @Transactional
  public JobResponse cancel(UUID owner, UUID id) {
    TrainingJob job = owned(owner, id);
    try {
      job.requestCancel();
    } catch (IllegalStateException e) {
      throw new BusinessConflictException("STATE_CONFLICT", e.getMessage());
    }
    return map(job, skill(job.getSkillId()));
  }

  @Transactional(readOnly = true)
  public List<EpisodeInputResponse> inputEpisodes(UUID owner, UUID id) {
    owned(owner, id);
    return inputs.findAllByIdTrainingJobIdOrderByInputOrder(id).stream()
        .map(input -> new EpisodeInputResponse(
            input.getId().episodeId(), input.getInputOrder(), input.getInputRole()))
        .toList();
  }

  @Transactional
  public void delete(UUID owner, UUID id) {
    TrainingJob job =
        jobs.findByIdAndOwnerId(id, owner)
            .orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    if (job.isDeleted()) return;
    if (job.getStatus() != TrainingJobStatus.CANCELED
        && job.getStatus() != TrainingJobStatus.SUCCEEDED
        && job.getStatus() != TrainingJobStatus.FAILED)
      throw new BusinessConflictException(
          "TRAINING_DELETE_NOT_ALLOWED", "진행 중인 학습은 삭제할 수 없습니다. 먼저 학습을 취소해 주세요.");
    job.softDelete(clock.instant());
  }

  private List<Episode> selectEpisodes(UUID owner, Skill skill, CreateRequest request) {
    if (request.episodeIds() == null
        || request.episodeIds().isEmpty()
        || new HashSet<>(request.episodeIds()).size() != request.episodeIds().size())
      throw new BusinessConflictException("EPISODE_INVALID", "중복 없는 Episode가 하나 이상 필요합니다.");
    List<Episode> found = episodes.findAllByIdIn(request.episodeIds());
    if (found.size() != request.episodeIds().size()) throw new ResourceNotFoundException("Episode");
    UUID storage = null;
    Map<UUID, Episode> byId = new HashMap<>();
    for (Episode episode : found) {
      if (!episode.getOwnerId().equals(owner)
          || !episode.getSkillId().equals(skill.getId())
          || episode.getStatus() != EpisodeStatus.READY
          || !(episode.getQualityStatus() == EpisodeQualityStatus.VALID
              || episode.getQualityStatus() == EpisodeQualityStatus.WARNING)
          || episode.getLabelStatus() != EpisodeLabelStatus.LABELED
          || !Boolean.TRUE.equals(episode.getSuccess()))
        throw new BusinessConflictException("EPISODE_INVALID", "학습에 사용할 수 없는 Episode입니다.");
      EpisodeArchive archive =
          archives
              .findByEpisodeId(episode.getId())
              .filter(a -> a.getStatus() == EpisodeArchiveStatus.AVAILABLE)
              .orElseThrow(
                  () ->
                      new BusinessConflictException(
                          "EPISODE_NOT_AVAILABLE", "Episode 파일을 사용할 수 없습니다."));
      if (storage != null && !storage.equals(episode.getStorageNodeId()))
        throw new BusinessConflictException(
            "EPISODE_STORAGE_NODE_MISMATCH", "서로 다른 저장 노드의 Episode는 함께 사용할 수 없습니다.");
      storage = episode.getStorageNodeId();
      byId.put(episode.getId(), episode);
    }
    return request.episodeIds().stream().map(byId::get).toList();
  }

  private void attach(
      TrainingJob job, Episode episode, int order, TrainingEpisodeRole inputRole) {
    EpisodeArchive archive = archives.findByEpisodeId(episode.getId()).orElseThrow();
    inputs.save(
        new TrainingJobEpisode(
            new TrainingJobEpisodeId(job.getId(), episode.getId()),
            order,
            inputRole,
            archive.getSha256(),
            episode.getContractVersion(),
            episode.getSource(),
            episode.getLabelStatus(),
            episode.getSuccess(),
            episode.getQualityStatus(),
            clock.instant()));
  }

  private List<TrainingEpisodeInput> resolveTrainingInputs(List<Episode> selected) {
    List<TrainingEpisodeInput> inputs = new ArrayList<>();
    for (Episode episode : selected) {
      String datasetRole = datasetRole(episode);
      if ("CALIBRATION".equals(datasetRole)) continue;
      TrainingEpisodeRole role =
          "MAPPING".equals(datasetRole)
              ? TrainingEpisodeRole.MAPPING
              : TrainingEpisodeRole.DEMONSTRATION;
      inputs.add(new TrainingEpisodeInput(episode, role));
    }
    long mappingCount =
        inputs.stream().filter(input -> input.role() == TrainingEpisodeRole.MAPPING).count();
    long demonstrationCount =
        inputs.stream().filter(input -> input.role() == TrainingEpisodeRole.DEMONSTRATION).count();
    if (mappingCount > 1)
      throw new BusinessConflictException(
          "MULTIPLE_MAPPING_EPISODES", "공간 매핑 Episode는 하나만 사용할 수 있습니다.");
    if (demonstrationCount < 1)
      throw new BusinessConflictException(
          "INSUFFICIENT_DEMONSTRATION_EPISODES", "학습에는 시연 Episode가 한 개 이상 필요합니다.");
    return inputs;
  }

  private String datasetRole(Episode episode) {
    Object qualityRole = episode.getQualitySummary().values().get("datasetRole");
    if (qualityRole != null && !qualityRole.toString().isBlank())
      return qualityRole.toString().trim().toUpperCase(Locale.ROOT);
    return archives
        .findByEpisodeId(episode.getId())
        .map(EpisodeArchive::getManifestSummary)
        .map(summary -> summary.details().get("datasetRole"))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .map(value -> value.trim().toUpperCase(Locale.ROOT))
        .orElse("DEMONSTRATION");
  }

  private String inputSnapshotHash(List<TrainingEpisodeInput> selected) {
    try {
      StringBuilder canonical = new StringBuilder();
      for (int index = 0; index < selected.size(); index++) {
        TrainingEpisodeInput input = selected.get(index);
        appendSnapshot(canonical, index + 1, input.role(), input.episode());
      }
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
    }
  }

  private record TrainingEpisodeInput(Episode episode, TrainingEpisodeRole role) {}

  private void appendSnapshot(
      StringBuilder canonical, int order, TrainingEpisodeRole role, Episode episode) {
    EpisodeArchive archive = archives.findByEpisodeId(episode.getId()).orElseThrow();
    canonical
        .append(order)
        .append('|')
        .append(role)
        .append('|')
        .append(episode.getId())
        .append('|')
        .append(archive.getSha256())
        .append('|')
        .append(episode.getContractVersion())
        .append('\n');
  }

  private SkillVersion validateBaseVersion(
      Skill skill, TrainingMode mode, UUID baseSkillVersionId) {
    if (mode == TrainingMode.FROM_SCRATCH) {
      if (baseSkillVersionId != null)
        throw new BusinessConflictException(
            "BASE_MODEL_NOT_ALLOWED", "처음부터 학습할 때는 기준 모델을 지정할 수 없습니다.");
      return null;
    }
    if (baseSkillVersionId == null)
      throw new BusinessConflictException(
          "BASE_MODEL_REQUIRED", "추가 학습에는 기준 스킬 버전이 필요합니다.");
    SkillVersion version =
        skillVersions
            .findById(baseSkillVersionId)
            .orElseThrow(() -> new ResourceNotFoundException("BaseSkillVersion"));
    if (!version.getSkillId().equals(skill.getId()))
      throw new BusinessConflictException(
          "BASE_MODEL_SKILL_MISMATCH", "기준 모델이 학습 대상 동작에 속하지 않습니다.");
    if (version.getStatus() != SkillVersionStatus.READY)
      throw new BusinessConflictException(
          "BASE_MODEL_NOT_READY", "READY 상태의 모델만 추가 학습 기준으로 사용할 수 있습니다.");
    return version;
  }

  private TrainingJob owned(UUID owner, UUID id) {
    return jobs.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner)
        .orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
  }

  private Skill skill(UUID id) {
    return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill"));
  }

  private JobResponse map(TrainingJob j, Skill s) {
    return new JobResponse(
        j.getId(),
        s.getSkillKey(),
        s.getDisplayName(),
        j.getUmiDeviceId(),
        j.getInputMode(),
        j.getTrainingMode(),
        j.getBaseSkillVersionId(),
        j.getStatus(),
        j.getProgressStage(),
        j.getProgressPercent(),
        j.getExpectedEpisodeCount(),
        j.getReceivedEpisodeCount(),
        j.getReceivedBytes(),
        j.getEpisodeCount(),
        j.getResultSkillVersionId(),
        j.getErrorCode() == null
            ? null
            : new ErrorInfo(j.getErrorCode(), j.getErrorMessage(), "CONTACT_SUPPORT"),
        j.getCreatedAt(),
        j.getStartedAt(),
        j.getFinishedAt(),
        j.getUpdatedAt());
  }
}
