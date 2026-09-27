package com.anssa8bu.ssafy.deployment.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeploymentTimeoutScheduler {
  private final DeploymentTimeoutService timeoutService;

  public DeploymentTimeoutScheduler(DeploymentTimeoutService timeoutService) {
    this.timeoutService = timeoutService;
  }

  @Scheduled(fixedDelayString = "${app.deployment.timeout-scan-interval:5s}")
  public void failExpiredPendingDeployments() {
    timeoutService.failExpiredPendingDeployments();
  }
}
