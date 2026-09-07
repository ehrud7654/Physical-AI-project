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
@Table(name = "devices")
public class Device extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, updatable = false, length = 20)
    private DeviceType deviceType;

    @Column(name = "handle_number", nullable = false, updatable = false, length = 100)
    private String handleNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    private DeviceConnectionStatus connectionStatus;

    @Column(name = "firmware_version", length = 100)
    private String firmwareVersion;

    @Column(name = "runtime_version", length = 100)
    private String runtimeVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Device() {
    }

    public Device(
            UUID id,
            UUID ownerId,
            DeviceType deviceType,
            String handleNumber,
            String name,
            DeviceConnectionStatus connectionStatus
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType must not be null");
        this.handleNumber = requireText(handleNumber, "handleNumber");
        this.name = requireText(name, "name");
        this.connectionStatus = Objects.requireNonNull(connectionStatus, "connectionStatus must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public void rename(String name) {
        this.name = requireText(name, "name");
    }

    public void reportHeartbeat(
            DeviceConnectionStatus connectionStatus,
            String firmwareVersion,
            String runtimeVersion,
            Instant receivedAt
    ) {
        this.connectionStatus = Objects.requireNonNull(connectionStatus, "connectionStatus must not be null");
        this.firmwareVersion = firmwareVersion;
        this.runtimeVersion = runtimeVersion;
        this.lastSeenAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getHandleNumber() {
        return handleNumber;
    }

    public String getName() {
        return name;
    }

    public DeviceConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
