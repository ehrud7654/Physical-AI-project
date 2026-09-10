package com.anssa8bu.ssafy.execution.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "robot_executions")
@EntityListeners(AuditingEntityListener.class)
public class RobotExecution {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "robot_id", nullable = false)
  private UUID robotId;

  @Column(name = "task_version_id", nullable = false)
  private UUID taskVersionId;

  @Column(name = "deployment_id", nullable = false)
  private UUID deploymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RobotExecutionStatus status;

  @Column(name = "current_step")
  private Integer currentStep;

  @Column(name = "total_steps", nullable = false)
  private int totalSteps;

  @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal progressPercent;

  @Column(name = "last_report_id", length = 150)
  private String lastReportId;

  @Column(name = "last_report_sequence", nullable = false)
  private long lastReportSequence;

  @Column(name = "last_report_hash", length = 64)
  private String lastReportHash;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "recovery_action", length = 40)
  private ExecutionRecoveryAction recoveryAction;

  @CreatedDate
  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected RobotExecution() {}

  public RobotExecution(
      UUID id, UUID ownerId, UUID robotId, UUID taskVersionId, UUID deploymentId, int totalSteps) {
    if (totalSteps < 1) throw new IllegalArgumentException("totalSteps must be positive");
    this.id = id;
    this.ownerId = ownerId;
    this.robotId = robotId;
    this.taskVersionId = taskVersionId;
    this.deploymentId = deploymentId;
    this.totalSteps = totalSteps;
    status = RobotExecutionStatus.REQUESTED;
    progressPercent = BigDecimal.ZERO;
  }

  public void acceptReport(
      String reportId,
      long sequence,
      String reportHash,
      RobotExecutionStatus nextStatus,
      Integer currentStep,
      BigDecimal progressPercent,
      Instant occurredAt) {
    if (sequence <= lastReportSequence) throw new IllegalArgumentException("sequence must increase");
    if (reportId == null || reportId.isBlank()) throw new IllegalArgumentException("reportId is required");
    if (reportHash == null || !reportHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("reportHash must be lowercase SHA-256");
    }
    if (currentStep != null && (currentStep < 1 || currentStep > totalSteps)) {
      throw new IllegalArgumentException("currentStep is out of range");
    }
    if (progressPercent == null
        || progressPercent.compareTo(BigDecimal.ZERO) < 0
        || progressPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("progressPercent is out of range");
    }
    lastReportId = reportId;
    lastReportSequence = sequence;
    lastReportHash = reportHash;
    status = nextStatus;
    this.currentStep = currentStep;
    this.progressPercent = progressPercent;
    if (nextStatus == RobotExecutionStatus.STARTING && startedAt == null) startedAt = occurredAt;
    if (nextStatus == RobotExecutionStatus.SUCCEEDED
        || nextStatus == RobotExecutionStatus.CANCELED
        || nextStatus == RobotExecutionStatus.FAILED) finishedAt = occurredAt;
  }

  public void fail(String code, String message, ExecutionRecoveryAction recoveryAction, Instant occurredAt) {
    if (code == null || code.isBlank() || message == null || message.isBlank()) {
      throw new IllegalArgumentException("failure code and message are required");
    }
    status = RobotExecutionStatus.FAILED;
    errorCode = code;
    errorMessage = message;
    this.recoveryAction = recoveryAction;
    finishedAt = occurredAt;
  }

  public UUID getId() { return id; }
  public UUID getOwnerId() { return ownerId; }
  public UUID getRobotId() { return robotId; }
  public UUID getTaskVersionId() { return taskVersionId; }
  public UUID getDeploymentId() { return deploymentId; }
  public Integer getCurrentStep() { return currentStep; }
  public RobotExecutionStatus getStatus() { return status; }
  public int getTotalSteps() { return totalSteps; }
  public BigDecimal getProgressPercent() { return progressPercent; }
  public long getLastReportSequence() { return lastReportSequence; }
  public String getLastReportId() { return lastReportId; }
  public String getLastReportHash() { return lastReportHash; }
  public String getErrorCode() { return errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public ExecutionRecoveryAction getRecoveryAction() { return recoveryAction; }
  public Instant getRequestedAt() { return requestedAt; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getFinishedAt() { return finishedAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Long getRowVersion() { return rowVersion; }

  public void requestCancel(Instant occurredAt) {
    if (status == RobotExecutionStatus.REQUESTED) {
      status = RobotExecutionStatus.CANCELED;
      finishedAt = occurredAt;
      return;
    }
    if (status == RobotExecutionStatus.STARTING || status == RobotExecutionStatus.RUNNING) {
      status = RobotExecutionStatus.CANCEL_REQUESTED;
      return;
    }
    if (status != RobotExecutionStatus.CANCEL_REQUESTED) {
      throw new IllegalStateException("execution is already terminal");
    }
  }
}

