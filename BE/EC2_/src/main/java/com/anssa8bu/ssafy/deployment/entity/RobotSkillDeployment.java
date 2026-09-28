package com.anssa8bu.ssafy.deployment.entity;

import com.anssa8bu.ssafy.common.entity.CreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "robot_skill_deployments")
public class RobotSkillDeployment extends CreatedEntity {
  @Id private UUID id;

  @Column(name = "robot_id", nullable = false)
  private UUID robotId;

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @Column(name = "skill_version_id", nullable = false)
  private UUID skillVersionId;

  @Column(name = "deployment_id", nullable = false)
  private UUID deploymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RobotSkillDeploymentStatus status;

  @Column(name = "deployed_at", nullable = false)
  private Instant deployedAt;

  @Column(name = "retired_at")
  private Instant retiredAt;

  protected RobotSkillDeployment() {}

  public RobotSkillDeployment(UUID id, UUID robotId, UUID skillId, UUID skillVersionId,
      UUID deploymentId, Instant deployedAt) {
    this.id = id; this.robotId = robotId; this.skillId = skillId;
    this.skillVersionId = skillVersionId; this.deploymentId = deploymentId;
    this.status = RobotSkillDeploymentStatus.ACTIVE; this.deployedAt = deployedAt;
  }

  public void retire(Instant at) { status = RobotSkillDeploymentStatus.RETIRED; retiredAt = at; }
  public UUID getId() { return id; }
  public UUID getRobotId() { return robotId; }
  public UUID getSkillId() { return skillId; }
  public UUID getSkillVersionId() { return skillVersionId; }
  public UUID getDeploymentId() { return deploymentId; }
  public RobotSkillDeploymentStatus getStatus() { return status; }
  public Instant getDeployedAt() { return deployedAt; }
  public Instant getRetiredAt() { return retiredAt; }
}
