package com.anssa8bu.ssafy.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy.deployment.entity.Deployment;
import com.anssa8bu.ssafy.device.entity.*;
import com.anssa8bu.ssafy.execution.entity.*;
import com.anssa8bu.ssafy.execution.repository.RobotExecutionRepository;
import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.robot.entity.*;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.task.entity.*;
import com.anssa8bu.ssafy.user.entity.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

class RobotExecutionEntityIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager entityManager;
  @Autowired RobotExecutionRepository repository;

  @Test
  @Transactional
  @Rollback
  void robotExecutionSurvivesFlushClearWithUuidEnumInstantAndReportHash() {
    UUID ownerId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID taskVersionId = UUID.randomUUID();
    UUID robotId = UUID.randomUUID();
    UUID deploymentId = UUID.randomUUID();
    UUID executionId = UUID.randomUUID();

    entityManager.persist(
        new User(
            ownerId, "execution-" + ownerId + "@test.local", "hash", "owner", UserStatus.ACTIVE));
    entityManager.persist(
        new Device(
            deviceId,
            ownerId,
            DeviceType.JETSON,
            "jetson-" + deviceId,
            "jetson",
            DeviceConnectionStatus.OFFLINE));
    entityManager.persist(
        new RobotProfile(
            profileId,
            "execution-profile-" + profileId,
            "1.0",
            "arm",
            "ros2",
            "humble",
            Map.of(),
            Map.of(),
            Map.of()));
    entityManager.persist(
        new Task(taskId, ownerId, "execution_" + taskId.toString().replace("-", ""), "task", null));
    entityManager.persist(new TaskVersion(taskVersionId, taskId, 1));
    entityManager.persist(
        new Robot(robotId, ownerId, deviceId, profileId, "robot", RobotConnectionStatus.OFFLINE));
    entityManager.persist(new Deployment(deploymentId, robotId, taskVersionId));

    RobotExecution execution =
        new RobotExecution(executionId, ownerId, robotId, taskVersionId, deploymentId, 3);
    repository.save(execution);
    entityManager.flush();

    Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    execution.acceptReport(
        "report-1",
        1,
        "a".repeat(64),
        RobotExecutionStatus.STARTING,
        1,
        new BigDecimal("10.00"),
        startedAt);
    entityManager.flush();
    entityManager.clear();

    RobotExecution loaded = repository.findById(executionId).orElseThrow();
    assertThat(loaded.getId()).isEqualTo(executionId);
    assertThat(loaded.getRobotId()).isEqualTo(robotId);
    assertThat(loaded.getStatus()).isEqualTo(RobotExecutionStatus.STARTING);
    assertThat(loaded.getStartedAt()).isEqualTo(startedAt);
    assertThat(loaded.getLastReportId()).isEqualTo("report-1");
    assertThat(loaded.getLastReportSequence()).isOne();
    assertThat(loaded.getLastReportHash()).isEqualTo("a".repeat(64));
    assertThat(loaded.getRequestedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getRowVersion()).isOne();
  }
}
