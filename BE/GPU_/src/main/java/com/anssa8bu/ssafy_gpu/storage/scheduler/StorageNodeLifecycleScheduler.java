package com.anssa8bu.ssafy_gpu.storage.scheduler;

import com.anssa8bu.ssafy_gpu.storage.application.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageNodeLifecycleScheduler {
  private static final Logger log = LoggerFactory.getLogger(StorageNodeLifecycleScheduler.class);
  private final StorageNodeRegistrationService registration;
  private final StorageNodeHeartbeatService heartbeat;

  public StorageNodeLifecycleScheduler(
      StorageNodeRegistrationService registration, StorageNodeHeartbeatService heartbeat) {
    this.registration = registration;
    this.heartbeat = heartbeat;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void register() {
    try {
      registration.register();
    } catch (RuntimeException e) {
      log.warn("StorageNode 등록 실패", e);
    }
  }

  @Scheduled(fixedDelayString = "${app.worker.storage-heartbeat-interval:15s}")
  public void heartbeat() {
    try {
      heartbeat.heartbeat();
    } catch (IllegalStateException unregistered) {
      register();
    } catch (RuntimeException e) {
      log.warn("StorageNode heartbeat 실패: {}", e.getMessage());
    }
  }
}
