package com.anssa8bu.ssafy.event.entity;

import com.anssa8bu.ssafy.common.entity.CreatedEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends CreatedEntity {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false, length = 150, updatable = false)
  private String eventId;

  @Column(name = "aggregate_type", nullable = false, length = 100, updatable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, length = 150, updatable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 150, updatable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "locked_by", length = 100)
  private String lockedBy;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "aggregate_sequence", updatable = false)
  private Long aggregateSequence;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected OutboxEvent() {}

  public OutboxEvent(
      UUID id,
      String eventId,
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload,
      Instant availableAt) {
    this(id, eventId, aggregateType, aggregateId, eventType, payload, availableAt, null);
  }

  public OutboxEvent(
      UUID id,
      String eventId,
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload,
      Instant availableAt,
      Long sequence) {
    this.id = Objects.requireNonNull(id);
    this.eventId = text(eventId);
    this.aggregateType = text(aggregateType);
    this.aggregateId = text(aggregateId);
    this.eventType = text(eventType);
    this.payload = Map.copyOf(payload);
    this.availableAt = Objects.requireNonNull(availableAt);
    if (sequence != null && sequence < 1) throw new IllegalArgumentException();
    aggregateSequence = sequence;
    status = OutboxStatus.PENDING;
  }

  public void claim(String publisherId, Instant now, Duration lease) {
    if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED)
      throw new IllegalStateException("not claimable");
    lockedBy = text(publisherId);
    lockedAt = now;
    leaseExpiresAt = now.plus(lease);
    status = OutboxStatus.PROCESSING;
    attempt++;
  }

  public void markPublished(String publisherId, Instant at) {
    owner(publisherId);
    status = OutboxStatus.PUBLISHED;
    publishedAt = at;
    lastError = null;
    clear();
  }

  public void reschedule(String publisherId, Instant next, String error) {
    owner(publisherId);
    status = OutboxStatus.FAILED;
    availableAt = next;
    lastError = sanitize(error);
    clear();
  }

  public void markDead(String publisherId, String error) {
    owner(publisherId);
    status = OutboxStatus.DEAD;
    lastError = sanitize(error);
    clear();
  }

  public void recoverExpired(Instant now) {
    if (status != OutboxStatus.PROCESSING || leaseExpiresAt.isAfter(now))
      throw new IllegalStateException("not expired");
    status = OutboxStatus.FAILED;
    availableAt = now;
    lastError = "publisher lease expired";
    clear();
  }

  private void owner(String id) {
    if (status != OutboxStatus.PROCESSING || !Objects.equals(lockedBy, id))
      throw new IllegalStateException("not owner");
  }

  private void clear() {
    lockedBy = null;
    lockedAt = null;
    leaseExpiresAt = null;
  }

  private static String sanitize(String s) {
    if (s == null) return "unknown publish error";
    String v =
        s.replaceAll(
            "(?i)(token|password|authorization|cookie)\\s*[:=]\\s*[^,\\s]+", "$1=[REDACTED]");
    return v.substring(0, Math.min(v.length(), 1000));
  }

  private static String text(String s) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException();
    return s;
  }

  public UUID getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempt() {
    return attempt;
  }

  public Instant getAvailableAt() {
    return availableAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public Long getAggregateSequence() {
    return aggregateSequence;
  }

  public Long getRowVersion() {
    return rowVersion;
  }
}
