package com.anssa8bu.ssafy.task.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
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

  public Integer getLatestVersion() {
    return latestVersion;
  }
}
