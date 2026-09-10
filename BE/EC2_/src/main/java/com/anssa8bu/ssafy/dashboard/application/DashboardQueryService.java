package com.anssa8bu.ssafy.dashboard.application;

import com.anssa8bu.ssafy.dashboard.dto.DashboardResponse;
import com.anssa8bu.ssafy.dashboard.dto.DashboardSummaryResponse;
import com.anssa8bu.ssafy.dashboard.repository.DashboardDeviceQueryRepository;
import com.anssa8bu.ssafy.dashboard.repository.DashboardExecutionQueryRepository;
import com.anssa8bu.ssafy.dashboard.repository.DashboardRobotQueryRepository;
import com.anssa8bu.ssafy.dashboard.repository.DashboardTrainingQueryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {

  private static final int RECENT_ITEM_LIMIT = 5;
  private static final Duration UMI_ONLINE_WINDOW = Duration.ofSeconds(60);
  private static final Duration ROBOT_ONLINE_WINDOW = Duration.ofSeconds(20);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

  private final DashboardDeviceQueryRepository deviceRepository;
  private final DashboardRobotQueryRepository robotRepository;
  private final DashboardTrainingQueryRepository trainingRepository;
  private final DashboardExecutionQueryRepository executionRepository;
  private final Clock clock;

  public DashboardQueryService(
      DashboardDeviceQueryRepository deviceRepository,
      DashboardRobotQueryRepository robotRepository,
      DashboardTrainingQueryRepository trainingRepository,
      DashboardExecutionQueryRepository executionRepository,
      Clock clock) {
    this.deviceRepository = deviceRepository;
    this.robotRepository = robotRepository;
    this.trainingRepository = trainingRepository;
    this.executionRepository = executionRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public DashboardResponse getDashboard(UUID ownerId) {
    Instant now = clock.instant();
    LocalDate businessDate = LocalDate.ofInstant(now, BUSINESS_ZONE);
    Instant startOfDay = businessDate.atStartOfDay(BUSINESS_ZONE).toInstant();
    Instant endOfDay = businessDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();

    var umiSummary = deviceRepository.summarize(ownerId, now.minus(UMI_ONLINE_WINDOW));
    var robotSummary = robotRepository.summarize(ownerId, now.minus(ROBOT_ONLINE_WINDOW));
    var trainingSummary = trainingRepository.summarize(ownerId);
    var executionSummary = executionRepository.summarize(ownerId, startOfDay, endOfDay);

    return new DashboardResponse(
        new DashboardSummaryResponse(umiSummary, robotSummary, trainingSummary, executionSummary),
        robotRepository.findRecent(ownerId, now.minus(ROBOT_ONLINE_WINDOW), RECENT_ITEM_LIMIT),
        trainingRepository.findRecent(ownerId, RECENT_ITEM_LIMIT),
        now);
  }
}
