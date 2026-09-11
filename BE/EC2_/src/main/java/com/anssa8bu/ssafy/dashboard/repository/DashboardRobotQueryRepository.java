package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.dashboard.dto.DashboardRobotResponse;
import com.anssa8bu.ssafy.dashboard.dto.DashboardRobotStatus;
import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.RobotSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRobotQueryRepository {

  private static final String ACTIVE_EXECUTION_STATUSES =
      "'REQUESTED', 'STARTING', 'RUNNING', 'CANCEL_REQUESTED'";

  private final JdbcClient jdbcClient;

  public DashboardRobotQueryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public RobotSummary summarize(UUID ownerId, Instant onlineThreshold) {
    String sql =
        """
        SELECT COUNT(*) AS total_count,
               COUNT(*) FILTER (
                   WHERE r.connection_status = 'ONLINE'
                     AND r.last_seen_at >= :onlineThreshold
                     AND NOT EXISTS (
                         SELECT 1 FROM robot_executions e
                          WHERE e.robot_id = r.id
                            AND e.status IN (%s)
                     )
               ) AS available_count
          FROM robots r
         WHERE r.owner_id = :ownerId
        """
            .formatted(ACTIVE_EXECUTION_STATUSES);

    return jdbcClient
        .sql(sql)
        .param("ownerId", ownerId)
        .param("onlineThreshold", onlineThreshold.atOffset(ZoneOffset.UTC))
        .query(
            (rs, rowNum) ->
                new RobotSummary(rs.getLong("total_count"), rs.getLong("available_count")))
        .single();
  }

  public List<DashboardRobotResponse> findRecent(UUID ownerId, Instant onlineThreshold, int limit) {
    String sql =
        """
        SELECT r.id,
               r.name,
               r.last_seen_at,
               r.current_deployment_id,
               CASE
                 WHEN r.connection_status <> 'ONLINE'
                   OR r.last_seen_at IS NULL
                   OR r.last_seen_at < :onlineThreshold THEN 'OFFLINE'
                 WHEN EXISTS (
                     SELECT 1 FROM robot_executions e
                      WHERE e.robot_id = r.id
                        AND e.status IN (%s)
                 ) THEN 'BUSY'
                 ELSE 'AVAILABLE'
               END AS dashboard_status
          FROM robots r
         WHERE r.owner_id = :ownerId
         ORDER BY r.updated_at DESC, r.id
         LIMIT :limit
        """
            .formatted(ACTIVE_EXECUTION_STATUSES);

    return jdbcClient
        .sql(sql)
        .param("ownerId", ownerId)
        .param("onlineThreshold", onlineThreshold.atOffset(ZoneOffset.UTC))
        .param("limit", limit)
        .query(
            (rs, rowNum) ->
                new DashboardRobotResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    DashboardRobotStatus.valueOf(rs.getString("dashboard_status")),
                    toInstant(rs.getTimestamp("last_seen_at")),
                    rs.getObject("current_deployment_id", UUID.class)))
        .list();
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
