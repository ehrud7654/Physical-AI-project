package com.anssa8bu.ssafy.episodeupload.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "episode_upload_sessions")
public class EpisodeUploadSession extends AuditableEntity {
  @Id private UUID id;
  @Column(name = "episode_id", nullable = false, updatable = false) private UUID episodeId;
  @Column(name = "training_job_id", updatable = false) private UUID trainingJobId;
  @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
  @Column(name = "umi_device_id", nullable = false, updatable = false) private UUID umiDeviceId;
  @Column(name = "session_id", nullable = false, updatable = false) private UUID sessionId;
  @Column(name = "content_type", nullable = false, updatable = false) private String contentType;
  @Column(name = "size_bytes", nullable = false, updatable = false) private long sizeBytes;
  @Column(nullable = false, updatable = false, length = 64) private String sha256;
  @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;
  @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, Object> summary;
  @Column(name = "bucket_name", nullable = false, updatable = false) private String bucketName;
  @Column(name = "object_key", nullable = false, updatable = false, length = 500) private String objectKey;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EpisodeUploadStatus status;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "canceled_at") private Instant canceledAt;
  @Column(name = "quality_status", length = 20) private String qualityStatus;
  @Column(name = "validation_error_code", length = 100) private String validationErrorCode;
  @Column(name = "validation_error_message", columnDefinition = "text") private String validationErrorMessage;
  @Column(name = "validated_at") private Instant validatedAt;
  @Version @Column(name = "row_version", nullable = false) private Long rowVersion;

  protected EpisodeUploadSession() {}

  public EpisodeUploadSession(UUID id, UUID episodeId, UUID trainingJobId, UUID ownerId,
      UUID umiDeviceId, UUID sessionId, long sizeBytes, String sha256,
      Map<String, Object> metadata, Map<String, Object> summary, String bucketName,
      String objectKey, Instant expiresAt) {
    this.id = Objects.requireNonNull(id); this.episodeId = Objects.requireNonNull(episodeId);
    this.trainingJobId = trainingJobId; this.ownerId = Objects.requireNonNull(ownerId);
    this.umiDeviceId = Objects.requireNonNull(umiDeviceId); this.sessionId = Objects.requireNonNull(sessionId);
    this.contentType = "application/zip"; this.sizeBytes = sizeBytes; this.sha256 = sha256;
    this.metadata = Map.copyOf(metadata); this.summary = Map.copyOf(summary);
    this.bucketName = bucketName; this.objectKey = objectKey;
    this.status = EpisodeUploadStatus.UPLOAD_PENDING; this.expiresAt = expiresAt;
  }

  public void renew(Instant expiration) {
    if (status != EpisodeUploadStatus.UPLOAD_PENDING) {
      throw new IllegalStateException("only pending upload session can be renewed");
    }
    expiresAt = Objects.requireNonNull(expiration);
  }
  public void complete(Instant at) { status = EpisodeUploadStatus.VALIDATING; completedAt = at; }
  public void retryValidation() {
    if (status != EpisodeUploadStatus.FAILED
        || !"VALIDATION_PROCESSING_FAILED".equals(validationErrorCode)) {
      throw new IllegalStateException("only processing failures can retry validation");
    }
    status = EpisodeUploadStatus.VALIDATING;
    qualityStatus = null;
    validationErrorCode = null;
    validationErrorMessage = null;
    validatedAt = null;
  }
  public void markReady(String qualityStatus, Instant at) {
    requireValidating();
    if (!"VALID".equals(qualityStatus) && !"WARNING".equals(qualityStatus)) {
      throw new IllegalArgumentException("ready quality status must be VALID or WARNING");
    }
    status = EpisodeUploadStatus.READY;
    this.qualityStatus = qualityStatus;
    validatedAt = Objects.requireNonNull(at);
  }
  public void markInvalid(String code, String message, Instant at) {
    finishFailure(EpisodeUploadStatus.INVALID, "INVALID", code, message, at);
  }
  public void markFailed(String code, String message, Instant at) {
    finishFailure(EpisodeUploadStatus.FAILED, null, code, message, at);
  }
  private void finishFailure(
      EpisodeUploadStatus result, String quality, String code, String message, Instant at) {
    requireValidating();
    status = result;
    qualityStatus = quality;
    validationErrorCode = requireText(code);
    validationErrorMessage = requireText(message);
    validatedAt = Objects.requireNonNull(at);
  }
  private void requireValidating() {
    if (status != EpisodeUploadStatus.VALIDATING) {
      throw new IllegalStateException("upload session is not validating");
    }
  }
  private static String requireText(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank");
    return value;
  }
  public void cancel(Instant at) {
    if (status != EpisodeUploadStatus.UPLOAD_PENDING) {
      throw new IllegalStateException("only pending upload session can be cancelled");
    }
    status = EpisodeUploadStatus.CANCELLED;
    canceledAt = Objects.requireNonNull(at);
  }
  public UUID getId() { return id; }
  public UUID getEpisodeId() { return episodeId; }
  public UUID getTrainingJobId() { return trainingJobId; }
  public UUID getOwnerId() { return ownerId; }
  public UUID getUmiDeviceId() { return umiDeviceId; }
  public UUID getSessionId() { return sessionId; }
  public long getSizeBytes() { return sizeBytes; }
  public String getSha256() { return sha256; }
  public Map<String, Object> getMetadata() { return metadata; }
  public Map<String, Object> getSummary() { return summary; }
  public String getBucketName() { return bucketName; }
  public String getObjectKey() { return objectKey; }
  public EpisodeUploadStatus getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getCanceledAt() { return canceledAt; }
  public String getQualityStatus() { return qualityStatus; }
  public String getValidationErrorCode() { return validationErrorCode; }
  public String getValidationErrorMessage() { return validationErrorMessage; }
  public Instant getValidatedAt() { return validatedAt; }
}
