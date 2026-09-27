package com.anssa8bu.ssafy.jetson.dto;

import com.anssa8bu.ssafy.deployment.entity.RobotSkillDeploymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JetsonStateModels {
  private JetsonStateModels() {}
  public record CurrentDeployment(UUID deploymentId, UUID taskVersionId,
      List<JetsonCommand.Step> steps, List<ActiveSkill> skills, Instant appliedAt) {}
  public record ActiveSkill(UUID skillId, String skillKey, UUID skillVersionId, int version,
      UUID deploymentId, RobotSkillDeploymentStatus status, Instant deployedAt) {}
}
