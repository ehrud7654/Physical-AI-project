package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.deployment.entity.DeploymentStatus;
import com.anssa8bu.ssafy.deployment.repository.DeploymentRepository;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JetsonCommandRecoveryService {
  private final DeploymentRepository deployments;
  private final RobotExecutionRepository executions;
  private final JetsonCommandFactory commands;
  private final JetsonCommandStreamService streams;
  private final io.micrometer.core.instrument.MeterRegistry meter;

  public JetsonCommandRecoveryService(
      DeploymentRepository deployments,
      RobotExecutionRepository executions,
      JetsonCommandFactory commands,
      JetsonCommandStreamService streams,
      io.micrometer.core.instrument.MeterRegistry meter) {
    this.deployments = deployments;
    this.executions = executions;
    this.commands = commands;
    this.streams = streams;
    this.meter = meter;
  }

  @Transactional(readOnly = true)
  public void replay(UUID robotId) {
    deployments
        .findFirstByRobotIdAndStatusInOrderByCreatedAtAsc(
            robotId, EnumSet.of(DeploymentStatus.PENDING, DeploymentStatus.DOWNLOADING))
        .ifPresent(
            value -> {
              streams.publish(
                  commands.deploymentApply(robotId, value.getId(), value.getTaskVersionId()));
              meter.counter("jetson.command.replay", "type", "deployment").increment();
            });

    executions
        .findFirstByRobotIdAndStatusInOrderByRequestedAtAsc(
            robotId,
            EnumSet.of(
                RobotExecutionStatus.REQUESTED, RobotExecutionStatus.STARTING,
                RobotExecutionStatus.RUNNING, RobotExecutionStatus.CANCEL_REQUESTED))
        .ifPresent(
            value -> {
              streams.publish(commands.recoverExecution(value));
              meter.counter("jetson.command.replay", "type", "execution").increment();
            });
  }
}
