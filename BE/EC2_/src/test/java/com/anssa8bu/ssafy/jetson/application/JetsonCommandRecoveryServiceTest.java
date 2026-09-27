package com.anssa8bu.ssafy.jetson.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.anssa8bu.ssafy.deployment.entity.DeploymentStatus;
import com.anssa8bu.ssafy.deployment.repository.DeploymentRepository;
import com.anssa8bu.ssafy.execution.entity.RobotExecutionStatus;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JetsonCommandRecoveryServiceTest {
  @Test
  void recoveryQueriesOnlyNonTerminalCommands() {
    DeploymentRepository deployments = mock(DeploymentRepository.class);
    RobotExecutionRepository executions = mock(RobotExecutionRepository.class);
    when(deployments.findFirstByRobotIdAndStatusInOrderByCreatedAtAsc(any(), any()))
        .thenReturn(Optional.empty());
    when(executions.findFirstByRobotIdAndStatusInOrderByRequestedAtAsc(any(), any()))
        .thenReturn(Optional.empty());
    var service =
        new JetsonCommandRecoveryService(
            deployments,
            executions,
            mock(JetsonCommandFactory.class),
            mock(JetsonCommandStreamService.class),
            new SimpleMeterRegistry());

    service.replay(UUID.randomUUID());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<DeploymentStatus>> deploymentStatuses = ArgumentCaptor.forClass(Set.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<RobotExecutionStatus>> executionStatuses =
        ArgumentCaptor.forClass(Set.class);
    verify(deployments)
        .findFirstByRobotIdAndStatusInOrderByCreatedAtAsc(any(), deploymentStatuses.capture());
    verify(executions)
        .findFirstByRobotIdAndStatusInOrderByRequestedAtAsc(any(), executionStatuses.capture());
    assertThat(deploymentStatuses.getValue())
        .containsExactlyInAnyOrder(DeploymentStatus.PENDING, DeploymentStatus.DOWNLOADING);
    assertThat(executionStatuses.getValue())
        .containsExactlyInAnyOrder(
            RobotExecutionStatus.REQUESTED, RobotExecutionStatus.STARTING,
            RobotExecutionStatus.RUNNING, RobotExecutionStatus.CANCEL_REQUESTED);
    assertThat(executionStatuses.getValue())
        .doesNotContain(
            RobotExecutionStatus.SUCCEEDED,
            RobotExecutionStatus.FAILED,
            RobotExecutionStatus.CANCELED);
  }
}
