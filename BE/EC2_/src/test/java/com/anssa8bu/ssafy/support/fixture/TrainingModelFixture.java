package com.anssa8bu.ssafy.support.fixture;

import com.anssa8bu.ssafy.training.entity.TrainingInputMode;
import com.anssa8bu.ssafy.training.entity.TrainingJob;
import com.anssa8bu.ssafy.training.entity.TrainingProfile;
import com.anssa8bu.ssafy.training.entity.TrainingProfileStatus;
import jakarta.persistence.EntityManager;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** 학습 프로필·작업. seed에 ACTIVE 프로필이 하나 있으므로 기본은 RETIRED로 만든다. */
public final class TrainingModelFixture {
  private final EntityManager em;

  public TrainingModelFixture(EntityManager em) {
    this.em = em;
  }

  public TrainingProfile retiredProfile() {
    return profile(TrainingProfileStatus.RETIRED);
  }

  public TrainingProfile profile(TrainingProfileStatus status) {
    UUID id = UUID.randomUUID();
    TrainingProfile profile =
        new TrainingProfile(
            id,
            "fixture-" + id,
            1,
            status,
            "ACT",
            Map.of("epochs", 1, "batchSize", 1),
            sha256Like(id),
            null);
    em.persist(profile);
    return profile;
  }

  /** fixture 프로필의 config_sha256은 항상 {@code sha256Like(id)}이다 (엔티티에 getter가 없음). */
  public static String configSha256Of(TrainingProfile profile) {
    return sha256Like(profile.getId());
  }

  /** 프로필의 config_sha256과 같은 해시를 사용해 복합 FK(training_profile_id, training_config_sha256)를 만족시킨다. */
  public TrainingJob job(UUID ownerId, UUID skillId, UUID deviceId, TrainingProfile profile) {
    return job(ownerId, skillId, deviceId, profile.getId(), configSha256Of(profile));
  }

  public TrainingJob job(
      UUID ownerId, UUID skillId, UUID deviceId, UUID profileId, String configSha256) {
    TrainingJob job =
        new TrainingJob(
            UUID.randomUUID(),
            ownerId,
            skillId,
            deviceId,
            profileId,
            TrainingInputMode.EXISTING_EPISODES,
            Map.of("epochs", 1),
            configSha256,
            Map.of("skillId", skillId.toString()),
            0);
    em.persist(job);
    return job;
  }

  /** 소문자 64자리 hex — 실제 SHA-256 값은 아니지만 CHECK 정규식과 엔티티 검증을 통과한다. */
  public static String sha256Like(UUID seed) {
    String hex = HexFormat.of().formatHex(uuidBytes(seed));
    return (hex + hex).substring(0, 64);
  }

  private static byte[] uuidBytes(UUID u) {
    return java.nio.ByteBuffer.allocate(16)
        .putLong(u.getMostSignificantBits())
        .putLong(u.getLeastSignificantBits())
        .array();
  }
}
