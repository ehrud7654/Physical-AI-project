package com.anssa8bu.ssafy.support.fixture;

import com.anssa8bu.ssafy.training.entity.TrainingInputMode;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;

/** EC2 orchestration jobs; training algorithm configuration belongs to the trainer. */
public final class TrainingModelFixture {
  private final EntityManager em;

  public TrainingModelFixture(EntityManager em) {
    this.em = em;
  }

  public TrainingJob job(UUID ownerId, UUID skillId, UUID deviceId) {
    TrainingJob job =
        new TrainingJob(
            UUID.randomUUID(),
            ownerId,
            skillId,
            deviceId,
            TrainingInputMode.EXISTING_EPISODES,
            Map.of("skillId", skillId.toString()),
            0);
    em.persist(job);
    return job;
  }
}
