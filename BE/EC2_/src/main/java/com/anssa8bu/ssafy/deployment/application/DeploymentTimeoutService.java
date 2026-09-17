package com.anssa8bu.ssafy.deployment.application;

import com.anssa8bu.ssafy.deployment.entity.DeploymentStatus;
import com.anssa8bu.ssafy.deployment.repository.DeploymentRepository;
import com.anssa8bu.ssafy.event.application.DomainEventPayload;
import com.anssa8bu.ssafy.event.application.OutboxEventFactory;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentTimeoutService {
  static final String ERROR_CODE = "DEPLOYMENT_TIMEOUT";
  static final String ERROR_MESSAGE = "배포에 실패했어요. 기기연결을 확인하세요.";

  private final DeploymentRepository deployments;
  private final RobotRepository robots;
  private final OutboxEventRepository outbox;
  private final OutboxEventFactory eventFactory;
  private final Clock clock;
  private final Duration pendingTimeout;

  public DeploymentTimeoutService(
      DeploymentRepository deployments,
      RobotRepository robots,
      OutboxEventRepository outbox,
      OutboxEventFactory eventFactory,
      Clock clock,
      @Value("${app.deployment.pending-timeout:30s}") Duration pendingTimeout) {
    this.deployments = deployments;
    this.robots = robots;
    this.outbox = outbox;
    this.eventFactory = eventFactory;
    this.clock = clock;
    this.pendingTimeout = pendingTimeout;
  }

  @Transactional
  public int failExpiredPendingDeployments() {
    Instant now = clock.instant();
    var expired =
        deployments.findAllByStatusAndCreatedAtBefore(
            DeploymentStatus.PENDING, now.minus(pendingTimeout));

    expired.forEach(
        deployment -> {
          deployment.failPendingTimeout(ERROR_CODE, ERROR_MESSAGE);
          UUID ownerId =
              robots
                  .findById(deployment.getRobotId())
                  .orElseThrow(() -> new IllegalStateException("deployment robot not found"))
                  .getOwnerId();
          outbox.save(
              eventFactory.create(
                  new DomainEventPayload(
                      1,
                      UUID.randomUUID().toString(),
                      "DEPLOYMENT_FAILED",
                      "DEPLOYMENT",
                      deployment.getId().toString(),
                      ownerId,
                      now,
                      Map.of(
                          "robotId", deployment.getRobotId().toString(),
                          "taskVersionId", deployment.getTaskVersionId().toString(),
                          "code", ERROR_CODE,
                          "message", ERROR_MESSAGE)),
                  null));
        });
    return expired.size();
  }
}
