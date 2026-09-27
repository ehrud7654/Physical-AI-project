package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.jetson.dto.JetsonCommand;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JetsonCommandStreamService {
  private final ConcurrentHashMap<UUID, Connection> streams = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, ReentrantLock> robotLocks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
  private final MeterRegistry meter;

  public JetsonCommandStreamService(MeterRegistry meter) {
    this.meter = meter;
  }

  public SseEmitter connectAndReplay(UUID robotId, String lastEventId, Runnable replay) {
    ReentrantLock lock = lock(robotId);
    lock.lock();
    try {
      long generation =
          generations.computeIfAbsent(robotId, ignored -> new AtomicLong()).incrementAndGet();
      SseEmitter emitter = new SseEmitter(0L);
      Connection connection = new Connection(emitter, generation, normalize(lastEventId));
      Connection previous = streams.put(robotId, connection);
      if (previous != null) previous.emitter().complete();
      emitter.onCompletion(() -> remove(robotId, connection));
      emitter.onTimeout(() -> remove(robotId, connection));
      emitter.onError(error -> remove(robotId, connection));
      try {
        emitter.send(SseEmitter.event().comment("connected generation=" + generation));
        replay.run();
        meter.counter("jetson.sse.connection", "result", "connected").increment();
      } catch (RuntimeException | IOException exception) {
        remove(robotId, connection);
        emitter.completeWithError(exception);
      }
      return emitter;
    } finally {
      lock.unlock();
    }
  }

  public void publish(JetsonCommand command) {
    ReentrantLock lock = lock(command.robotId());
    lock.lock();
    try {
      Connection connection = streams.get(command.robotId());
      if (connection == null) {
        meter.counter("jetson.command.delivery", "result", "offline").increment();
        return;
      }
      if (command.commandId().equals(connection.lastEventId())) {
        meter.counter("jetson.command.delivery", "result", "already_received").increment();
        return;
      }
      try {
        connection
            .emitter()
            .send(SseEmitter.event().id(command.commandId()).name("robot.command").data(command));
        connection.lastEventId(command.commandId());
        meter.counter("jetson.command.delivery", "result", "sent").increment();
      } catch (IOException exception) {
        remove(command.robotId(), connection);
        connection.emitter().completeWithError(exception);
        meter.counter("jetson.command.delivery", "result", "failure").increment();
      }
    } finally {
      lock.unlock();
    }
  }

  public long generation(UUID robotId) {
    AtomicLong value = generations.get(robotId);
    return value == null ? 0 : value.get();
  }

  @Scheduled(fixedDelayString = "${app.jetson.sse-ping-interval-ms:15000}")
  public void ping() {
    streams.forEach(
        (robotId, connection) -> {
          try {
            connection.emitter().send(SseEmitter.event().comment("ping"));
          } catch (IOException exception) {
            remove(robotId, connection);
            connection.emitter().completeWithError(exception);
          }
        });
  }

  private ReentrantLock lock(UUID robotId) {
    return robotLocks.computeIfAbsent(robotId, ignored -> new ReentrantLock());
  }

  private void remove(UUID robotId, Connection connection) {
    streams.remove(robotId, connection);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static final class Connection {
    private final SseEmitter emitter;

    @SuppressWarnings("unused")
    private final long generation;

    private volatile String lastEventId;

    private Connection(SseEmitter emitter, long generation, String lastEventId) {
      this.emitter = emitter;
      this.generation = generation;
      this.lastEventId = lastEventId;
    }

    SseEmitter emitter() {
      return emitter;
    }

    String lastEventId() {
      return lastEventId;
    }

    void lastEventId(String value) {
      lastEventId = value;
    }
  }
}
