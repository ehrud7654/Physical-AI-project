package com.anssa8bu.ssafy.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
    UmiSummary umi, RobotSummary robots, TrainingSummary trainings, ExecutionSummary executions) {

  public record UmiSummary(long totalCount, long onlineCount) {}

  public record RobotSummary(long totalCount, long availableCount) {}

  public record TrainingSummary(long runningCount, BigDecimal averageProgressPercent) {}

  public record ExecutionSummary(
      long completedTodayCount,
      long succeededTodayCount,
      long failedTodayCount,
      BigDecimal successRatePercent) {}
}
