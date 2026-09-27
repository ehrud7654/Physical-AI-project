package com.anssa8bu.ssafy_gpu.training.scheduler;

import com.anssa8bu.ssafy_gpu.training.application.TrainingEventService;
import com.anssa8bu.ssafy_gpu.training.persistence.TrainingEventJournal;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrainingEventRetryScheduler {
  private static final Logger log = LoggerFactory.getLogger(TrainingEventRetryScheduler.class);
  private final TrainingEventJournal journal;
  private final TrainingEventService events;

  public TrainingEventRetryScheduler(TrainingEventJournal journal, TrainingEventService events) {
    this.journal = journal;
    this.events = events;
  }

  @Scheduled(
      initialDelayString = "${app.worker.event-retry-interval:10s}",
      fixedDelayString = "${app.worker.event-retry-interval:10s}")
  public void retry() {
    for (var entry : journal.retryable(Instant.now())) {
      try {
        events.retry(entry);
      } catch (RuntimeException exception) {
        log.warn(
            "학습 이벤트 재전송 실패: eventId={}, attempts={}",
            entry.request().eventId(),
            entry.attempts() + 1,
            exception);
      }
    }
  }
}
