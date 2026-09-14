package com.anssa8bu.ssafy.robot.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "robots")
public class Robot extends AuditableEntity {
  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "jetson_device_id", nullable = false)
  private UUID jetsonDeviceId;

  @Column(name = "robot_profile_id", nullable = false)
  private UUID robotProfileId;

  @Column(nullable = false, length = 150)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "connection_status", nullable = false, length = 20)
  private RobotConnectionStatus connectionStatus;

  @Column(name = "current_deployment_id")
  private UUID currentDeploymentId;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected Robot() {}

  public Robot(
      UUID id, UUID owner, UUID device, UUID profile, String name, RobotConnectionStatus status) {
    this.id = id;
    ownerId = owner;
    jetsonDeviceId = device;
    robotProfileId = profile;
    this.name = name;
    connectionStatus = status;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public UUID getJetsonDeviceId() {
    return jetsonDeviceId;
  }

  public UUID getRobotProfileId() {
    return robotProfileId;
  }

  public String getName() {
    return name;
  }

  public UUID getCurrentDeploymentId() {
    return currentDeploymentId;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public RobotConnectionStatus getConnectionStatus() {
    return connectionStatus;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public void reportHeartbeat(Instant receivedAt) {
    connectionStatus = RobotConnectionStatus.ONLINE;
    lastSeenAt = receivedAt;
  }

  public void activateDeployment(UUID deploymentId) {
    currentDeploymentId = deploymentId;
  }
}
