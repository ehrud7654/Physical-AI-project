package com.anssa8bu.ssafy.episode.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import com.anssa8bu.ssafy.episode.vo.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "episodes")
public class Episode extends AuditableEntity {
  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(name = "umi_device_id", nullable = false, updatable = false)
  private UUID umiDeviceId;

  @Column(name = "storage_node_id", nullable = false, updatable = false)
  private UUID storageNodeId;

  @Column(name = "retention_policy_id", nullable = false, updatable = false)
  private UUID retentionPolicyId;

  @Column(name = "skill_id", nullable = false, updatable = false)
  private UUID skillId;

  @Column(name = "session_id", nullable = false, updatable = false, length = 150)
  private String sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private EpisodeSource source;

  private Boolean success;

  @Enumerated(EnumType.STRING)
  @Column(name = "label_status", nullable = false, length = 20)
  private EpisodeLabelStatus labelStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "label_source", length = 20)
  private EpisodeLabelSource labelSource;

  @Column(name = "labeled_at")
  private Instant labeledAt;

  @Column(name = "contract_version", nullable = false, updatable = false, length = 100)
  private String contractVersion;

  @Column(name = "meta_schema_version", nullable = false, updatable = false, length = 50)
  private String metaSchemaVersion;

  @Column(name = "summary_schema_version", nullable = false, updatable = false, length = 50)
  private String summarySchemaVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EpisodeStatus status;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "ended_at", nullable = false, updatable = false)
  private Instant endedAt;

  @Column(name = "start_monotonic_ns", nullable = false, updatable = false)
  private long startMonotonicNs;

  @Enumerated(EnumType.STRING)
  @Column(name = "timestamp_timebase", nullable = false, updatable = false, length = 30)
  private TimestampTimebase timestampTimebase;

  @Column(name = "first_timestamp_ns", nullable = false, updatable = false)
  private long firstTimestampNs;

  @Column(name = "last_timestamp_ns", nullable = false, updatable = false)
  private long lastTimestampNs;

  @Column(name = "duration_ns", nullable = false, updatable = false)
  private long durationNs;

  @Column(name = "frame_count", nullable = false, updatable = false)
  private int frameCount;

  @Column(name = "pose_sample_count", nullable = false, updatable = false)
  private int poseSampleCount;

  @Column(name = "imu_sample_count", nullable = false, updatable = false)
  private int imuSampleCount;

  @Column(name = "gripper_sample_count", nullable = false, updatable = false)
  private int gripperSampleCount;

  @Column(name = "missing_frame_count", nullable = false, updatable = false)
  private int missingFrameCount;

  @Column(name = "tracking_gap_count", nullable = false, updatable = false)
  private int trackingGapCount;

  @Column(name = "tracking_lost_duration_ns", nullable = false, updatable = false)
  private long trackingLostDurationNs;

  @Enumerated(EnumType.STRING)
  @Column(name = "quality_status", nullable = false, length = 20)
  private EpisodeQualityStatus qualityStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "capture_metadata", nullable = false, columnDefinition = "jsonb")
  private CaptureMetadata captureMetadata;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "quality_summary", nullable = false, columnDefinition = "jsonb")
  private QualitySummary qualitySummary;

  @Column(name = "validated_at")
  private Instant validatedAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "retention_expires_at", nullable = false)
  private Instant retentionExpiresAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected Episode() {}

  public Episode(
      UUID id,
      EpisodeIdentity identity,
      Boolean success,
      EpisodeLabelStatus labelStatus,
      EpisodeLabelSource labelSource,
      Instant labeledAt,
      String contractVersion,
      String metaSchemaVersion,
      String summarySchemaVersion,
      EpisodeStatus status,
      EpisodeTiming timing,
      EpisodeSampleSummary samples,
      EpisodeQualityStatus qualityStatus,
      CaptureMetadata captureMetadata,
      QualitySummary qualitySummary,
      Instant retentionExpiresAt) {
    this.id = Objects.requireNonNull(id);
    Objects.requireNonNull(identity);
    Objects.requireNonNull(timing);
    Objects.requireNonNull(samples);
    validateLabel(success, labelStatus, labelSource, labeledAt);
    this.ownerId = identity.ownerId();
    this.umiDeviceId = identity.umiDeviceId();
    this.storageNodeId = identity.storageNodeId();
    this.retentionPolicyId = identity.retentionPolicyId();
    this.skillId = identity.skillId();
    this.sessionId = identity.sessionId();
    this.source = identity.source();
    this.success = success;
    this.labelStatus = labelStatus;
    this.labelSource = labelSource;
    this.labeledAt = labeledAt;
    this.contractVersion = text(contractVersion);
    this.metaSchemaVersion = text(metaSchemaVersion);
    this.summarySchemaVersion = text(summarySchemaVersion);
    this.status = Objects.requireNonNull(status);
    this.startedAt = timing.startedAt();
    this.endedAt = timing.endedAt();
    this.startMonotonicNs = timing.startMonotonicNs();
    this.timestampTimebase = TimestampTimebase.MONOTONIC_NS;
    this.firstTimestampNs = timing.firstTimestampNs();
    this.lastTimestampNs = timing.lastTimestampNs();
    this.durationNs = timing.durationNs();
    this.frameCount = samples.frameCount();
    this.poseSampleCount = samples.poseSampleCount();
    this.imuSampleCount = samples.imuSampleCount();
    this.gripperSampleCount = samples.gripperSampleCount();
    this.missingFrameCount = samples.missingFrameCount();
    this.trackingGapCount = samples.trackingGapCount();
    this.trackingLostDurationNs = samples.trackingLostDurationNs();
    this.qualityStatus = Objects.requireNonNull(qualityStatus);
    this.captureMetadata = Objects.requireNonNull(captureMetadata);
    this.qualitySummary = Objects.requireNonNull(qualitySummary);
    this.retentionExpiresAt = Objects.requireNonNull(retentionExpiresAt);
  }

  private static String text(String v) {
    if (v == null || v.isBlank()) throw new IllegalArgumentException("text must not be blank");
    return v;
  }

  private static void validateLabel(
      Boolean success, EpisodeLabelStatus status, EpisodeLabelSource source, Instant at) {
    Objects.requireNonNull(status);
    boolean complete = success != null && source != null && at != null;
    if ((status == EpisodeLabelStatus.LABELED) != complete)
      throw new IllegalArgumentException("label fields must match labelStatus");
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public UUID getUmiDeviceId() {
    return umiDeviceId;
  }

  public UUID getStorageNodeId() {
    return storageNodeId;
  }

  public UUID getRetentionPolicyId() {
    return retentionPolicyId;
  }

  public UUID getSkillId() {
    return skillId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public EpisodeSource getSource() {
    return source;
  }

  public Boolean getSuccess() {
    return success;
  }

  public EpisodeLabelStatus getLabelStatus() {
    return labelStatus;
  }

  public EpisodeLabelSource getLabelSource() {
    return labelSource;
  }

  public Instant getLabeledAt() {
    return labeledAt;
  }

  public EpisodeStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public long getDurationNs() {
    return durationNs;
  }

  public EpisodeQualityStatus getQualityStatus() {
    return qualityStatus;
  }

  public CaptureMetadata getCaptureMetadata() {
    return captureMetadata;
  }

  public QualitySummary getQualitySummary() {
    return qualitySummary;
  }

  public Instant getValidatedAt() {
    return validatedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public String getContractVersion() {
    return contractVersion;
  }

  public String getMetaSchemaVersion() {
    return metaSchemaVersion;
  }

  public String getSummarySchemaVersion() {
    return summarySchemaVersion;
  }

  public long getStartMonotonicNs() {
    return startMonotonicNs;
  }

  public TimestampTimebase getTimestampTimebase() {
    return timestampTimebase;
  }

  public long getFirstTimestampNs() {
    return firstTimestampNs;
  }

  public long getLastTimestampNs() {
    return lastTimestampNs;
  }

  public int getFrameCount() {
    return frameCount;
  }

  public int getPoseSampleCount() {
    return poseSampleCount;
  }

  public int getImuSampleCount() {
    return imuSampleCount;
  }

  public int getGripperSampleCount() {
    return gripperSampleCount;
  }

  public int getMissingFrameCount() {
    return missingFrameCount;
  }

  public int getTrackingGapCount() {
    return trackingGapCount;
  }

  public long getTrackingLostDurationNs() {
    return trackingLostDurationNs;
  }

  public Instant getRetentionExpiresAt() {
    return retentionExpiresAt;
  }
}
