package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.device.entity.*;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JetsonOfflineScheduler {
  private final RuntimeStateService runtime;
  private final DeviceRepository devices;
  private final RobotRepository robots;
  private final Clock clock;
  private final Duration timeout;

  public JetsonOfflineScheduler(
      DeviceRepository d,
      RobotRepository r,
      Clock c,
      @Value("${app.jetson.offline-timeout:20s}") Duration timeout,
      RuntimeStateService runtime) {
    this.runtime = runtime;
    devices = d;
    robots = r;
    clock = c;
    this.timeout = timeout;
  }

  @Scheduled(fixedDelayString = "${app.jetson.offline-scan-interval:5s}")
  @Transactional
  public void markStaleOffline() {
    var stale =
        devices.findAllByDeviceTypeAndConnectionStatusAndLastSeenAtBefore(
            DeviceType.JETSON, DeviceConnectionStatus.ONLINE, clock.instant().minus(timeout));
    stale.forEach(
        device -> {
          if (runtime.device(device.getId(), device.getLastSeenAt()).status()
              != LiveConnectionStatus.OFFLINE) return;
          device.markOffline();
          robots.findByJetsonDeviceId(device.getId()).ifPresent(robot -> robot.markOffline());
        });
  }
}
