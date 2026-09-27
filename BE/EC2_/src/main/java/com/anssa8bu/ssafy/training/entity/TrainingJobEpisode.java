package com.anssa8bu.ssafy.training.entity;

import com.anssa8bu.ssafy.episode.entity.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "training_job_episodes")
public class TrainingJobEpisode {
  @EmbeddedId private TrainingJobEpisodeId id;

  @Column(name = "input_order", nullable = false)
  private int inputOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_role", nullable = false, length = 20)
  private TrainingEpisodeRole inputRole;

  @Column(name = "archive_sha256_snapshot", nullable = false, length = 64)
  private String archiveSha256Snapshot;

  @Column(name = "contract_version_snapshot", nullable = false, length = 100)
  private String contractVersionSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_snapshot", nullable = false, length = 10)
  private EpisodeSource sourceSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "label_status_snapshot", nullable = false, length = 20)
  private EpisodeLabelStatus labelStatusSnapshot;

  @Column(name = "success_snapshot")
  private Boolean successSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "quality_status_snapshot", nullable = false, length = 20)
  private EpisodeQualityStatus qualityStatusSnapshot;

  @Column(name = "attached_at", nullable = false)
  private Instant attachedAt;

  protected TrainingJobEpisode() {}

  public TrainingJobEpisode(
      TrainingJobEpisodeId id,
      int order,
      TrainingEpisodeRole role,
      String hash,
      String contract,
      EpisodeSource source,
      EpisodeLabelStatus label,
      Boolean success,
      EpisodeQualityStatus quality,
      Instant at) {
    if (order < 1 || hash == null || !hash.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException();
    this.id = Objects.requireNonNull(id);
    inputOrder = order;
    inputRole = Objects.requireNonNull(role);
    archiveSha256Snapshot = hash;
    contractVersionSnapshot = contract;
    sourceSnapshot = source;
    labelStatusSnapshot = label;
    successSnapshot = success;
    qualityStatusSnapshot = quality;
    attachedAt = at;
  }

  public TrainingJobEpisodeId getId() {
    return id;
  }

  public int getInputOrder() {
    return inputOrder;
  }

  public TrainingEpisodeRole getInputRole() {
    return inputRole;
  }

}
