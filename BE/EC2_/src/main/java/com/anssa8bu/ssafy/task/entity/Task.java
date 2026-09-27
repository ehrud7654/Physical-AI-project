package com.anssa8bu.ssafy.task.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "tasks")
public class Task extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "task_key", nullable = false, length = 100)
  private String taskKey;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "latest_version")
  private Integer latestVersion;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected Task() {}

  public Task(UUID id, UUID owner, String key, String name, String desc) {
    this.id = id;
    ownerId = owner;
    taskKey = key;
    displayName = name;
    description = desc;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getTaskKey() {
    return taskKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public Integer getLatestVersion() {
    return latestVersion;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete(Instant deletedAt) {
    if (this.deletedAt == null) this.deletedAt = Objects.requireNonNull(deletedAt);
  }

  public void update(String name, String description) {
    if (name != null) {
      if (name.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
      displayName = name.trim();
    }
    this.description = description;
  }

  public int publishNextVersion() {
    latestVersion = latestVersion == null ? 1 : latestVersion + 1;
    return latestVersion;
  }
}
