package com.anssa8bu.ssafy.common.redis.runtime;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Conditional SQL prevents late observations and unrelated entity edits from overwriting snapshots.
 */
@Component
public class RuntimeSnapshotWriter {
  private final JdbcTemplate jdbc;
  private final Duration interval;

  public RuntimeSnapshotWriter(
      JdbcTemplate jdbc, @Value("${app.runtime-state.snapshot-interval:60s}") Duration interval) {
    if (interval.isNegative() || interval.isZero())
      throw new IllegalArgumentException("Snapshot interval must be positive");
    this.jdbc = jdbc;
    this.interval = interval;
  }

  public void device(UUID id, String firmware, String runtime, Instant now) {
    var at = Timestamp.from(now);
    var cutoff = Timestamp.from(now.minus(interval));
    jdbc.update(
        """
        update devices set connection_status='ONLINE',firmware_version=?,runtime_version=?,last_seen_at=?,updated_at=?
        where id=? and (last_seen_at is null or last_seen_at < ?)
        and (last_seen_at is null or last_seen_at <= ? or connection_status <> 'ONLINE'
          or firmware_version is distinct from ? or runtime_version is distinct from ?)
        """,
        firmware,
        runtime,
        at,
        at,
        id,
        at,
        cutoff,
        firmware,
        runtime);
    jdbc.update(
        """
        update robots set connection_status='ONLINE',last_seen_at=?,updated_at=?,row_version=row_version+1
        where jetson_device_id=? and (last_seen_at is null or last_seen_at < ?)
        and (last_seen_at is null or last_seen_at <= ? or connection_status <> 'ONLINE')
        """,
        at,
        at,
        id,
        at,
        cutoff);
  }

  public void storage(StorageNodeState state) {
    var at = Timestamp.from(state.lastSeenAt());
    jdbc.update(
        """
        update storage_nodes set status=?,capacity_bytes=?,available_bytes=?,last_seen_at=?,updated_at=?,row_version=row_version+1
        where id=? and (last_seen_at is null or last_seen_at < ?)
        and (last_seen_at is null or last_seen_at <= ? or status <> ?)
        """,
        state.status().name(),
        state.capacityBytes(),
        state.availableBytes(),
        at,
        at,
        state.nodeId(),
        at,
        Timestamp.from(state.lastSeenAt().minus(interval)),
        state.status().name());
  }
}
