package com.anssa8bu.ssafy.gate.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class GateApiModels {
  private GateApiModels() {}
  public record GateResultRequest(@NotBlank String metric, @Positive int n, @Positive int runs,
      @DecimalMin("0.0") @DecimalMax("1.0") double value,
      @DecimalMin("0.0") @DecimalMax("1.0") double ciLow,
      @DecimalMin("0.0") @DecimalMax("1.0") double ciHigh, @NotNull Instant measuredAt) {}
  public record GateResultResponse(UUID skillVersionId, String status, boolean gatePassed,
      String metric, int n, int runs, double value, double ciLow, double ciHigh,
      Instant measuredAt) {}
}
