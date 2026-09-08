package com.anssa8bu.ssafy.event.application;

import com.anssa8bu.ssafy.event.config.OutboxProperties;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxCleanupService {
  private final OutboxEventRepository repo;
  private final OutboxProperties props;
  private final Clock clock;
  private final MeterRegistry meter;

  public OutboxCleanupService(
      OutboxEventRepository r, OutboxProperties p, Clock c, MeterRegistry m) {
    repo = r;
    props = p;
    clock = c;
    meter = m;
  }

  @Transactional
  @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 3 * * *}")
  public void scheduledCleanup() {
    if (props.enabled()) doCleanup();
  }

  @Transactional
  public int cleanupNow() {
    return doCleanup();
  }

  private int doCleanup() {
    int count = repo.deletePublishedBefore(clock.instant().minus(props.retention()));
    meter.counter("outbox.cleanup.deleted").increment(count);
    return count;
  }
}
