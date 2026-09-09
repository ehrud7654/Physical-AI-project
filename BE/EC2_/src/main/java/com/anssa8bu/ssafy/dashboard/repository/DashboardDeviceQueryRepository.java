package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.UmiSummary;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardDeviceQueryRepository {

  private final JdbcClient jdbcClient;

  public DashboardDeviceQueryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public UmiSummary summarize(UUID ownerId, Instant onlineThreshold) {
    return jdbcClient
        .sql(
            """
            SELECT COUNT(*) AS total_count,
                   COUNT(*) FILTER (
                       WHERE connection_status = 'ONLINE' AND last_seen_at >= :onlineThreshold
                   ) AS online_count
              FROM devices
             WHERE owner_id = :ownerId
               AND device_type = 'UMI_CAMERA'
            """)
        .param("ownerId", ownerId)
        .param("onlineThreshold", onlineThreshold.atOffset(ZoneOffset.UTC))
        .query(
            (rs, rowNum) -> new UmiSummary(rs.getLong("total_count"), rs.getLong("online_count")))
        .single();
  }
}
