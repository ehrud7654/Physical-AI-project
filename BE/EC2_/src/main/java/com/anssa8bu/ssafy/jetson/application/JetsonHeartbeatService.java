package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatRequest;
import com.anssa8bu.ssafy.jetson.dto.JetsonHeartbeatResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JetsonHeartbeatService {
  private final com.anssa8bu.ssafy.device.repository.DeviceRepository devices;
  private final RuntimeStateService runtime;
  private final RuntimeSnapshotWriter snapshots;
  private final JetsonIdentityService identity;
  private final Clock clock;
  private final int intervalSeconds;

  public JetsonHeartbeatService(
      JetsonIdentityService identity,
      Clock clock,
      @Value("${app.jetson.heartbeat-interval-seconds:15}") int intervalSeconds,
      RuntimeStateService runtime,
      RuntimeSnapshotWriter snapshots,
      com.anssa8bu.ssafy.device.repository.DeviceRepository devices) {
    this.devices = devices;
    this.runtime = runtime;
    this.snapshots = snapshots;
    this.identity = identity;
    this.clock = clock;
    this.intervalSeconds = intervalSeconds;
  }

  @Transactional
  public JetsonHeartbeatResponse heartbeat(Jwt jwt, JetsonHeartbeatRequest request) {
    Device device =
        devices
            .findByIdAndOwnerId(
                java.util.UUID.fromString(jwt.getClaimAsString("deviceId")),
                java.util.UUID.fromString(jwt.getSubject()))
            .orElseThrow(
                () -> new com.anssa8bu.ssafy.common.exception.ResourceNotFoundException("Device"));
    if (device.getRegistrationStatus()
        != com.anssa8bu.ssafy.device.entity.DeviceRegistrationStatus.REGISTERED)
      throw new BusinessConflictException("DEVICE_NOT_REGISTERED", "등록 완료된 장치가 아닙니다.");
    boolean jetson = device.getDeviceType() == com.anssa8bu.ssafy.device.entity.DeviceType.JETSON;
    if (!(jetson ? "robot-command-v1" : "device-heartbeat-v1").equals(request.contractVersion()))
      throw new BusinessConflictException("UNSUPPORTED_CONTRACT_VERSION", "지원하지 않는 로봇 명령 계약입니다.");
    if (jetson) identity.requireRobot(jwt);
    Instant now = clock.instant();
    snapshots.device(device.getId(), request.firmwareVersion(), request.runtimeVersion(), now);
    runtime.deviceHeartbeat(device.getId(), now);
    return new JetsonHeartbeatResponse(now, intervalSeconds);
  }
}
