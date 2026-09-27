package com.anssa8bu.ssafy.support.fixture;

import com.anssa8bu.ssafy.deployment.entity.Deployment;
import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.entity.RobotConnectionStatus;
import com.anssa8bu.ssafy.task.entity.Task;
import com.anssa8bu.ssafy.task.entity.TaskVersion;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;

/** 사용자 → Jetson 장치 → 로봇 프로필 → 로봇 → 태스크/버전 → 배포. */
public final class DeploymentFixture {
  private final EntityManager em;
  private final UserDeviceFixture users;

  public DeploymentFixture(EntityManager em) {
    this.em = em;
    this.users = new UserDeviceFixture(em);
  }

  public record Graph(
      UUID userId, UUID jetsonId, UUID robotProfileId, UUID robotId, UUID taskVersionId) {}

  public Graph graph() {
    UUID user = users.user().getId();
    UUID jetson = users.jetson(user).getId();
    UUID profile = robotProfile().getId();
    UUID robot = robot(user, jetson, profile).getId();
    UUID task = task(user).getId();
    UUID version = taskVersion(task).getId();
    return new Graph(user, jetson, profile, robot, version);
  }

  public RobotProfile robotProfile() {
    UUID id = UUID.randomUUID();
    RobotProfile profile =
        new RobotProfile(
            id, "profile-" + id, "1.0", "arm", "ros2", "humble", Map.of(), Map.of(), Map.of());
    em.persist(profile);
    return profile;
  }

  public Robot robot(UUID ownerId, UUID jetsonId, UUID robotProfileId) {
    Robot robot =
        new Robot(
            UUID.randomUUID(),
            ownerId,
            jetsonId,
            robotProfileId,
            "fixture-robot",
            RobotConnectionStatus.OFFLINE);
    em.persist(robot);
    return robot;
  }

  public Task task(UUID ownerId) {
    UUID id = UUID.randomUUID();
    Task task = new Task(id, ownerId, "task_" + id.toString().replace("-", ""), "fixture", null);
    em.persist(task);
    return task;
  }

  public TaskVersion taskVersion(UUID taskId) {
    TaskVersion version = new TaskVersion(UUID.randomUUID(), taskId, 1);
    em.persist(version);
    return version;
  }

  /** 상태 PENDING으로 생성되어 uq_robot_active_deployment 대상이 된다. */
  public Deployment pendingDeployment(UUID robotId, UUID taskVersionId) {
    Deployment deployment = new Deployment(UUID.randomUUID(), robotId, taskVersionId);
    em.persist(deployment);
    return deployment;
  }
}
