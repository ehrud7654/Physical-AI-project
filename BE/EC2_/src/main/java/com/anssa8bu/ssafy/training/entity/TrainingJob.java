package com.anssa8bu.ssafy.training.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_jobs")
public class TrainingJob extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @Column(name = "umi_device_id", nullable = false)
  private UUID umiDeviceId;

  @Column(name = "training_profile_id", nullable = false)
  private UUID trainingProfileId;

  @Column(name = "assigned_storage_node_id")
  private UUID assignedStorageNodeId;

  @Column(name = "assigned_worker_id", length = 100)
  private String assignedWorkerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_mode", nullable = false, length = 30)
  private TrainingInputMode inputMode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TrainingJobStatus status;

  @Column(nullable = false)
  private int attempt = 1;

  @Column(name = "lease_token_hash", length = 64)
  private String leaseTokenHash;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "training_config_snapshot", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> trainingConfigSnapshot;

  @Column(name = "training_config_sha256", nullable = false, length = 64)
  private String trainingConfigSha256;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dataset_query", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> datasetQuery;

  @Column(name = "gpu_index")
  private Integer gpuIndex;

  @Column(name = "git_rev", length = 100)
  private String gitRev;

  @Column(name = "expected_episode_count", nullable = false)
  private int expectedEpisodeCount;

  @Column(name = "received_episode_count", nullable = false)
  private int receivedEpisodeCount;

  @Column(name = "received_bytes", nullable = false)
  private long receivedBytes;

  @Column(name = "episode_count", nullable = false)
  private int episodeCount;

  @Column(name = "input_snapshot_sha256", length = 64)
  private String inputSnapshotSha256;

  @Column(name = "progress_stage", length = 100)
  private String progressStage;

  @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal progressPercent;

  @Column(name = "current_epoch")
  private Integer currentEpoch;

  @Column(name = "total_epochs")
  private Integer totalEpochs;

  @Column(name = "result_skill_version_id")
  private UUID resultSkillVersionId;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "model_completed_at")
  private Instant modelCompletedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected TrainingJob() {}

  public TrainingJob(
      UUID id,
      UUID ownerId,
      UUID skillId,
      UUID deviceId,
      UUID profileId,
      TrainingInputMode mode,
      Map<String, Object> config,
      String hash,
      Map<String, Object> query,
      int episodeCount) {
    if (hash == null || !hash.matches("[0-9a-f]{64}") || episodeCount < 0)
      throw new IllegalArgumentException();
    this.id = Objects.requireNonNull(id);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.skillId = Objects.requireNonNull(skillId);
    this.umiDeviceId = Objects.requireNonNull(deviceId);
    this.trainingProfileId = Objects.requireNonNull(profileId);
    this.inputMode = Objects.requireNonNull(mode);
    this.status = TrainingJobStatus.CREATED;
    this.trainingConfigSnapshot = Map.copyOf(config);
    this.trainingConfigSha256 = hash;
    this.datasetQuery = Map.copyOf(query);
    this.episodeCount = episodeCount;
    this.progressPercent = BigDecimal.ZERO;
  }

  public UUID getId() {
    return id;
  }

  public TrainingJobStatus getStatus() {
    return status;
  }

  public Map<String, Object> getTrainingConfigSnapshot() {
    return trainingConfigSnapshot;
  }

  public BigDecimal getProgressPercent() {
    return progressPercent;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public void planInput(int expectedCount, boolean ready) {
    if (expectedCount < 1) throw new IllegalArgumentException("expectedCount must be positive");
    expectedEpisodeCount = expectedCount;
    status = ready ? TrainingJobStatus.QUEUED : TrainingJobStatus.CREATED;
  }

  public void requestCancel() {
    if (status == TrainingJobStatus.CREATED || status == TrainingJobStatus.COLLECTING
        || status == TrainingJobStatus.INPUT_VALIDATING || status == TrainingJobStatus.INPUT_READY
        || status == TrainingJobStatus.QUEUED || status == TrainingJobStatus.PREPARING) {
      status = TrainingJobStatus.CANCELED;
      finishedAt = Instant.now();
    } else if (status == TrainingJobStatus.RUNNING || status == TrainingJobStatus.MODEL_UPLOADING
        || status == TrainingJobStatus.MODEL_VALIDATING) status = TrainingJobStatus.CANCEL_REQUESTED;
    else throw new IllegalStateException("종료된 학습은 취소할 수 없습니다.");
  }

  public void updateExpectedEpisodeCount(int expectedCount) {
    if (inputMode != TrainingInputMode.COLLECT_NEW
        || (status != TrainingJobStatus.CREATED && status != TrainingJobStatus.COLLECTING))
      throw new IllegalStateException("입력 계획을 변경할 수 없는 상태입니다.");
    if (expectedCount < 1) throw new IllegalArgumentException("expectedCount must be positive");
    expectedEpisodeCount = expectedCount;
  }

  public void beginInputValidation(int confirmedCount) {
    if (inputMode != TrainingInputMode.COLLECT_NEW)
      throw new IllegalStateException("신규 수집 작업만 입력 완료를 요청할 수 있습니다.");
    if (status != TrainingJobStatus.CREATED && status != TrainingJobStatus.COLLECTING)
      throw new IllegalStateException("입력 완료를 요청할 수 없는 상태입니다.");
    if (confirmedCount != receivedEpisodeCount)
      throw new IllegalStateException("수신된 Episode 수와 확인한 수가 일치하지 않습니다.");
    status = TrainingJobStatus.INPUT_VALIDATING;
    progressStage = "입력 검증";
  }

  public UUID getOwnerId() { return ownerId; }
  public UUID getSkillId() { return skillId; }
  public UUID getUmiDeviceId() { return umiDeviceId; }
  public UUID getTrainingProfileId() { return trainingProfileId; }
  public TrainingInputMode getInputMode() { return inputMode; }
  public int getExpectedEpisodeCount() { return expectedEpisodeCount; }
  public int getReceivedEpisodeCount() { return receivedEpisodeCount; }
  public long getReceivedBytes() { return receivedBytes; }
  public int getEpisodeCount() { return episodeCount; }
  public String getProgressStage() { return progressStage; }
  public UUID getResultSkillVersionId() { return resultSkillVersionId; }
  public String getErrorCode() { return errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getFinishedAt() { return finishedAt; }
}
