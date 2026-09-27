package com.anssa8bu.ssafy.dashboard.repository;

import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse.UmiSummary;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardDeviceQueryRepository {
  private final JdbcClient jdbcClient;
  private final RuntimeStateService runtime;

  public DashboardDeviceQueryRepository(JdbcClient jdbcClient, RuntimeStateService runtime) {
    this.jdbcClient = jdbcClient;
    this.runtime = runtime;
  }

  public UmiSummary summarize(UUID ownerId, Instant onlineThreshold) {
    var ids =
        jdbcClient
            .sql(
                "select id from devices where owner_id=:ownerId and device_type='UMI_CAMERA' and registration_status='REGISTERED'")
            .param("ownerId", ownerId)
            .query(UUID.class)
            .list();
    long online =
        ids.stream()
            .filter(id -> runtime.device(id, null).status() == LiveConnectionStatus.ONLINE)
            .count();
    return new UmiSummary(ids.size(), online);
  }
}
