package com.anssa8bu.ssafy.jetson.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JetsonCommandStreamServiceTest {
  @Test
  void reconnectReplacesConnectionAndIncrementsGeneration() {
    var service = new JetsonCommandStreamService(new SimpleMeterRegistry());
    UUID robotId = UUID.randomUUID();
    AtomicInteger replayCount = new AtomicInteger();

    service.connectAndReplay(robotId, null, replayCount::incrementAndGet);
    service.connectAndReplay(robotId, "execution:old:start", replayCount::incrementAndGet);

    assertThat(service.generation(robotId)).isEqualTo(2);
    assertThat(replayCount).hasValue(2);
  }
}
