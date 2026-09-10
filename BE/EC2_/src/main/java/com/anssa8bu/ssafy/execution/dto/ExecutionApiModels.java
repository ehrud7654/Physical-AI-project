package com.anssa8bu.ssafy.execution.dto;
import com.anssa8bu.ssafy.execution.entity.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class ExecutionApiModels {
  private ExecutionApiModels() {}
  public record CreateRequest(@NotNull UUID taskVersionId,@NotNull UUID deploymentId) {}
  public record ErrorInfo(String code,String message,ExecutionRecoveryAction recoveryAction) {}
  public record ExecutionResponse(UUID id,UUID ownerId,UUID robotId,UUID taskVersionId,UUID deploymentId,
      RobotExecutionStatus status,Integer currentStep,int totalSteps,BigDecimal progressPercent,ErrorInfo error,
      Instant requestedAt,Instant startedAt,Instant finishedAt,Instant updatedAt) {}
}
