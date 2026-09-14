package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatRequest;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatResponse;
import com.anssa8bu.ssafy.robot.entity.Robot;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JetsonHeartbeatService {
  private final JetsonIdentityService identity;
  private final Clock clock;
  private final int intervalSeconds;

  public JetsonHeartbeatService(
      JetsonIdentityService identity,
      Clock clock,
      @Value("${app.jetson.heartbeat-interval-seconds:15}") int intervalSeconds) {
    this.identity = identity;
    this.clock = clock;
    this.intervalSeconds = intervalSeconds;
  }

  @Transactional
  public JetsonHeartbeatResponse heartbeat(Jwt jwt, JetsonHeartbeatRequest request) {
    Device device = identity.requireDevice(jwt);
    Robot robot = identity.requireRobot(jwt);
    Instant now = clock.instant();
    device.reportHeartbeat(
        DeviceConnectionStatus.ONLINE, request.firmwareVersion(), request.runtimeVersion(), now);
    robot.reportHeartbeat(now);
    return new JetsonHeartbeatResponse(now, intervalSeconds);
  }
}
