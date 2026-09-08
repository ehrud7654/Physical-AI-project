package com.anssa8bu.ssafy.purge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_purge_reports")
public class DataPurgeReport {
  @Id private UUID id;

  @Column(name = "data_purge_job_id", nullable = false)
  private UUID dataPurgeJobId;

  @Column(name = "report_id", nullable = false, length = 150)
  private String reportId;

  @Column(nullable = false)
  private int attempt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DataPurgeJobStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "archive_result", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> archiveResult;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "reported_at", nullable = false)
  private Instant reportedAt;

  protected DataPurgeReport() {}

  public Map<String, Object> getArchiveResult() {
    return archiveResult;
  }
}
