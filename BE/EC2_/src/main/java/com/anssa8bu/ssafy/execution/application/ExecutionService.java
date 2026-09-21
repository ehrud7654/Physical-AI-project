package com.anssa8bu.ssafy.execution.application;

import static com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.*;

import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.deployment.entity.*;
import com.anssa8bu.ssafy.deployment.repository.DeploymentRepository;
import com.anssa8bu.ssafy.execution.entity.*;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import com.anssa8bu.ssafy.robot.entity.*;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import com.anssa8bu.ssafy.task.entity.TaskStep;
import com.anssa8bu.ssafy.task.entity.TaskStepType;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionService {
  private static final Set<RobotExecutionStatus> ACTIVE =
      EnumSet.of(
          RobotExecutionStatus.REQUESTED,
          RobotExecutionStatus.STARTING,
          RobotExecutionStatus.RUNNING,
          RobotExecutionStatus.CANCEL_REQUESTED,
          RobotExecutionStatus.STATUS_UNKNOWN);
  private final RuntimeStateService runtime;
  private final RobotExecutionRepository executions;
  private final RobotRepository robots;
  private final DeploymentRepository deployments;
  private final TaskStepRepository steps;
  private final Clock clock;
  private final IdempotencyGuard idempotency;

  public ExecutionService(
      RobotExecutionRepository e,
      RobotRepository r,
      DeploymentRepository d,
      TaskStepRepository s,
      Clock c,
      IdempotencyGuard idempotency,
      RuntimeStateService runtime) {
    executions = e;
    robots = r;
    deployments = d;
    steps = s;
    clock = c;
    this.idempotency = idempotency;
    this.runtime = runtime;
  }

  @Transactional
  public ExecutionResponse create(
      UUID owner, UUID robotId, String idempotencyKey, CreateRequest request) {
    String path = "/api/v1/robots/" + robotId + "/executions";
    var claim = idempotency.claim(owner, "POST", path, idempotencyKey, request);
    if (claim.existingResourceId() != null) return get(owner, robotId, claim.existingResourceId());
    Robot robot =
        robots
            .findByIdAndOwnerId(robotId, owner)
            .orElseThrow(() -> new ResourceNotFoundException("Robot"));
    runtime.requireOnline(robot.getJetsonDeviceId());
    if (executions.existsByRobotIdAndStatusIn(robotId, ACTIVE))
      throw new BusinessConflictException("ROBOT_BUSY", "이미 실행 중인 작업이 있습니다.");
    Deployment deployment =
        deployments
            .findByIdAndRobotId(request.deploymentId(), robotId)
            .orElseThrow(() -> new ResourceNotFoundException("Deployment"));
    if (deployment.getStatus() != DeploymentStatus.SUCCEEDED
        || !deployment.getTaskVersionId().equals(request.taskVersionId()))
      throw new BusinessConflictException("DEPLOYMENT_NOT_READY", "완료된 현재 배포가 아닙니다.");
    if (!Objects.equals(robot.getCurrentDeploymentId(), deployment.getId()))
      throw new BusinessConflictException("DEPLOYMENT_NOT_CURRENT", "현재 로봇에 적용된 배포가 아닙니다.");
    int total =
        countExecutableSteps(
            steps.findAllByTaskVersionIdOrderByStepOrder(request.taskVersionId()), null);
    RobotExecution saved =
        executions.save(
            new RobotExecution(
                UUID.randomUUID(),
                owner,
                robotId,
                request.taskVersionId(),
                request.deploymentId(),
                total));
    idempotency.complete(owner, "POST", path, claim, 202, "ROBOT_EXECUTION", saved.getId());
    return map(saved);
  }

  @Transactional(readOnly = true)
  public List<ExecutionResponse> list(UUID owner, UUID robot) {
    ownedRobot(owner, robot);
    return executions.findAllByRobotIdAndOwnerIdOrderByRequestedAtDesc(robot, owner).stream()
        .map(this::map)
        .toList();
  }

  @Transactional(readOnly = true)
  public ExecutionResponse get(UUID owner, UUID robot, UUID id) {
    return map(owned(owner, robot, id));
  }

  @Transactional
  public ExecutionResponse cancel(UUID owner, UUID robot, UUID id, String idempotencyKey) {
    String path = "/api/v1/robots/" + robot + "/executions/" + id + "/cancel";
    var claim = idempotency.claim(owner, "POST", path, idempotencyKey, Map.of("executionId", id));
    if (claim.existingResourceId() != null) return get(owner, robot, claim.existingResourceId());
    RobotExecution e = owned(owner, robot, id);
    try {
      e.requestCancel(clock.instant());
    } catch (IllegalStateException x) {
      throw new BusinessConflictException("STATE_CONFLICT", x.getMessage());
    }
    idempotency.complete(owner, "POST", path, claim, 202, "ROBOT_EXECUTION", e.getId());
    return map(e);
  }

  private void ownedRobot(UUID o, UUID r) {
    robots.findByIdAndOwnerId(r, o).orElseThrow(() -> new ResourceNotFoundException("Robot"));
  }

  private RobotExecution owned(UUID o, UUID r, UUID id) {
    ownedRobot(o, r);
    return executions
        .findByIdAndRobotIdAndOwnerId(id, r, o)
        .orElseThrow(() -> new ResourceNotFoundException("RobotExecution"));
  }

  private int countExecutableSteps(List<TaskStep> all, UUID parentId) {
    int total = 0;
    for (TaskStep step : all) {
      if (!Objects.equals(step.getParentStepId(), parentId)) continue;
      if (step.getStepType() == TaskStepType.REPEAT) {
        total += step.getRepeatCount() * countExecutableSteps(all, step.getId());
      } else total++;
    }
    return total;
  }

  private ExecutionResponse map(RobotExecution e) {
    return new ExecutionResponse(
        e.getId(),
        e.getOwnerId(),
        e.getRobotId(),
        e.getTaskVersionId(),
        e.getDeploymentId(),
        e.getStatus(),
        e.getCurrentStep(),
        e.getTotalSteps(),
        e.getProgressPercent(),
        e.getErrorCode() == null
            ? null
            : new ErrorInfo(e.getErrorCode(), e.getErrorMessage(), e.getRecoveryAction()),
        e.getRequestedAt(),
        e.getStartedAt(),
        e.getFinishedAt(),
        e.getUpdatedAt());
  }
}
