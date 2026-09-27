package com.anssa8bu.ssafy.device.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "device_credentials")
public class DeviceCredential extends AuditableEntity {

  @Id
  @Column(name = "device_id", nullable = false, updatable = false)
  private UUID deviceId;

  @Column(name = "device_secret_hash", nullable = false, length = 255)
  private String deviceSecretHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeviceCredentialStatus status;

  @Column(name = "enrolled_at")
  private Instant enrolledAt;

  @Column(name = "last_authenticated_at")
  private Instant lastAuthenticatedAt;

  @Column(name = "secret_rotated_at")
  private Instant secretRotatedAt;

  protected DeviceCredential() {}

  public DeviceCredential(
      UUID deviceId,
      String deviceSecretHash, Instant enrolledAt) {
    this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
    this.deviceSecretHash = requireText(deviceSecretHash, "deviceSecretHash");
    this.status = DeviceCredentialStatus.ACTIVE;
    this.enrolledAt = enrolledAt;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  public void rotateSecret(String newSecretHash, Instant rotatedAt) {
    this.deviceSecretHash = requireText(newSecretHash, "newSecretHash");
    this.secretRotatedAt = Objects.requireNonNull(rotatedAt, "rotatedAt must not be null");
  }

  public void enroll(String newSecretHash, Instant enrolledAt) {
    this.deviceSecretHash = requireText(newSecretHash, "newSecretHash");
    this.status = DeviceCredentialStatus.ACTIVE;
    this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt must not be null");
    this.secretRotatedAt = enrolledAt;
  }

  public void recordAuthentication(Instant authenticatedAt) {
    this.lastAuthenticatedAt =
        Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");
  }

  public void revoke() {
    this.status = DeviceCredentialStatus.REVOKED;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public String getDeviceSecretHash() {
    return deviceSecretHash;
  }

  public DeviceCredentialStatus getStatus() {
    return status;
  }

  public Instant getEnrolledAt() {
    return enrolledAt;
  }

  public Instant getLastAuthenticatedAt() {
    return lastAuthenticatedAt;
  }

  public Instant getSecretRotatedAt() {
    return secretRotatedAt;
  }
}
