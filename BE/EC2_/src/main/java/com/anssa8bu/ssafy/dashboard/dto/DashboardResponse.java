package com.anssa8bu.ssafy.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record DashboardResponse(
    DashboardSummaryResponse summary,
    List<DashboardRobotResponse> robots,
    List<RecentTrainingResponse> recentTrainings,
    Instant generatedAt) {}
