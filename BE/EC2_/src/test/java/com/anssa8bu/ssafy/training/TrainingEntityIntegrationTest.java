package com.anssa8bu.ssafy.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.training.entity.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

class TrainingEntityIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;

  @Test
  @Transactional
  @Rollback
  void profileJsonUuidEnumSurviveFlushClearAndRollback() {
    UUID id = UUID.randomUUID();
    Map<String, Object> params = Map.of("epochs", 100, "batchSize", 8);
    em.persist(
        new TrainingProfile(
            id,
            "fixed-profile-" + id,
            1,
            TrainingProfileStatus.RETIRED,
            "diffusion",
            params,
            "c".repeat(64),
            null));
    em.flush();
    em.clear();
    TrainingProfile loaded = em.find(TrainingProfile.class, id);
    assertThat(loaded.getId()).isEqualTo(id);
    assertThat(loaded.getStatus()).isEqualTo(TrainingProfileStatus.RETIRED);
    assertThat(loaded.getHyperparameters()).isEqualTo(params);
  }
}
