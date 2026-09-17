package com.anssa8bu.ssafy.deployment.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployments")
public class Deployment extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "robot_id", nullable = false)
  private UUID robotId;

  @Column(name = "task_version_id", nullable = false)
  private UUID taskVersionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DeploymentStatus status;

  @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal progressPercent;

  @Column(name = "last_report_sequence", nullable = false)
  private long lastReportSequence;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected Deployment() {}

  public Deployment(UUID id, UUID robot, UUID taskVersion) {
    this.id = id;
    robotId = robot;
    taskVersionId = taskVersion;
    status = DeploymentStatus.PENDING;
    progressPercent = BigDecimal.ZERO;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRobotId() {
    return robotId;
  }

  public UUID getTaskVersionId() {
    return taskVersionId;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public void cancel() {
    if (status == DeploymentStatus.SUCCEEDED
        || status == DeploymentStatus.FAILED
        || status == DeploymentStatus.CANCELED) {
      throw new IllegalStateException("deployment is already terminal");
    }
    status = DeploymentStatus.CANCELED;
  }

  public void failPendingTimeout(String errorCode, String errorMessage) {
    if (status != DeploymentStatus.PENDING) {
      throw new IllegalStateException("only pending deployment can time out");
    }
    status = DeploymentStatus.FAILED;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public DeploymentStatus getStatus() {
    return status;
  }

  public BigDecimal getProgressPercent() {
    return progressPercent;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public long getLastReportSequence() {
    return lastReportSequence;
  }

  public void acceptReport(
      long sequence,
      DeploymentStatus nextStatus,
      BigDecimal progressPercent,
      String errorCode,
      String errorMessage,
      Instant receivedAt) {
    if (sequence <= lastReportSequence)
      throw new IllegalArgumentException("sequence must increase");
    if (progressPercent == null
        || progressPercent.compareTo(BigDecimal.ZERO) < 0
        || progressPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("progressPercent is out of range");
    }
    if (nextStatus == DeploymentStatus.FAILED
        && (errorCode == null
            || errorCode.isBlank()
            || errorMessage == null
            || errorMessage.isBlank())) {
      throw new IllegalArgumentException("failed deployment requires an error");
    }
    this.lastReportSequence = sequence;
    this.status = nextStatus;
    this.progressPercent = progressPercent;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    if (nextStatus == DeploymentStatus.SUCCEEDED && appliedAt == null) appliedAt = receivedAt;
  }
}
