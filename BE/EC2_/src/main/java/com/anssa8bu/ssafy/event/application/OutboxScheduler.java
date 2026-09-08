package com.anssa8bu.ssafy.event.application;

import com.anssa8bu.ssafy.event.config.OutboxProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {
  private final OutboxPublishService publishService;
  private final OutboxClaimService claimService;
  private final OutboxProperties properties;

  public OutboxScheduler(
      OutboxPublishService publishService,
      OutboxClaimService claimService,
      OutboxProperties properties) {
    this.publishService = publishService;
    this.claimService = claimService;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.outbox.fixed-delay:1s}")
  public void run() {
    if (!properties.enabled()) {
      return;
    }
    claimService.recoverExpired();
    publishService.publishBatch();
  }
}
