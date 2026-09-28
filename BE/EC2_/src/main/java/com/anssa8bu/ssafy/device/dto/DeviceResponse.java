package com.anssa8bu.ssafy.device.dto;

import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceRegistrationStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
    UUID id,
    UUID ownerId,
    DeviceType deviceType,
    String handleNumber,
    String name,
    String location,
    LiveConnectionStatus connectionStatus,
    DeviceRegistrationStatus registrationStatus,
    Instant lastSeenAt,
    Instant createdAt) {

  public static DeviceResponse from(Device device) {
    return new DeviceResponse(
        device.getId(),
        device.getOwnerId(),
        device.getDeviceType(),
        device.getHandleNumber(),
        device.getName(),
        device.getLocation(),
        LiveConnectionStatus.OFFLINE,
        device.getRegistrationStatus(),
        device.getLastSeenAt(),
        device.getCreatedAt());
  }
}
