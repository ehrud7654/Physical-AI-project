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
    if (request.occurredAt().isAfter(now.plus(Duration.ofMinutes(5))))
      throw new BusinessConflictException("INVALID_EVENT_TIME", "이벤트 시각이 서버 시각보다 지나치게 미래입니다.");
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
    try {
      switch (event.type()) {
        case "STARTED" -> { requireEmpty(p); job.start(event.occurredAt()); }
        case "PROGRESS" -> job.applyProgress(requiredText(p,"stage"), requiredDecimal(p,"percent"),
            optionalInteger(p,"epoch"), optionalInteger(p,"totalEpochs"));
        case "TRAINING_FINISHED" -> {
          requiredPositiveLong(p,"checkpointSizeBytes");
          requiredSha256(p,"checkpointSha256");
          job.markModelUploading(event.occurredAt());
        }
        case "FAILED" -> job.fail(requiredText(p,"code"), requiredText(p,"message"), now);
        case "CANCELED" -> { requireEmpty(p); job.cancel(now); }
        default -> throw new BusinessConflictException("UNSUPPORTED_EVENT_TYPE", "지원하지 않는 학습 이벤트입니다.");
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new BusinessConflictException("INVALID_TRAINING_EVENT", e.getMessage());
    }
  }
  private static void requireEmpty(Map<String,Object> p) {
    if (!p.isEmpty()) throw new IllegalArgumentException("이 이벤트의 payload는 빈 객체여야 합니다.");
  }
  private static String requiredText(Map<String,Object> p,String k) {
    Object v=p.get(k); if (v==null || v.toString().isBlank()) throw new IllegalArgumentException(k+"가 필요합니다.");
    return v.toString();
  }
  private static Integer optionalInteger(Map<String,Object> p,String k) {
    Object v=p.get(k); if (v==null) return null;
    if (!(v instanceof Number n)) throw new IllegalArgumentException(k+"는 정수여야 합니다.");
    return n.intValue();
  }
  private static BigDecimal requiredDecimal(Map<String,Object> p,String k) {
    Object v=p.get(k); if (!(v instanceof Number n)) throw new IllegalArgumentException(k+"는 숫자여야 합니다.");
    return new BigDecimal(n.toString());
  }
  private static long requiredPositiveLong(Map<String,Object> p,String k) {
    Object v=p.get(k); if (!(v instanceof Number n) || n.longValue()<=0) throw new IllegalArgumentException(k+"는 양수여야 합니다.");
    return n.longValue();
  }
  private static String requiredSha256(Map<String,Object> p,String k) {
    String value=requiredText(p,k);
    if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(k+"는 소문자 SHA-256이어야 합니다.");
    return value;
  }
}
