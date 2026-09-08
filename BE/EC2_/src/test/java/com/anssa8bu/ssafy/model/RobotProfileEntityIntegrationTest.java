package com.anssa8bu.ssafy.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

class RobotProfileEntityIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;

  @Test
  @Transactional
  @Rollback
  void jsonUuidSurviveFlushClearAndRollback() {
    UUID profileId = UUID.randomUUID();
    Map<String, Object> camera = Map.of("names", List.of("front"));
    em.persist(
        new RobotProfile(
            profileId,
            "robot-" + profileId,
            "1.0",
            "arm",
            "ros2",
            "humble",
            camera,
            Map.of("joints", 6),
            Map.of("space", "joint")));
    em.flush();
    em.clear();
    assertThat(em.find(RobotProfile.class, profileId).getCameraContract()).isEqualTo(camera);
  }
}
