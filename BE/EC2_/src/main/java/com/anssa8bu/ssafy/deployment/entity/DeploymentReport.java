package com.anssa8bu.ssafy.deployment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "deployment_reports")
public class DeploymentReport {
  @Id private UUID id;

  @Column(name = "deployment_id", nullable = false)
  private UUID deploymentId;

  @Column(name = "report_id", nullable = false, length = 150)
  private String reportId;

  @Column(nullable = false)
  private long sequence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DeploymentStatus status;

  @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal progressPercent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> items;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> error;

  @Column(name = "reported_at", nullable = false)
  private Instant reportedAt;

  protected DeploymentReport() {}

  public DeploymentReport(
      UUID id,
      UUID deploymentId,
      String reportId,
      long sequence,
      DeploymentStatus status,
      BigDecimal progressPercent,
      java.util.List<java.util.Map<String, Object>> items,
      java.util.Map<String, Object> error,
      Instant reportedAt) {
    this.id = id;
    this.deploymentId = deploymentId;
    this.reportId = reportId;
    this.sequence = sequence;
    this.status = status;
    this.progressPercent = progressPercent;
    this.items = items == null ? java.util.List.of() : java.util.List.copyOf(items);
    this.error = error;
    this.reportedAt = reportedAt;
  }

  public long getSequence() {
    return sequence;
  }

  public String getReportId() {
    return reportId;
  }
}
