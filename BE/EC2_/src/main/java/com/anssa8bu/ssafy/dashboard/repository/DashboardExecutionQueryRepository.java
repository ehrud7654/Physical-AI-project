package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.ExecutionSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardExecutionQueryRepository {

  private final JdbcClient jdbcClient;

  public DashboardExecutionQueryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public ExecutionSummary summarize(UUID ownerId, Instant startInclusive, Instant endExclusive) {
    return jdbcClient
        .sql(
            """
            SELECT COUNT(*) FILTER (
                       WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELED', 'STATUS_UNKNOWN')
                   ) AS completed_count,
                   COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded_count,
                   COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_count
              FROM robot_executions
             WHERE owner_id = :ownerId
               AND finished_at >= :startInclusive
               AND finished_at < :endExclusive
            """)
        .param("ownerId", ownerId)
        .param("startInclusive", startInclusive.atOffset(ZoneOffset.UTC))
        .param("endExclusive", endExclusive.atOffset(ZoneOffset.UTC))
        .query(
            (rs, rowNum) -> {
              long succeeded = rs.getLong("succeeded_count");
              long failed = rs.getLong("failed_count");
              return new ExecutionSummary(
                  rs.getLong("completed_count"),
                  succeeded,
                  failed,
                  calculateSuccessRate(succeeded, failed));
            })
        .single();
  }

  private static BigDecimal calculateSuccessRate(long succeeded, long failed) {
    long decided = succeeded + failed;
    if (decided == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return BigDecimal.valueOf(succeeded)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(decided), 2, RoundingMode.HALF_UP);
  }
}
