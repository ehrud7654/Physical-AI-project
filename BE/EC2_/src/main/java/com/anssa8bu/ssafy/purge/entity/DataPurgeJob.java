package com.anssa8bu.ssafy.purge.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "data_purge_jobs")
public class DataPurgeJob extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "episode_id", nullable = false)
  private UUID episodeId;

  @Column(name = "storage_node_id", nullable = false)
  private UUID storageNodeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DataPurgeJobStatus status;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "assigned_worker_id", length = 100)
  private String assignedWorkerId;

  @Column(name = "lease_token_hash", length = 64)
  private String leaseTokenHash;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "scheduled_at", nullable = false)
  private Instant scheduledAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected DataPurgeJob() {}

  public DataPurgeJob(UUID id, UUID episodeId, UUID nodeId, Instant scheduledAt) {
    this.id = id;
    this.episodeId = episodeId;
    storageNodeId = nodeId;
    status = DataPurgeJobStatus.PENDING;
    this.scheduledAt = scheduledAt;
  }

  public UUID getId() {
    return id;
  }

  public DataPurgeJobStatus getStatus() {
    return status;
  }

  public Long getRowVersion() {
    return rowVersion;
  }
}
