package com.anssa8bu.ssafy.deployment.dto;

import com.anssa8bu.ssafy.deployment.entity.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class DeploymentApiModels {
  private DeploymentApiModels() {}

  public record CreateRequest(@NotNull UUID taskVersionId) {}

  public record ErrorInfo(String code, String message) {}

  public record ItemResponse(
      UUID id,
      UUID skillVersionId,
      boolean gatePassed,
      DeploymentItemStatus status,
      Instant downloadedAt,
      Instant verifiedAt,
      ErrorInfo error) {}

  public record DeploymentResponse(
      UUID id,
      UUID robotId,
      UUID taskVersionId,
      DeploymentStatus status,
      BigDecimal progressPercent,
      List<ItemResponse> items,
      ErrorInfo error,
      Instant createdAt,
      Instant updatedAt,
      Instant appliedAt) {}
}
