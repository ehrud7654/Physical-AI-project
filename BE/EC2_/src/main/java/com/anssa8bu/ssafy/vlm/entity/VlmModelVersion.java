package com.anssa8bu.ssafy.vlm.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vlm_model_versions")
public class VlmModelVersion extends AuditableEntity {
  @Id private UUID id;

  @Column(nullable = false, length = 100)
  private String version;

  @Column(name = "model_name", nullable = false, length = 150)
  private String modelName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VlmModelStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model_metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> modelMetadata;

  protected VlmModelVersion() {}

  public VlmModelVersion(UUID id, String v, String n, VlmModelStatus s, Map<String, Object> m) {
    this.id = id;
    version = v;
    modelName = n;
    status = s;
    modelMetadata = Map.copyOf(m);
  }

  public UUID getId() {
    return id;
  }

  public Map<String, Object> getModelMetadata() {
    return modelMetadata;
  }
}
