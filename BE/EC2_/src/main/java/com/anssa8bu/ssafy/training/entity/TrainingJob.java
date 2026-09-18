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

  @Column(name = "assigned_storage_node_id")
  private UUID assignedStorageNodeId;

  @Column(name = "assigned_worker_id", length = 100)
  private String assignedWorkerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "training_mode", nullable = false, length = 30)
  private TrainingMode trainingMode;

  @Column(name = "base_skill_version_id")
  private UUID baseSkillVersionId;

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

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected TrainingJob() {}

  public TrainingJob(
      UUID id,
      UUID ownerId,
      UUID skillId,
      UUID deviceId,
      TrainingInputMode mode,
      Map<String, Object> query,
      int episodeCount) {
    this(id, ownerId, skillId, deviceId, mode, query, episodeCount,
        TrainingMode.FROM_SCRATCH, null);
  }

  public TrainingJob(
      UUID id,
      UUID ownerId,
      UUID skillId,
      UUID deviceId,
      TrainingInputMode mode,
      Map<String, Object> query,
      int episodeCount,
      TrainingMode trainingMode,
      UUID baseSkillVersionId) {
    if (episodeCount < 0) throw new IllegalArgumentException();
    this.id = Objects.requireNonNull(id);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.skillId = Objects.requireNonNull(skillId);
    this.umiDeviceId = Objects.requireNonNull(deviceId);
    this.inputMode = Objects.requireNonNull(mode);
    this.trainingMode = Objects.requireNonNull(trainingMode);
    if ((trainingMode == TrainingMode.FINE_TUNE) != (baseSkillVersionId != null))
      throw new IllegalArgumentException("학습 모드와 기준 모델이 일치하지 않습니다.");
    this.baseSkillVersionId = baseSkillVersionId;
    this.status = TrainingJobStatus.CREATED;
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
    if (status == TrainingJobStatus.CREATED
        || status == TrainingJobStatus.COLLECTING
        || status == TrainingJobStatus.INPUT_VALIDATING
        || status == TrainingJobStatus.INPUT_READY
        || status == TrainingJobStatus.QUEUED
        || status == TrainingJobStatus.PREPARING) {
      status = TrainingJobStatus.CANCELED;
      finishedAt = Instant.now();
    } else if (status == TrainingJobStatus.RUNNING
        || status == TrainingJobStatus.MODEL_UPLOADING
        || status == TrainingJobStatus.MODEL_VALIDATING)
      status = TrainingJobStatus.CANCEL_REQUESTED;
    else throw new IllegalStateException("종료된 학습은 취소할 수 없습니다.");
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public UUID getSkillId() {
    return skillId;
  }

  public UUID getUmiDeviceId() {
    return umiDeviceId;
  }

  public TrainingInputMode getInputMode() {
    return inputMode;
  }

  public TrainingMode getTrainingMode() { return trainingMode; }

  public UUID getBaseSkillVersionId() { return baseSkillVersionId; }

  public int getExpectedEpisodeCount() {
    return expectedEpisodeCount;
  }

  public int getReceivedEpisodeCount() {
    return receivedEpisodeCount;
  }

  public long getReceivedBytes() {
    return receivedBytes;
  }

  public int getEpisodeCount() {
    return episodeCount;
  }

  public String getProgressStage() {
    return progressStage;
  }

  public UUID getResultSkillVersionId() {
    return resultSkillVersionId;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete(Instant deletedAt) {
    if (this.deletedAt == null) this.deletedAt = Objects.requireNonNull(deletedAt);
  }

  public void claim(String workerId, UUID storageNodeId, int gpuIndex, String gitRev,
      String leaseHash, Instant leaseExpiresAt) {
    if (status != TrainingJobStatus.QUEUED)
      throw new IllegalStateException("claim할 수 없는 학습 상태입니다.");
    this.assignedWorkerId = Objects.requireNonNull(workerId);
    this.assignedStorageNodeId = storageNodeId;
    this.gpuIndex = gpuIndex;
    this.gitRev = gitRev;
    this.leaseTokenHash = Objects.requireNonNull(leaseHash);
    this.leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt);
    this.status = TrainingJobStatus.PREPARING;
  }

  public void renewLease(Instant expiresAt) { this.leaseExpiresAt = Objects.requireNonNull(expiresAt); }
  public void rotateLease(String leaseHash, Instant expiresAt) {
    this.leaseTokenHash = Objects.requireNonNull(leaseHash);
    this.leaseExpiresAt = Objects.requireNonNull(expiresAt);
  }

  public void applyProgress(String stage, BigDecimal percent, Integer epoch, Integer totalEpochs) {
    if (status != TrainingJobStatus.RUNNING)
      throw new IllegalStateException("RUNNING 상태에서만 진행도를 갱신할 수 있습니다.");
    if (stage == null || stage.isBlank() || percent == null
        || percent.compareTo(BigDecimal.ZERO) < 0
        || percent.compareTo(new BigDecimal("100")) > 0)
      throw new IllegalArgumentException("유효한 stage와 0~100 percent가 필요합니다.");
    if ((epoch == null) != (totalEpochs == null)
        || (epoch != null && (epoch < 0 || totalEpochs < 1 || epoch > totalEpochs)))
      throw new IllegalArgumentException("epoch와 totalEpochs가 올바르지 않습니다.");
    this.progressStage = stage;
    this.progressPercent = percent;
    this.currentEpoch = epoch;
    this.totalEpochs = totalEpochs;
  }

  public void start(Instant at) {
    if (status != TrainingJobStatus.PREPARING && status != TrainingJobStatus.QUEUED)
      throw new IllegalStateException("학습을 시작할 수 없는 상태입니다.");
    status = TrainingJobStatus.RUNNING;
    if (startedAt == null) startedAt = at;
  }

  public void markModelUploading(Instant completedAt) {
    if (status != TrainingJobStatus.RUNNING && status != TrainingJobStatus.MODEL_UPLOADING)
      throw new IllegalStateException("학습 완료를 보고할 수 없는 상태입니다.");
    if (startedAt != null && completedAt.isBefore(startedAt))
      throw new IllegalArgumentException("모델 완료 시각은 학습 시작 시각보다 빠를 수 없습니다.");
    if (modelCompletedAt != null && !modelCompletedAt.equals(completedAt))
      throw new IllegalStateException("최초 모델 완료 시각과 일치하지 않습니다.");
    status = TrainingJobStatus.MODEL_UPLOADING;
    modelCompletedAt = completedAt;
  }

  public void markModelValidating() { status = TrainingJobStatus.MODEL_VALIDATING; }

  public void complete(UUID skillVersionId, Instant at) {
    resultSkillVersionId = Objects.requireNonNull(skillVersionId);
    status = TrainingJobStatus.SUCCEEDED;
    progressPercent = new BigDecimal("100.00");
    finishedAt = at;
  }

  public void fail(String code, String message, Instant at) {
    status = TrainingJobStatus.FAILED;
    errorCode = code;
    errorMessage = message;
    finishedAt = at;
  }

  public void cancel(Instant at) { status = TrainingJobStatus.CANCELED; finishedAt = at; }

  public void releaseAssignment() {
    assignedWorkerId=null; assignedStorageNodeId=null; gpuIndex=null; gitRev=null;
    leaseTokenHash=null; leaseExpiresAt=null;
  }

  public void recoverExpiredLease(int maxAttempts, Instant at) {
    if (leaseExpiresAt==null || leaseExpiresAt.isAfter(at))
      throw new IllegalStateException("lease가 만료되지 않았습니다.");
    if (status==TrainingJobStatus.CANCEL_REQUESTED) {
      cancel(at); releaseAssignment(); return;
    }
    if (attempt>=maxAttempts) {
      fail("TRAINING_LEASE_EXHAUSTED","학습 Worker lease 재시도 한도를 초과했습니다.",at);
      releaseAssignment(); return;
    }
    attempt++;
    status=TrainingJobStatus.QUEUED;
    progressStage="재할당 대기";
    releaseAssignment();
  }

  public String getAssignedWorkerId() { return assignedWorkerId; }
  public UUID getAssignedStorageNodeId() { return assignedStorageNodeId; }
  public int getAttempt() { return attempt; }
  public String getLeaseTokenHash() { return leaseTokenHash; }
  public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
  public Map<String, Object> getDatasetQuery() { return datasetQuery; }
  public String getInputSnapshotSha256() { return inputSnapshotSha256; }
  public void fixInputSnapshotSha256(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("input snapshot hash가 올바르지 않습니다.");
    if (inputSnapshotSha256 != null && !inputSnapshotSha256.equals(value))
      throw new IllegalStateException("고정된 input snapshot hash를 변경할 수 없습니다.");
    inputSnapshotSha256 = value;
  }
  public Instant getModelCompletedAt() { return modelCompletedAt; }
}
