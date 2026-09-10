package com.anssa8bu.ssafy.training.dto;

import com.anssa8bu.ssafy.training.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class TrainingJobApiModels {
  private TrainingJobApiModels() {}
  public record CreateRequest(@NotBlank String skillKey, @NotNull UUID umiDeviceId,
      @NotNull TrainingInputMode inputMode, @Positive Integer expectedEpisodeCount,
      List<UUID> episodeIds) {}
  public record JobResponse(UUID id, String skillKey, String skillDisplayName, UUID umiDeviceId,
      TrainingInputMode inputMode, TrainingJobStatus status, String progressStage,
      BigDecimal progressPercent, int expectedEpisodeCount, int receivedEpisodeCount,
      long receivedBytes, int episodeCount, UUID resultSkillVersionId, ErrorInfo error,
      Instant createdAt, Instant startedAt, Instant finishedAt, Instant updatedAt) {}
  public record ErrorInfo(String code, String message, String recoveryAction) {}
  public record EpisodeInputResponse(UUID episodeId, int inputOrder) {}
  public record InputPlanUpdateRequest(@NotNull @Positive Integer expectedEpisodeCount) {}
  public record InputCompleteRequest(@NotNull @Positive Integer receivedEpisodeCount) {}
  public record InputCompleteResponse(UUID trainingJobId, TrainingJobStatus status) {}
}
