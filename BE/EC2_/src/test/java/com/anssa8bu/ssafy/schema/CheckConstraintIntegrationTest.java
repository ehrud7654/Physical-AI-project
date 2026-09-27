package com.anssa8bu.ssafy.schema;

import static com.anssa8bu.ssafy.support.assertion.PostgresConstraintAssertions.INVALID_TEXT;
import static com.anssa8bu.ssafy.support.assertion.PostgresConstraintAssertions.assertCheckViolation;
import static com.anssa8bu.ssafy.support.assertion.PostgresConstraintAssertions.assertSqlState;

import com.anssa8bu.ssafy.event.entity.OutboxEvent;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.support.fixture.DeploymentFixture;
import com.anssa8bu.ssafy.support.fixture.EpisodeFixture;
import com.anssa8bu.ssafy.support.fixture.TrainingModelFixture;
import com.anssa8bu.ssafy.support.fixture.UserDeviceFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * CHECK 제약. 엔티티 생성자가 잘못된 값을 먼저 거부하므로, 정상 fixture를 flush한 뒤 SQL UPDATE로 한 컬럼만 바꿔 DB가 거부하는지 확인한다.
 * JdbcTemplate은 테스트 트랜잭션의 커넥션을 공유한다.
 */
@Transactional
class CheckConstraintIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  UserDeviceFixture users;
  EpisodeFixture episodes;
  TrainingModelFixture training;
  DeploymentFixture deployments;

  @BeforeEach
  void fixtures() {
    users = new UserDeviceFixture(em);
    episodes = new EpisodeFixture(em);
    training = new TrainingModelFixture(em);
    deployments = new DeploymentFixture(em);
  }

  @Test
  void userStatusEnum() {
    UUID id = users.user().getId();
    em.flush();
    assertCheckViolation(
        "users_status_check",
        () -> jdbc.update("update users set status = 'BANNED' where id = ?", id));
  }

  @Test
  void deviceTypeEnumUsesUpperSnakeCaseAfterV3() {
    UUID owner = users.user().getId();
    UUID id = users.umiCamera(owner).getId();
    em.flush();
    assertCheckViolation(
        "ck_devices_device_type",
        () -> jdbc.update("update devices set device_type = 'UMI_Camera' where id = ?", id));
  }

  @Test
  void inputSnapshotHashMustBeLowercaseHex64() {
    var g = episodes.graph();
    var job = training.job(g.userId(), g.skillId(), g.deviceId());
    job.fixInputSnapshotSha256("a".repeat(64));
    em.flush();
    assertCheckViolation(
        "training_jobs_input_snapshot_sha256_check",
        () ->
            jdbc.update(
                "update training_jobs set input_snapshot_sha256 = upper(input_snapshot_sha256) where id = ?",
                job.getId()));
  }

  @Test
  void datasetQueryMustBeValidJson() {
    var g = episodes.graph();
    var job = training.job(g.userId(), g.skillId(), g.deviceId());
    em.flush();
    assertSqlState(
        INVALID_TEXT,
        () ->
            jdbc.update(
                "update training_jobs set dataset_query = ?::jsonb where id = ?",
                "{",
                job.getId()));
  }

  @Test
  void retentionPolicyDeleteAttemptsAtLeastOne() {
    UUID id = episodes.retentionPolicy().getId();
    em.flush();
    assertCheckViolation(
        "retention_policies_max_delete_attempts_check",
        () ->
            jdbc.update("update retention_policies set max_delete_attempts = 0 where id = ?", id));
  }

  @Test
  void deploymentProgressWithinHundred() {
    var g = deployments.graph();
    UUID id = deployments.pendingDeployment(g.robotId(), g.taskVersionId()).getId();
    em.flush();
    assertCheckViolation(
        "deployments_progress_percent_check",
        () -> jdbc.update("update deployments set progress_percent = 100.01 where id = ?", id));
  }

  @Test
  void deploymentStatusEnum() {
    var g = deployments.graph();
    UUID id = deployments.pendingDeployment(g.robotId(), g.taskVersionId()).getId();
    em.flush();
    assertCheckViolation(
        "deployments_status_check",
        () -> jdbc.update("update deployments set status = 'DONE' where id = ?", id));
  }

  @Test
  void outboxStatusEnumAfterV5() {
    UUID id = outbox();
    assertCheckViolation(
        "ck_outbox_status",
        () -> jdbc.update("update outbox_events set status = 'QUEUED' where id = ?", id));
  }

  @Test
  void outboxProcessingRequiresLockColumns() {
    UUID id = outbox();
    assertCheckViolation(
        "ck_outbox_claim_consistency",
        () -> jdbc.update("update outbox_events set status = 'PROCESSING' where id = ?", id));
  }

  @Test
  void outboxNonProcessingMustNotHoldLock() {
    UUID id = outbox();
    assertCheckViolation(
        "ck_outbox_claim_consistency",
        () ->
            jdbc.update(
                "update outbox_events set locked_by = 'p1', locked_at = now(),"
                    + " lease_expires_at = now() where id = ?",
                id));
  }

  @Test
  void outboxAttemptNonNegative() {
    UUID id = outbox();
    assertCheckViolation(
        "ck_outbox_attempt",
        () -> jdbc.update("update outbox_events set attempt = -1 where id = ?", id));
  }

  @Test
  void outboxAggregateSequenceAtLeastOne() {
    UUID id = outbox();
    assertCheckViolation(
        "ck_outbox_sequence",
        () -> jdbc.update("update outbox_events set aggregate_sequence = 0 where id = ?", id));
  }

  private UUID outbox() {
    UUID id = UUID.randomUUID();
    em.persist(
        new OutboxEvent(
            id, "check-" + id, "TEST", id.toString(), "DONE", Map.of("k", "v"), Instant.now()));
    em.flush();
    return id;
  }
}
