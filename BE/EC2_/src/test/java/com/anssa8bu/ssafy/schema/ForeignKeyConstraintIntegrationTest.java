package com.anssa8bu.ssafy.schema;

import static com.anssa8bu.ssafy.support.assertion.PostgresConstraintAssertions.assertForeignKeyViolation;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.support.fixture.EpisodeFixture;
import com.anssa8bu.ssafy.support.fixture.TrainingModelFixture;
import com.anssa8bu.ssafy.support.fixture.UserDeviceFixture;
import com.anssa8bu.ssafy.training.entity.TrainingProfile;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 단일 FK, 복합 FK(부분 일치 거부), ON DELETE RESTRICT. */
@Transactional
class ForeignKeyConstraintIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  UserDeviceFixture users;
  EpisodeFixture episodes;
  TrainingModelFixture training;

  @BeforeEach
  void fixtures() {
    users = new UserDeviceFixture(em);
    episodes = new EpisodeFixture(em);
    training = new TrainingModelFixture(em);
  }

  @Test
  void deviceOwnerMustExist() {
    users.umiCamera(UUID.randomUUID());
    assertForeignKeyViolation("fk_devices_3", em::flush);
  }

  @Test
  void deletingReferencedUserIsRestricted() {
    UUID owner = users.user().getId();
    users.umiCamera(owner);
    em.flush();
    assertForeignKeyViolation(
        "fk_devices_3", () -> jdbc.update("delete from users where id = ?", owner));
  }

  @Test
  void archiveStorageNodeMustMatchEpisodeStorageNode() {
    var g = episodes.graph();
    UUID otherNode = episodes.storageNode().getId();
    // episode_id, storage_node_id 각각은 존재하지만 (episode_id, storage_node_id) 조합이 episodes와 다르다
    episodes.archive(g.episodeId(), otherNode);
    assertForeignKeyViolation("fk_episode_archives_10", em::flush);
  }

  @Test
  void archiveWithMatchingStorageNodeIsAccepted() {
    var g = episodes.graph();
    episodes.archive(g.episodeId(), g.nodeId());
    assertThatCode(em::flush).doesNotThrowAnyException();
  }

  @Test
  void trainingJobConfigHashMustMatchProfile() {
    var g = episodes.graph();
    TrainingProfile profile = training.retiredProfile();
    // training_profile_id는 존재하지만 (id, config_sha256) 조합이 프로필과 다르다
    training.job(
        g.userId(),
        g.skillId(),
        g.deviceId(),
        profile.getId(),
        TrainingModelFixture.sha256Like(UUID.randomUUID()));
    assertForeignKeyViolation("fk_training_jobs_13", em::flush);
  }

  @Test
  void trainingJobWithMatchingProfileHashIsAccepted() {
    var g = episodes.graph();
    TrainingProfile profile = training.retiredProfile();
    training.job(g.userId(), g.skillId(), g.deviceId(), profile);
    assertThatCode(em::flush).doesNotThrowAnyException();
  }
}
