package com.anssa8bu.ssafy.device.entity;

import com.anssa8bu.ssafy.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "registration_code_hash", length = 255)
    private String registrationCodeHash;

    @Column(name = "registration_code_expires_at")
    private Instant registrationCodeExpiresAt;

    @Column(name = "secret_rotated_at")
    private Instant secretRotatedAt;

    protected DeviceCredential() {
    }

    public DeviceCredential(
            UUID deviceId,
            String deviceSecretHash,
            String registrationCodeHash,
            Instant registrationCodeExpiresAt
    ) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.deviceSecretHash = requireText(deviceSecretHash, "deviceSecretHash");
        this.registrationCodeHash = registrationCodeHash == null
                ? null
                : requireText(registrationCodeHash, "registrationCodeHash");
        this.registrationCodeExpiresAt = registrationCodeExpiresAt;

        if ((registrationCodeHash == null) != (registrationCodeExpiresAt == null)) {
            throw new IllegalArgumentException("registration code hash and expiration must be provided together");
        }
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

    public void clearRegistrationCode() {
        this.registrationCodeHash = null;
        this.registrationCodeExpiresAt = null;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getDeviceSecretHash() {
        return deviceSecretHash;
    }

    public String getRegistrationCodeHash() {
        return registrationCodeHash;
    }

    public Instant getRegistrationCodeExpiresAt() {
        return registrationCodeExpiresAt;
    }

    public Instant getSecretRotatedAt() {
        return secretRotatedAt;
    }
}
