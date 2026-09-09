package com.anssa8bu.ssafy.deployment.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployment_items")
public class DeploymentItem extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "deployment_id", nullable = false)
  private UUID deploymentId;

  @Column(name = "skill_version_id", nullable = false)
  private UUID skillVersionId;

  @Column(name = "gate_passed", nullable = false)
  private boolean gatePassed;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DeploymentItemStatus status;

  @Column(name = "downloaded_at")
  private Instant downloadedAt;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  protected DeploymentItem() {}

  public DeploymentItem(UUID id, UUID deploymentId, UUID skillVersionId) {
    this.id = id;
    this.deploymentId = deploymentId;
    this.skillVersionId = skillVersionId;
    this.gatePassed = true;
    this.status = DeploymentItemStatus.PENDING;
  }

  public UUID getId() { return id; }
  public UUID getDeploymentId() { return deploymentId; }
  public UUID getSkillVersionId() { return skillVersionId; }
  public boolean isGatePassed() { return gatePassed; }
  public DeploymentItemStatus getStatus() { return status; }
  public Instant getDownloadedAt() { return downloadedAt; }
  public Instant getVerifiedAt() { return verifiedAt; }
  public String getErrorCode() { return errorCode; }
  public String getErrorMessage() { return errorMessage; }
}
