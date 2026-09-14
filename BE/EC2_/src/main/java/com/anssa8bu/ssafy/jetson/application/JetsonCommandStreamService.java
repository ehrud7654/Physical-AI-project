package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.jetson.dto.JetsonCommand;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class JetsonCommandStreamService {
  private final ConcurrentHashMap<UUID, SseEmitter> streams = new ConcurrentHashMap<>();

  public SseEmitter connect(UUID robotId) {
    SseEmitter emitter = new SseEmitter(0L);
    SseEmitter previous = streams.put(robotId, emitter);
    if (previous != null) previous.complete();
    emitter.onCompletion(() -> streams.remove(robotId, emitter));
    emitter.onTimeout(() -> streams.remove(robotId, emitter));
    emitter.onError(error -> streams.remove(robotId, emitter));
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException exception) {
      streams.remove(robotId, emitter);
      emitter.completeWithError(exception);
    }
    return emitter;
  }

  public void publish(JetsonCommand command) {
    SseEmitter emitter = streams.get(command.robotId());
    if (emitter == null) return;
    try {
      emitter.send(SseEmitter.event().id(command.commandId()).name("robot.command").data(command));
    } catch (IOException exception) {
      streams.remove(command.robotId(), emitter);
      emitter.completeWithError(exception);
    }
  }

  @Scheduled(fixedDelayString = "${app.jetson.sse-ping-interval-ms:15000}")
  public void ping() {
    streams.forEach(
        (robotId, emitter) -> {
          try {
            emitter.send(SseEmitter.event().comment("ping"));
          } catch (IOException exception) {
            streams.remove(robotId, emitter);
            emitter.completeWithError(exception);
          }
        });
  }
}
