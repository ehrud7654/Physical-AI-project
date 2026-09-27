package com.anssa8bu.ssafy.device.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "device_enrollments")
public class DeviceEnrollment extends AuditableEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "device_id", nullable = false, updatable = false)
  private UUID deviceId;

  @Column(name = "registration_token_hash", nullable = false, updatable = false, length = 64)
  private String registrationTokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeviceEnrollmentStatus status;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false, updatable = false)
  private int maxAttempts;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected DeviceEnrollment() {}

  public DeviceEnrollment(
      UUID id,
      UUID deviceId,
      String registrationTokenHash,
      Instant expiresAt,
      int maxAttempts,
      UUID createdBy) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
    this.registrationTokenHash = requireSha256(registrationTokenHash);
    this.status = DeviceEnrollmentStatus.ACTIVE;
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    this.maxAttempts = maxAttempts;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
  }

  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public void expire() {
    requireActive();
    status = DeviceEnrollmentStatus.EXPIRED;
  }

  public void revoke(Instant revokedAt) {
    requireActive();
    status = DeviceEnrollmentStatus.REVOKED;
    this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
  }

  public void markUsed(Instant usedAt) {
    requireActive();
    status = DeviceEnrollmentStatus.USED;
    this.usedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
  }

  public boolean recordFailedAttempt() {
    requireActive();
    attemptCount++;
    return attemptCount >= maxAttempts;
  }

  private void requireActive() {
    if (status != DeviceEnrollmentStatus.ACTIVE) {
      throw new IllegalStateException("device enrollment is not active");
    }
  }

  private static String requireSha256(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("registrationTokenHash must be lowercase SHA-256");
    }
    return value;
  }

  public UUID getId() { return id; }
  public UUID getDeviceId() { return deviceId; }
  public String getRegistrationTokenHash() { return registrationTokenHash; }
  public DeviceEnrollmentStatus getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUsedAt() { return usedAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public int getAttemptCount() { return attemptCount; }
  public int getMaxAttempts() { return maxAttempts; }
  public UUID getCreatedBy() { return createdBy; }
  public long getRowVersion() { return rowVersion; }
}
