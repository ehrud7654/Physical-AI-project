package com.anssa8bu.ssafy.jetson.dto;

import com.anssa8bu.ssafy.execution.entity.ExecutionRecoveryAction;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record JetsonExecutionReportRequest(
    @NotBlank @Size(max = 150) String reportId,
    @Positive long sequence,
    @NotNull RobotExecutionStatus status,
    @Positive Integer currentStep,
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
    @Valid ErrorInfo error) {

  public record ErrorInfo(
      @NotBlank @Size(max = 100) String code,
      @NotBlank String message,
      ExecutionRecoveryAction recoveryAction) {}
}
