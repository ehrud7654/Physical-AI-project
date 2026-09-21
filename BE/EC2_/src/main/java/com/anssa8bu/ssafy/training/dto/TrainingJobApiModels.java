package com.anssa8bu.ssafy.training.dto;

import com.anssa8bu.ssafy.skill.dto.SkillApiModels.CreateCustomSkillRequest;
import com.anssa8bu.ssafy.training.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class TrainingJobApiModels {
  private TrainingJobApiModels() {}

  public record CreateRequest(
      String skillKey,
      @Valid CreateCustomSkillRequest newSkill,
      @NotNull UUID umiDeviceId,
      List<UUID> episodeIds,
      TrainingMode trainingMode,
      UUID baseSkillVersionId,
      @NotNull UUID mappingEpisodeId) {}

  public record JobResponse(
      UUID id,
      String skillKey,
      String skillDisplayName,
      UUID umiDeviceId,
      TrainingInputMode inputMode,
      TrainingMode trainingMode,
      UUID baseSkillVersionId,
      TrainingJobStatus status,
      String progressStage,
      BigDecimal progressPercent,
      int expectedEpisodeCount,
      int receivedEpisodeCount,
      long receivedBytes,
      int episodeCount,
      UUID resultSkillVersionId,
      ErrorInfo error,
      Instant createdAt,
      Instant startedAt,
      Instant finishedAt,
      Instant updatedAt) {}

  public record ErrorInfo(String code, String message, String recoveryAction) {}

  public record EpisodeInputResponse(
      UUID episodeId, int inputOrder, TrainingEpisodeRole inputRole) {}

}
