package com.anssa8bu.ssafy.training.application.worker;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.event.application.*;
import com.anssa8bu.ssafy.event.repository.OutboxEventRepository;
import com.anssa8bu.ssafy.common.redis.repository.TrainingLeaseCacheRepository;
import com.anssa8bu.ssafy.training.entity.*;
import com.anssa8bu.ssafy.training.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingEventService {
  private final TrainingJobRepository jobs; private final TrainingJobEventRepository events;
  private final TrainingLeaseValidator leases; private final OutboxEventFactory outboxFactory;
  private final OutboxEventRepository outbox; private final Clock clock;
  private final TrainingLeaseCacheRepository leaseCache;
  public TrainingEventService(TrainingJobRepository jobs, TrainingJobEventRepository events,
      TrainingLeaseValidator leases, OutboxEventFactory outboxFactory,
      OutboxEventRepository outbox, Clock clock, TrainingLeaseCacheRepository leaseCache) {
    this.jobs=jobs; this.events=events; this.leases=leases; this.outboxFactory=outboxFactory;
    this.outbox=outbox; this.clock=clock;this.leaseCache=leaseCache;
  }
  @Transactional
  public EventResponse accept(UUID id, EventRequest request) {
    TrainingJob job=jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("TrainingJob"));
    Instant now=clock.instant(); leases.validate(job, null, request.attempt(), request.leaseToken(), now);
    var duplicate=events.findByTrainingJobIdAndAttemptAndEventId(id, request.attempt(), request.eventId());
    if (duplicate.isPresent()) {
      TrainingJobEvent saved=duplicate.get();
      if (saved.getSequence()!=request.sequence() || !saved.getEventType().equals(request.type())
          || !saved.getPayload().equals(request.payload()))
        throw new BusinessConflictException("EVENT_ID_REUSED", "같은 eventId에 다른 이벤트를 사용할 수 없습니다.");
      return new EventResponse(true, job.getStatus(), saved.getSequence());
    }
    long last=events.findFirstByTrainingJobIdAndAttemptOrderBySequenceDesc(id, request.attempt())
        .map(TrainingJobEvent::getSequence).orElse(0L);
    if (request.sequence()<=last) throw new BusinessConflictException("STALE_EVENT", "이전 sequence의 이벤트입니다.");
    apply(job, request, now);
    events.save(new TrainingJobEvent(UUID.randomUUID(), id, request.attempt(), request.eventId(),
        request.sequence(), request.type(), request.payload(), request.occurredAt(), now));
    DomainEventPayload payload=new DomainEventPayload(1, request.eventId(),
        "TRAINING_"+request.type(), "TRAINING_JOB", id.toString(), job.getOwnerId(), now,
        request.payload());
    outbox.save(outboxFactory.create(payload, request.sequence()));
    if (job.getStatus()==TrainingJobStatus.FAILED || job.getStatus()==TrainingJobStatus.CANCELED) {
      job.releaseAssignment();
      try { leaseCache.delete(job.getId()); } catch (RuntimeException ignored) { }
    }
    return new EventResponse(true, job.getStatus(), request.sequence());
  }
  private void apply(TrainingJob job, EventRequest event, Instant now) {
    Map<String,Object> p=event.payload();
    switch (event.type()) {
      case "STARTED" -> job.start(event.occurredAt());
      case "PROGRESS" -> job.applyProgress(text(p,"stage"), decimal(p,"percent"), integer(p,"epoch"), integer(p,"totalEpochs"));
      case "TRAINING_FINISHED" -> job.markModelUploading(event.occurredAt());
      case "FAILED", "INPUT_INVALID" -> job.fail(text(p,"code"), text(p,"message"), now);
      case "CANCELED" -> job.cancel(now);
      case "INPUT_PROGRESS", "INPUT_READY" -> { }
      default -> throw new BusinessConflictException("UNSUPPORTED_EVENT_TYPE", "지원하지 않는 학습 이벤트입니다.");
    }
  }
  private static String text(Map<String,Object> p,String k){ Object v=p.get(k); return v==null?null:v.toString(); }
  private static Integer integer(Map<String,Object> p,String k){ Object v=p.get(k); return v instanceof Number n?n.intValue():null; }
  private static BigDecimal decimal(Map<String,Object> p,String k){ Object v=p.get(k); return v instanceof Number n?new BigDecimal(n.toString()):null; }
}
