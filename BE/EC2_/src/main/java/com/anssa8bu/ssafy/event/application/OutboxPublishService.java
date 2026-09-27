package com.anssa8bu.ssafy.event.application;

import com.anssa8bu.ssafy.event.publisher.EventDispatcher;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublishService {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublishService.class);
  private final OutboxClaimService claimService;
  private final OutboxResultService resultService;
  private final OutboxEventRepository eventRepository;
  private final EventDispatcher dispatcher;
  private final Counter publishedCounter;
  private final Counter failedCounter;

  public OutboxPublishService(
      OutboxClaimService claimService,
      OutboxResultService resultService,
      OutboxEventRepository eventRepository,
      EventDispatcher dispatcher,
      MeterRegistry meter) {
    this.claimService = claimService;
    this.resultService = resultService;
    this.eventRepository = eventRepository;
    this.dispatcher = dispatcher;
    this.publishedCounter = meter.counter("outbox.publish", "result", "success");
    this.failedCounter = meter.counter("outbox.publish", "result", "failure");
  }

  public int publishBatch() {
    var eventIds = claimService.claim();
    for (var eventId : eventIds) {
      var event = eventRepository.findById(eventId).orElseThrow();
      try {
        dispatcher.dispatch(event);
        resultService.published(eventId);
        publishedCounter.increment();
      } catch (RuntimeException ex) {
        failedCounter.increment();
        log.warn(
            "Outbox publish failed eventId={} type={} attempt={}",
            event.getEventId(),
            event.getEventType(),
            event.getAttempt());
        resultService.failed(eventId, ex.getClass().getSimpleName());
      }
    }
    return eventIds.size();
  }
}
