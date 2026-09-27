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
  void jobJsonUuidEnumSurviveFlushClearAndRollback() {
    var graph = new com.anssa8bu.ssafy.support.fixture.EpisodeFixture(em).graph();
    var job =
        new com.anssa8bu.ssafy.support.fixture.TrainingModelFixture(em)
            .job(graph.userId(), graph.skillId(), graph.deviceId());
    em.flush();
    em.clear();
    TrainingJob loaded = em.find(TrainingJob.class, job.getId());
    assertThat(loaded.getId()).isEqualTo(job.getId());
    assertThat(loaded.getStatus()).isEqualTo(TrainingJobStatus.CREATED);
    assertThat(loaded.getDatasetQuery()).isEqualTo(Map.of("skillId", graph.skillId().toString()));
  }
}
