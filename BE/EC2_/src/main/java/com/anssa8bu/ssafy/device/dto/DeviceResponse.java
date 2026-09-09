package com.anssa8bu.ssafy.device.dto;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
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
    DeviceConnectionStatus connectionStatus,
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
        device.getLastSeenAt(),
        device.getCreatedAt());
  }
}
