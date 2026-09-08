package com.anssa8bu.ssafy.storage.entity;

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
@Table(name = "storage_nodes")
public class StorageNode extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "worker_id", nullable = false, updatable = false, length = 100)
    private String workerId;

    @Column(name = "node_key", nullable = false, updatable = false, length = 100)
    private String nodeKey;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, updatable = false, length = 20)
    private StorageType storageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StorageNodeStatus status;

    @Column(name = "capacity_bytes", nullable = false)
    private long capacityBytes;

    @Column(name = "available_bytes", nullable = false)
    private long availableBytes;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    protected StorageNode() {
    }

    public StorageNode(
            UUID id,
            String workerId,
            String nodeKey,
            String name,
            StorageType storageType,
            StorageNodeStatus status,
            long capacityBytes,
            long availableBytes
    ) {
        validateCapacity(capacityBytes, availableBytes);
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.workerId = requireText(workerId, "workerId");
        this.nodeKey = requireText(nodeKey, "nodeKey");
        this.name = requireText(name, "name");
        this.storageType = Objects.requireNonNull(storageType, "storageType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.capacityBytes = capacityBytes;
        this.availableBytes = availableBytes;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void validateCapacity(long capacityBytes, long availableBytes) {
        if (capacityBytes < 0) {
            throw new IllegalArgumentException("capacityBytes must be zero or positive");
        }
        if (availableBytes < 0 || availableBytes > capacityBytes) {
            throw new IllegalArgumentException("availableBytes must be between zero and capacityBytes");
        }
    }

    public void rename(String name) {
        this.name = requireText(name, "name");
    }

    public void reportHeartbeat(
            StorageNodeStatus status,
            long capacityBytes,
            long availableBytes,
            Instant receivedAt
    ) {
        validateCapacity(capacityBytes, availableBytes);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.capacityBytes = capacityBytes;
        this.availableBytes = availableBytes;
        this.lastSeenAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public String getName() {
        return name;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public StorageNodeStatus getStatus() {
        return status;
    }

    public long getCapacityBytes() {
        return capacityBytes;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }
}
