package com.anssa8bu.ssafy.model.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "robot_profiles")
public class RobotProfile extends AuditableEntity {
  @Id private UUID id;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(name = "contract_version", nullable = false, length = 100)
  private String contractVersion;

  @Column(nullable = false, length = 100)
  private String architecture;

  @Column(nullable = false, length = 100)
  private String runtime;

  @Column(name = "runtime_version", nullable = false, length = 100)
  private String runtimeVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "camera_contract", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> cameraContract;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "state_contract", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> stateContract;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_contract", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> actionContract;

  protected RobotProfile() {}

  public RobotProfile(
      UUID id,
      String n,
      String cv,
      String a,
      String r,
      String rv,
      Map<String, Object> c,
      Map<String, Object> s,
      Map<String, Object> ac) {
    this.id = Objects.requireNonNull(id);
    name = n;
    contractVersion = cv;
    architecture = a;
    runtime = r;
    runtimeVersion = rv;
    cameraContract = Map.copyOf(c);
    stateContract = Map.copyOf(s);
    actionContract = Map.copyOf(ac);
  }

  public UUID getId() {
    return id;
  }

  public Map<String, Object> getCameraContract() {
    return cameraContract;
  }
}
