package com.anssa8bu.ssafy.episode;

import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.episode.application.EpisodeQueryService;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import com.anssa8bu.ssafy.support.fixture.EpisodeFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class EpisodeQueryServiceIntegrationTest extends IntegrationTestBase {
  @Autowired EntityManager em;
  @Autowired EpisodeQueryService service;

  @Test
  void listGetAndRetentionPolicyReflectEntitiesAndTrainingRules() {
    EpisodeFixture fixture = new EpisodeFixture(em);
    EpisodeFixture.Graph g = fixture.graph();
    fixture.archive(g.episodeId(), g.nodeId());
    em.flush();

    EpisodeResponse detail = service.get(g.userId(), g.episodeId());
    assertThat(detail.umiDeviceHandleNumber()).startsWith("UMI_CAMERA-");
    assertThat(detail.skillKey()).startsWith("fixture_");
    assertThat(detail.archive().sizeBytes()).isEqualTo(100);
    // fixture는 READY·VALID·archive AVAILABLE이지만 UNLABELED → 학습 불가, 라벨 지정 안내
    assertThat(detail.trainable()).isFalse();
    assertThat(detail.blockingReasons())
        .extracting(BlockingReason::code)
        .containsExactly("EPISODE_NOT_LABELED");
    assertThat(detail.recoveryAction()).isEqualTo(RecoveryAction.SET_LABEL);

    ListQuery all =
        new ListQuery(null, null, null, null, null, null, null, null, null, null, null, null);
    assertThat(service.list(g.userId(), all))
        .extracting(EpisodeResponse::id)
        .containsExactly(g.episodeId());
    ListQuery bySkill =
        new ListQuery(
            null, null, detail.skillKey(), null, null, null, null, null, null, null, null, null);
    assertThat(service.list(g.userId(), bySkill)).hasSize(1);
    ListQuery trainableOnly =
        new ListQuery(null, null, null, null, null, null, null, null, null, true, null, null);
    assertThat(service.list(g.userId(), trainableOnly)).isEmpty();
    assertThat(service.list(UUID.randomUUID(), all)).isEqualTo(List.of());

    assertThatThrownBy(() -> service.get(UUID.randomUUID(), g.episodeId()))
        .isInstanceOf(ResourceNotFoundException.class);

    RetentionPolicyResponse policy = service.retentionPolicy();
    assertThat(policy.name()).isEqualTo("dev-provisional-retention");
    assertThat(policy.defaultRetentionDays()).isEqualTo(7);
  }
}
