package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.ExecutionResponse;
import com.anssa8bu.ssafy.jetson.dto.JetsonCommand;
import com.anssa8bu.ssafy.task.repository.TaskStepRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JetsonCommandFactory {
  private static final String CONTRACT_VERSION = "robot-command-v1";
  private final TaskStepRepository steps;
  private final Clock clock;

  public JetsonCommandFactory(TaskStepRepository steps, Clock clock) {
    this.steps = steps;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public JetsonCommand executionStart(ExecutionResponse execution) {
    List<JetsonCommand.Step> commandSteps =
        steps.findAllByTaskVersionIdOrderByStepOrder(execution.taskVersionId()).stream()
            .map(
                step ->
                    new JetsonCommand.Step(
                        step.getId(),
                        step.getParentStepId(),
                        step.getStepOrder(),
                        step.getStepType().name(),
                        step.getSkillVersionId(),
                        step.getTimeoutSeconds(),
                        step.getRepeatCount(),
                        step.getParameters()))
            .toList();
    return new JetsonCommand(
        "execution:" + execution.id() + ":start",
        0,
        "EXECUTE_TASK",
        execution.robotId(),
        execution.id(),
        execution.taskVersionId(),
        execution.deploymentId(),
        commandSteps,
        clock.instant(),
        CONTRACT_VERSION);
  }

  public JetsonCommand executionCancel(ExecutionResponse execution) {
    return new JetsonCommand(
        "execution:" + execution.id() + ":cancel",
        0,
        "CANCEL_EXECUTION",
        execution.robotId(),
        execution.id(),
        execution.taskVersionId(),
        execution.deploymentId(),
        List.of(),
        clock.instant(),
        CONTRACT_VERSION);
  }

  public JetsonCommand deploymentApply(
      java.util.UUID robotId, java.util.UUID deploymentId, java.util.UUID taskVersionId) {
    return new JetsonCommand(
        "deployment:" + deploymentId,
        0,
        "APPLY_DEPLOYMENT",
        robotId,
        null,
        taskVersionId,
        deploymentId,
        List.of(),
        clock.instant(),
        CONTRACT_VERSION);
  }
}
