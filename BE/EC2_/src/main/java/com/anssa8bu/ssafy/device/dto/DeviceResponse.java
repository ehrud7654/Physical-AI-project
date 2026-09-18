package com.anssa8bu.ssafy.device.dto;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.entity.DeviceRegistrationStatus;
import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
    UUID id,
    UUID ownerId,
    DeviceType deviceType,
    String handleNumber,
    String name,
    String location,
    DeviceConnectionStatus connectionStatus,
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
        device.getConnectionStatus(),
        device.getRegistrationStatus(),
        device.getLastSeenAt(),
        device.getCreatedAt());
  }
}
