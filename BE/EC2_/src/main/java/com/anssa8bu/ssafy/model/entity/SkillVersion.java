package com.anssa8bu.ssafy.model.entity;

import com.anssa8bu.ssafy.common.entity.CreatedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "skill_versions")
public class SkillVersion extends CreatedEntity {
  @Id private UUID id;

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @Column(name = "training_job_id", nullable = false)
  private UUID trainingJobId;

  @Column(name = "training_profile_id", nullable = false)
  private UUID trainingProfileId;

  @Column(name = "training_config_sha256", nullable = false, length = 64)
  private String trainingConfigSha256;

  @Column(name = "robot_profile_id", nullable = false)
  private UUID robotProfileId;

  @Column(nullable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SkillVersionStatus status;

  @Column(name = "checkpoint_bucket", nullable = false, length = 255)
  private String checkpointBucket;

  @Column(name = "checkpoint_key", nullable = false, length = 700)
  private String checkpointKey;

  @Column(name = "checkpoint_sha256", nullable = false, length = 64)
  private String checkpointSha256;

  @Column(name = "checkpoint_bytes", nullable = false)
  private long checkpointBytes;

  @Column(nullable = false, length = 100)
  private String architecture;

  @Column(nullable = false, length = 100)
  private String framework;

  @Column(name = "framework_version", nullable = false, length = 100)
  private String frameworkVersion;

  @Column(name = "contract_version", nullable = false, length = 100)
  private String contractVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_space", nullable = false, length = 30)
  private ActionSpace actionSpace;

  @Column(name = "n_params", nullable = false)
  private long parameterCount;

  @Column(name = "control_rate_hz", nullable = false, precision = 10, scale = 3)
  private BigDecimal controlRateHz;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "camera_names", nullable = false, columnDefinition = "jsonb")
  private List<String> cameraNames;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "input_schema", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> inputSchema;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "output_schema", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> outputSchema;

  @Column(name = "gate_metric", length = 100)
  private String gateMetric;

  @Column(name = "gate_n")
  private Integer gateN;

  @Column(name = "gate_runs")
  private Integer gateRuns;

  @Column(name = "gate_value")
  private Double gateValue;

  @Column(name = "gate_ci_low")
  private Double gateCiLow;

  @Column(name = "gate_ci_high")
  private Double gateCiHigh;

  @Column(name = "gate_measured_at")
  private Instant gateMeasuredAt;

  @Generated
  @Column(name = "gate_passed", insertable = false, updatable = false)
  private boolean gatePassed;

  @Column(name = "validated_at")
  private Instant validatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected SkillVersion() {}

  public boolean isGatePassed() {
    return gatePassed;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSkillId() { return skillId; }
  public UUID getRobotProfileId() { return robotProfileId; }
  public int getVersion() { return version; }
  public SkillVersionStatus getStatus() { return status; }
  public long getCheckpointBytes() { return checkpointBytes; }
  public String getCheckpointSha256() { return checkpointSha256; }
  public String getContractVersion() { return contractVersion; }
}
