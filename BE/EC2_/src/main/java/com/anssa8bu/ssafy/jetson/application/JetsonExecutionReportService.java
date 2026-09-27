package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.ErrorInfo;
import com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.ExecutionResponse;
import com.anssa8bu.ssafy.execution.entity.ExecutionRecoveryAction;
import com.anssa8bu.ssafy.execution.entity.RobotExecution;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import com.anssa8bu.ssafy.jetson.dto.JetsonExecutionReportRequest;
import com.anssa8bu.ssafy.robot.entity.Robot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JetsonExecutionReportService {
  private final JetsonIdentityService identity;
  private final RobotExecutionRepository executions;
  private final Clock clock;

  public JetsonExecutionReportService(
      JetsonIdentityService identity, RobotExecutionRepository executions, Clock clock) {
    this.identity = identity;
    this.executions = executions;
    this.clock = clock;
  }

  @Transactional
  public ExecutionResponse report(Jwt jwt, UUID executionId, JetsonExecutionReportRequest request) {
    Robot robot = identity.requireRobot(jwt);
    RobotExecution execution =
        executions
            .findByIdAndRobotIdAndOwnerId(executionId, robot.getId(), robot.getOwnerId())
            .orElseThrow(() -> new ResourceNotFoundException("RobotExecution"));
    String hash = hash(request);
    if (request.sequence() == execution.getLastReportSequence()
        && request.reportId().equals(execution.getLastReportId())
        && hash.equals(execution.getLastReportHash())) {
      return response(execution);
    }
    if (request.sequence() <= execution.getLastReportSequence()) {
      throw new BusinessConflictException("REPORT_OUT_OF_ORDER", "실행 보고 sequence가 중복되거나 역순입니다.");
    }
    validateFailure(request);
    Instant now = clock.instant();
    try {
      execution.acceptReport(
          request.reportId(),
          request.sequence(),
          hash,
          request.status(),
          request.currentStep(),
          request.progressPercent(),
          now);
      if (request.status() == RobotExecutionStatus.FAILED) {
        JetsonExecutionReportRequest.ErrorInfo error = request.error();
        execution.fail(
            error.code(),
            error.message(),
            error.recoveryAction() == null
                ? ExecutionRecoveryAction.CONTACT_SUPPORT
                : error.recoveryAction(),
            now);
      }
    } catch (IllegalArgumentException exception) {
      throw new BusinessConflictException("INVALID_EXECUTION_REPORT", exception.getMessage());
    }
    return response(execution);
  }

  private void validateFailure(JetsonExecutionReportRequest request) {
    if (request.status() == RobotExecutionStatus.FAILED && request.error() == null) {
      throw new BusinessConflictException("INVALID_EXECUTION_REPORT", "FAILED 보고에는 error가 필요합니다.");
    }
    if (request.status() != RobotExecutionStatus.FAILED && request.error() != null) {
      throw new BusinessConflictException(
          "INVALID_EXECUTION_REPORT", "FAILED 이외 상태에는 error를 보낼 수 없습니다.");
    }
  }

  private String hash(JetsonExecutionReportRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(request.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private ExecutionResponse response(RobotExecution execution) {
    return new ExecutionResponse(
        execution.getId(),
        execution.getOwnerId(),
        execution.getRobotId(),
        execution.getTaskVersionId(),
        execution.getDeploymentId(),
        execution.getStatus(),
        execution.getCurrentStep(),
        execution.getTotalSteps(),
        execution.getProgressPercent(),
        execution.getErrorCode() == null
            ? null
            : new ErrorInfo(
                execution.getErrorCode(),
                execution.getErrorMessage(),
                execution.getRecoveryAction()),
        execution.getRequestedAt(),
        execution.getStartedAt(),
        execution.getFinishedAt(),
        execution.getUpdatedAt());
  }
}
