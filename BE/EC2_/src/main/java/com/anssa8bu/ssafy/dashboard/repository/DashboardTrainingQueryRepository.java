package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.TrainingSummary;
import com.anssa8bu.ssafy.dashboard.dto.RecentTrainingResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardTrainingQueryRepository {

  private static final String ACTIVE_STATUSES =
      "'CREATED', 'COLLECTING', 'INPUT_VALIDATING', 'INPUT_READY', 'QUEUED', 'PREPARING', "
          + "'RUNNING', 'MODEL_UPLOADING', 'MODEL_VALIDATING', 'CANCEL_REQUESTED'";

  private final JdbcClient jdbcClient;

  public DashboardTrainingQueryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public TrainingSummary summarize(UUID ownerId) {
    String sql =
        """
        SELECT COUNT(*) FILTER (WHERE status IN (%s)) AS running_count,
               COALESCE(AVG(progress_percent) FILTER (WHERE status IN (%s)), 0)
                   AS average_progress
          FROM training_jobs
         WHERE owner_id = :ownerId
           AND deleted_at IS NULL
        """
            .formatted(ACTIVE_STATUSES, ACTIVE_STATUSES);

    return jdbcClient
        .sql(sql)
        .param("ownerId", ownerId)
        .query(
            (rs, rowNum) ->
                new TrainingSummary(
                    rs.getLong("running_count"), rs.getBigDecimal("average_progress")))
        .single();
  }

  public List<RecentTrainingResponse> findRecent(UUID ownerId, int limit) {
    return jdbcClient
        .sql(
            """
            SELECT j.id,
                   j.skill_id,
                   s.display_name AS skill_name,
                   j.umi_device_id,
                   d.name AS umi_device_name,
                   j.status,
                   j.progress_stage,
                   j.progress_percent,
                   j.started_at,
                   j.updated_at
              FROM training_jobs j
              JOIN skills s ON s.id = j.skill_id
              JOIN devices d ON d.id = j.umi_device_id
             WHERE j.owner_id = :ownerId
               AND j.deleted_at IS NULL
             ORDER BY j.updated_at DESC, j.id
             LIMIT :limit
            """)
        .param("ownerId", ownerId)
        .param("limit", limit)
        .query(
            (rs, rowNum) ->
                new RecentTrainingResponse(
                    rs.getObject("id", UUID.class),
                    rs.getObject("skill_id", UUID.class),
                    rs.getString("skill_name"),
                    rs.getObject("umi_device_id", UUID.class),
                    rs.getString("umi_device_name"),
                    rs.getString("status"),
                    rs.getString("progress_stage"),
                    defaultZero(rs.getBigDecimal("progress_percent")),
                    toInstant(rs.getTimestamp("started_at")),
                    toInstant(rs.getTimestamp("updated_at"))))
        .list();
  }

  private static BigDecimal defaultZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
