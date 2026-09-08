package com.anssa8bu.ssafy.storage.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "retention_policies")
public class RetentionPolicy extends AuditableEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, updatable = false, length = 100)
  private String name;

  @Column(name = "default_retention_days", nullable = false, updatable = false)
  private int defaultRetentionDays;

  @Column(name = "post_training_grace_days", nullable = false, updatable = false)
  private int postTrainingGraceDays;

  @Column(name = "max_retention_days", nullable = false, updatable = false)
  private int maxRetentionDays;

  @Column(name = "max_delete_attempts", nullable = false, updatable = false)
  private int maxDeleteAttempts;

  @Column(name = "allow_manual_extension", nullable = false, updatable = false)
  private boolean allowManualExtension;

  @Column(name = "allow_manual_purge", nullable = false, updatable = false)
  private boolean allowManualPurge;

  @Column(name = "is_default", nullable = false, updatable = false)
  private boolean defaultPolicy;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected RetentionPolicy() {}

  public RetentionPolicy(
      UUID id,
      String name,
      int defaultRetentionDays,
      int postTrainingGraceDays,
      int maxRetentionDays,
      int maxDeleteAttempts,
      boolean allowManualExtension,
      boolean allowManualPurge,
      boolean defaultPolicy,
      boolean active) {
    validateDurations(
        defaultRetentionDays, postTrainingGraceDays, maxRetentionDays, maxDeleteAttempts);
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireText(name);
    this.defaultRetentionDays = defaultRetentionDays;
    this.postTrainingGraceDays = postTrainingGraceDays;
    this.maxRetentionDays = maxRetentionDays;
    this.maxDeleteAttempts = maxDeleteAttempts;
    this.allowManualExtension = allowManualExtension;
    this.allowManualPurge = allowManualPurge;
    this.defaultPolicy = defaultPolicy;
    this.active = active;
  }

  private static String requireText(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return name;
  }

  private static void validateDurations(
      int defaultRetentionDays,
      int postTrainingGraceDays,
      int maxRetentionDays,
      int maxDeleteAttempts) {
    if (defaultRetentionDays < 0) {
      throw new IllegalArgumentException("defaultRetentionDays must be zero or positive");
    }
    if (postTrainingGraceDays < 0) {
      throw new IllegalArgumentException("postTrainingGraceDays must be zero or positive");
    }
    if (maxRetentionDays < defaultRetentionDays) {
      throw new IllegalArgumentException(
          "maxRetentionDays must not be less than defaultRetentionDays");
    }
    if (maxDeleteAttempts < 1) {
      throw new IllegalArgumentException("maxDeleteAttempts must be at least one");
    }
  }

  public void deactivate() {
    this.active = false;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getDefaultRetentionDays() {
    return defaultRetentionDays;
  }

  public int getPostTrainingGraceDays() {
    return postTrainingGraceDays;
  }

  public int getMaxRetentionDays() {
    return maxRetentionDays;
  }

  public int getMaxDeleteAttempts() {
    return maxDeleteAttempts;
  }

  public boolean isAllowManualExtension() {
    return allowManualExtension;
  }

  public boolean isAllowManualPurge() {
    return allowManualPurge;
  }

  public boolean isDefaultPolicy() {
    return defaultPolicy;
  }

  public boolean isActive() {
    return active;
  }
}
