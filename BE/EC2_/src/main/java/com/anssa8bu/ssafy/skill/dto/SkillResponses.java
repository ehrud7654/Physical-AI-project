package com.anssa8bu.ssafy.skill.dto;

import com.anssa8bu.ssafy.skill.entity.*;
import java.time.Instant;
import java.util.UUID;

public final class SkillResponses {
  private SkillResponses() {}

  public record SkillResponse(
      UUID id,
      String skillKey,
      SkillTier tier,
      SkillStatus status,
      String displayName,
      String description,
      String persona,
      Instant createdAt,
      Instant updatedAt) {}

  public record SkillVersionResponse(
      UUID id,
      UUID skillId,
      int version,
      String status,
      long checkpointBytes,
      String checkpointSha256,
      UUID robotProfileId,
      String contractVersion,
      boolean gatePassed,
      Instant createdAt) {}

  public record SkillOverviewResponse(
      String displayName,
      String description,
      Integer latestVersion,
      Instant latestVersionCreatedAt,
      SkillStatus deploymentStatus,
      Instant latestTrainingRequestedAt) {}

  public record TrainingRequirementsResponse(
      UUID skillId,
      String skillKey,
      int minimumEpisodeCount,
      int recommendedEpisodeCount,
      int maximumEpisodeCount,
      java.util.List<String> allowedSources,
      boolean requiresSameUmiDevice,
      String requiredContractVersion,
      boolean supported,
      int requirementsVersion) {}
}
