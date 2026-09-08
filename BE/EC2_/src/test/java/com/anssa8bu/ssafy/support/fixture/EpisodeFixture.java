package com.anssa8bu.ssafy.support.fixture;

import com.anssa8bu.ssafy.episode.entity.Episode;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchive;
import com.anssa8bu.ssafy.episode.entity.EpisodeArchiveStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeLabelStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeQualityStatus;
import com.anssa8bu.ssafy.episode.entity.EpisodeSource;
import com.anssa8bu.ssafy.episode.entity.EpisodeStatus;
import com.anssa8bu.ssafy.episode.vo.ArchiveManifestSummary;
import com.anssa8bu.ssafy.episode.vo.CaptureMetadata;
import com.anssa8bu.ssafy.episode.vo.EpisodeIdentity;
import com.anssa8bu.ssafy.episode.vo.EpisodeSampleSummary;
import com.anssa8bu.ssafy.episode.vo.EpisodeTiming;
import com.anssa8bu.ssafy.episode.vo.QualitySummary;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.entity.SkillStatus;
import com.anssa8bu.ssafy.skill.entity.SkillTier;
import com.anssa8bu.ssafy.storage.entity.RetentionPolicy;
import com.anssa8bu.ssafy.storage.entity.StorageNode;
import com.anssa8bu.ssafy.storage.entity.StorageNodeStatus;
import com.anssa8bu.ssafy.storage.entity.StorageType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 에피소드 그래프(사용자·장치·저장소·정책·스킬·에피소드)를 의존 순서대로 생성한다. */
public final class EpisodeFixture {
  private final EntityManager em;
  private final UserDeviceFixture users;

  public EpisodeFixture(EntityManager em) {
    this.em = em;
    this.users = new UserDeviceFixture(em);
  }

  public record Graph(
      UUID userId, UUID deviceId, UUID nodeId, UUID policyId, UUID skillId, UUID episodeId) {}

  public Graph graph() {
    UUID user = users.user().getId();
    UUID device = users.umiCamera(user).getId();
    UUID node = storageNode().getId();
    UUID policy = retentionPolicy().getId();
    UUID skill = skill().getId();
    UUID episode = episode(user, device, node, policy, skill).getId();
    return new Graph(user, device, node, policy, skill, episode);
  }

  public StorageNode storageNode() {
    UUID id = UUID.randomUUID();
    StorageNode node =
        new StorageNode(
            id,
            "worker-" + id,
            "node-" + id,
            "fixture",
            StorageType.LOCAL,
            StorageNodeStatus.ONLINE,
            1_000_000,
            900_000);
    em.persist(node);
    return node;
  }

  /** seed에 이미 기본 정책이 있으므로 is_default=false로 만든다. */
  public RetentionPolicy retentionPolicy() {
    UUID id = UUID.randomUUID();
    RetentionPolicy policy =
        new RetentionPolicy(id, "fixture-" + id, 7, 1, 30, 3, true, true, false, true);
    em.persist(policy);
    return policy;
  }

  public Skill skill() {
    UUID id = UUID.randomUUID();
    Skill skill =
        new Skill(
            id,
            "fixture_" + id.toString().replace("-", ""),
            SkillTier.TUTORIAL,
            SkillStatus.PLANNED,
            "fixture",
            null,
            false,
            null);
    em.persist(skill);
    return skill;
  }

  public Episode episode(UUID userId, UUID deviceId, UUID nodeId, UUID policyId, UUID skillId) {
    Instant started = Instant.parse("2026-09-08T01:00:00Z");
    Instant ended = started.plusSeconds(5);
    Episode episode =
        new Episode(
            UUID.randomUUID(),
            new EpisodeIdentity(
                userId, deviceId, nodeId, policyId, skillId, "session-fixture", EpisodeSource.REAL),
            null,
            EpisodeLabelStatus.UNLABELED,
            null,
            null,
            "1.0.0",
            "1.0",
            "1.0",
            EpisodeStatus.READY,
            new EpisodeTiming(started, ended, 100, 110, 510),
            new EpisodeSampleSummary(4, 4, 20, 4, 0, 0, 0),
            EpisodeQualityStatus.VALID,
            new CaptureMetadata(Map.of(), Map.of(), Map.of(), Map.of()),
            new QualitySummary(Map.of()),
            ended.plusSeconds(86_400));
    em.persist(episode);
    return episode;
  }

  public EpisodeArchive archive(UUID episodeId, UUID nodeId) {
    UUID id = UUID.randomUUID();
    EpisodeArchive archive =
        new EpisodeArchive(
            id,
            episodeId,
            nodeId,
            "episodes/fixture/" + id + "/archive.zip",
            "fixture.zip",
            "1.0",
            100,
            200,
            7,
            "b".repeat(64),
            new ArchiveManifestSummary(Map.of("meta.json", "a".repeat(64)), 1, Map.of()),
            EpisodeArchiveStatus.AVAILABLE);
    em.persist(archive);
    return archive;
  }
}
