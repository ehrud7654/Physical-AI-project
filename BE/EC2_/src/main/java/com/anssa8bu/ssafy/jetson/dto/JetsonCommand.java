package com.anssa8bu.ssafy.jetson.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JetsonCommand(
    String commandId,
    long sequence,
    String type,
    UUID robotId,
    UUID executionId,
    UUID taskVersionId,
    UUID deploymentId,
    List<Step> steps,
    Instant issuedAt,
    String contractVersion) {

  public record Step(
      UUID stepId,
      UUID parentStepId,
      int order,
      String type,
      UUID skillVersionId,
      Integer timeoutSeconds,
      Integer repeatCount,
      Map<String, Object> parameters) {}
}
