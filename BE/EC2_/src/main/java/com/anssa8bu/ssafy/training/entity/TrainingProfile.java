package com.anssa8bu.ssafy.training.entity;

import com.anssa8bu.ssafy.common.entity.CreatedEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "training_profiles")
public class TrainingProfile extends CreatedEntity {
  @Id private UUID id;

  @Column(name = "profile_key", nullable = false, length = 100)
  private String profileKey;

  @Column(nullable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TrainingProfileStatus status;

  @Column(nullable = false, length = 100)
  private String algorithm;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> hyperparameters;

  @Column(name = "config_sha256", nullable = false, length = 64)
  private String configSha256;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "retired_at")
  private Instant retiredAt;

  protected TrainingProfile() {}

  public TrainingProfile(
      UUID id,
      String key,
      int version,
      TrainingProfileStatus status,
      String algorithm,
      Map<String, Object> params,
      String hash,
      String description) {
    if (version < 1 || hash == null || !hash.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException();
    this.id = Objects.requireNonNull(id);
    this.profileKey = text(key);
    this.version = version;
    this.status = Objects.requireNonNull(status);
    this.algorithm = text(algorithm);
    this.hyperparameters = Map.copyOf(params);
    this.configSha256 = hash;
    this.description = description;
  }

  private static String text(String v) {
    if (v == null || v.isBlank()) throw new IllegalArgumentException();
    return v;
  }

  public UUID getId() {
    return id;
  }

  public Map<String, Object> getHyperparameters() {
    return hyperparameters;
  }

  public TrainingProfileStatus getStatus() {
    return status;
  }

  public String getProfileKey() {
    return profileKey;
  }

  public int getVersion() {
    return version;
  }

  public String getConfigSha256() {
    return configSha256;
  }
}
