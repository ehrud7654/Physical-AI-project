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
}
