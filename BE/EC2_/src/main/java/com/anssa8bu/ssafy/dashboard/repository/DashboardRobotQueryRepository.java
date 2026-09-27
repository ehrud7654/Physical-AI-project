package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.dashboard.dto.*;
import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.RobotSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRobotQueryRepository {
  private final JdbcClient jdbcClient;
  private final RuntimeStateService runtime;

  public DashboardRobotQueryRepository(JdbcClient jdbcClient, RuntimeStateService runtime) {
    this.jdbcClient = jdbcClient;
    this.runtime = runtime;
  }

  private List<DashboardRobotResponse> query(UUID ownerId, Integer limit) {
    String sql =
        """
        select r.*, exists(select 1 from robot_executions e where e.robot_id=r.id
          and e.status in ('REQUESTED','STARTING','RUNNING','CANCEL_REQUESTED','STATUS_UNKNOWN')) as busy
        from robots r where r.owner_id=:ownerId order by r.updated_at desc,r.id
        """;
    var query =
        jdbcClient.sql(sql + (limit == null ? "" : " limit :limit")).param("ownerId", ownerId);
    if (limit != null) query.param("limit", limit);
    return query
        .query(
            (rs, n) -> {
              var live =
                  runtime.device(
                      rs.getObject("jetson_device_id", UUID.class),
                      toInstant(rs.getTimestamp("last_seen_at")));
              var status =
                  switch (live.status()) {
                    case UNKNOWN -> DashboardRobotStatus.UNKNOWN;
                    case OFFLINE -> DashboardRobotStatus.OFFLINE;
                    case ONLINE ->
                        rs.getBoolean("busy")
                            ? DashboardRobotStatus.BUSY
                            : DashboardRobotStatus.AVAILABLE;
                  };
              return new DashboardRobotResponse(
                  rs.getObject("id", UUID.class),
                  rs.getString("name"),
                  status,
                  live.lastSeenAt(),
                  rs.getObject("current_deployment_id", UUID.class));
            })
        .list();
  }

  public RobotSummary summarize(UUID ownerId) {
    var robots = query(ownerId, null);
    return new RobotSummary(
        robots.size(),
        robots.stream().filter(r -> r.status() == DashboardRobotStatus.AVAILABLE).count());
  }

  public List<DashboardRobotResponse> findRecent(UUID ownerId, int limit) {
    return query(ownerId, limit);
  }

  private static Instant toInstant(Timestamp time) {
    return time == null ? null : time.toInstant();
  }
}
