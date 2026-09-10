package com.anssa8bu.ssafy.schema;

import static com.anssa8bu.ssafy.support.assertion.PostgresConstraintAssertions.assertUniqueViolation;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.anssa8bu.ssafy.event.entity.OutboxEvent;
import com.anssa8bu.ssafy.storage.entity.RetentionPolicy;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.support.fixture.DeploymentFixture;
import com.anssa8bu.ssafy.support.fixture.TrainingModelFixture;
import com.anssa8bu.ssafy.support.fixture.UserDeviceFixture;
import com.anssa8bu.ssafy.training.entity.TrainingProfile;
import com.anssa8bu.ssafy.training.entity.TrainingProfileStatus;
import com.anssa8bu.ssafy.user.entity.User;
import com.anssa8bu.ssafy.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * UNIQUE 제약과 부분 UNIQUE 인덱스. 테스트마다 위반 1건만 만들고 트랜잭션은 종료 시 롤백된다(실패한 PostgreSQL 트랜잭션은
 * 이후 문장을 거부하므로 한 테스트에 위반을 두 개 넣지 않는다).
 */
@Transactional
class UniqueConstraintIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;
  UserDeviceFixture users;
  TrainingModelFixture training;
  DeploymentFixture deployments;

  @BeforeEach
  void fixtures() {
    users = new UserDeviceFixture(em);
    training = new TrainingModelFixture(em);
    deployments = new DeploymentFixture(em);
  }

  @Test
  void userEmailIsUniqueIgnoringCase() {
    String email = "Case-" + UUID.randomUUID() + "@Test.local";
    em.persist(new User(UUID.randomUUID(), email, "h", "a", UserStatus.ACTIVE));
    em.persist(new User(UUID.randomUUID(), email.toLowerCase(), "h", "b", UserStatus.ACTIVE));
    assertUniqueViolation("uq_users_email_lower", em::flush);
  }

  @Test
  void onlyOneDefaultRetentionPolicy() {
    // seed(V2)에 is_default=true 정책이 이미 있다
    em.persist(
        new RetentionPolicy(
            UUID.randomUUID(), "second-default-" + UUID.randomUUID(), 7, 1, 30, 3, true, true, true,
            true));
    assertUniqueViolation("uq_retention_policy_default", em::flush);
  }

  @Test
  void onlyOneActiveTrainingProfile() {
    // seed(V2)에 ACTIVE 프로필이 이미 있다
    training.profile(TrainingProfileStatus.ACTIVE);
    assertUniqueViolation("uq_training_profiles_one_active", em::flush);
  }

  @Test
  void multipleRetiredTrainingProfilesAreAllowed() {
    training.retiredProfile();
    training.retiredProfile();
    assertThatCode(em::flush).doesNotThrowAnyException();
  }

  @Test
  void trainingProfileKeyAndVersionAreUnique() {
    String key = "dup-key-" + UUID.randomUUID();
    for (int i = 0; i < 2; i++) {
      UUID id = UUID.randomUUID();
      em.persist(
          new TrainingProfile(
              id,
              key,
              1,
              TrainingProfileStatus.RETIRED,
              "ACT",
              Map.of(),
              TrainingModelFixture.sha256Like(id),
              null));
    }
    assertUniqueViolation("training_profiles_profile_key_version_key", em::flush);
  }

  @Test
  void robotJetsonDeviceIsUnique() {
    var g = deployments.graph();
    deployments.robot(g.userId(), g.jetsonId(), g.robotProfileId());
    assertUniqueViolation("robots_jetson_device_id_key", em::flush);
  }

  @Test
  void robotHasAtMostOneActiveDeployment() {
    var g = deployments.graph();
    deployments.pendingDeployment(g.robotId(), g.taskVersionId());
    deployments.pendingDeployment(g.robotId(), g.taskVersionId());
    assertUniqueViolation("uq_robot_active_deployment", em::flush);
  }

  @Test
  void outboxEventIdIsUnique() {
    String eventId = "dup-" + UUID.randomUUID();
    em.persist(outbox(eventId, "agg", null));
    em.persist(outbox(eventId, "agg", null));
    assertUniqueViolation("outbox_events_event_id_key", em::flush);
  }

  @Test
  void outboxAggregateSequenceIsUniquePerAggregate() {
    String aggregate = "agg-" + UUID.randomUUID();
    em.persist(outbox("seq-a-" + UUID.randomUUID(), aggregate, 1L));
    em.persist(outbox("seq-b-" + UUID.randomUUID(), aggregate, 1L));
    assertUniqueViolation("uq_outbox_aggregate_sequence", em::flush);
  }

  private static OutboxEvent outbox(String eventId, String aggregateId, Long sequence) {
    return new OutboxEvent(
        UUID.randomUUID(), eventId, "TEST", aggregateId, "DONE", Map.of("k", "v"), Instant.now(),
        sequence);
  }
}
