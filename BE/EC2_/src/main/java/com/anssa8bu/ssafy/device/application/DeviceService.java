package com.anssa8bu.ssafy.device.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.redis.runtime.*;
import com.anssa8bu.ssafy.device.dto.DeviceCreateRequest;
import com.anssa8bu.ssafy.device.dto.DeviceResponse;
import com.anssa8bu.ssafy.device.dto.DeviceUpdateRequest;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceRegistrationStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.exception.DeviceAlreadyRegisteredException;
import com.anssa8bu.ssafy.device.exception.DeviceNotFoundException;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.model.repository.RobotProfileRepository;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.entity.RobotConnectionStatus;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final RuntimeStateService runtime;
  private final DeviceRepository deviceRepository;
  private final RobotRepository robotRepository;
  private final RobotProfileRepository robotProfileRepository;

  public DeviceService(
      DeviceRepository deviceRepository,
      RobotRepository robotRepository,
      RobotProfileRepository robotProfileRepository,
      RuntimeStateService runtime) {
    this.deviceRepository = deviceRepository;
    this.robotRepository = robotRepository;
    this.robotProfileRepository = robotProfileRepository;
    this.runtime = runtime;
  }

  @Transactional
  public DeviceResponse create(UUID ownerId, DeviceCreateRequest request) {
    String handleNumber = request.handleNumber().trim();
    if (deviceRepository.existsByHandleNumber(handleNumber)) {
      throw new DeviceAlreadyRegisteredException();
    }

    Device device =
        new Device(
            UUID.randomUUID(),
            ownerId,
            request.deviceType(),
            handleNumber,
            request.name().trim(),
            DeviceConnectionStatus.OFFLINE);
    device.changeLocation(request.location());
    Device savedDevice = deviceRepository.save(device);

    if (request.deviceType() == DeviceType.JETSON) {
      UUID profileId =
          robotProfileRepository
              .findFirstByOrderByCreatedAtAsc()
              .orElseThrow(
                  () ->
                      new BusinessConflictException(
                          "ROBOT_PROFILE_NOT_CONFIGURED", "등록에 사용할 로봇 프로필이 없습니다."))
              .getId();
      robotRepository.save(
          new Robot(
              UUID.randomUUID(),
              ownerId,
              savedDevice.getId(),
              profileId,
              savedDevice.getName(),
              RobotConnectionStatus.OFFLINE));
    }

    return response(savedDevice);
  }

  @Transactional(readOnly = true)
  public List<DeviceResponse> findAll(UUID ownerId, DeviceType deviceType) {
    List<Device> devices =
        deviceType == null
            ? deviceRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
            : deviceRepository.findAllByOwnerIdAndDeviceTypeOrderByCreatedAtDesc(
                ownerId, deviceType);
    return devices.stream()
        .filter(device -> device.getRegistrationStatus() == DeviceRegistrationStatus.REGISTERED)
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public DeviceResponse findOne(UUID ownerId, UUID deviceId) {
    return response(findOwnedDevice(ownerId, deviceId));
  }

  @Transactional
  public DeviceResponse update(UUID ownerId, UUID deviceId, DeviceUpdateRequest request) {
    Device device = findOwnedDevice(ownerId, deviceId);
    device.rename(request.name().trim());
    device.changeLocation(request.location());
    return response(device);
  }

  private DeviceResponse response(Device device) {
    var live = runtime.device(device.getId(), device.getLastSeenAt());
    return new DeviceResponse(
        device.getId(),
        device.getOwnerId(),
        device.getDeviceType(),
        device.getHandleNumber(),
        device.getName(),
        device.getLocation(),
        live.status(),
        device.getRegistrationStatus(),
        live.lastSeenAt(),
        device.getCreatedAt());
  }

  private Device findOwnedDevice(UUID ownerId, UUID deviceId) {
    return deviceRepository
        .findByIdAndOwnerId(deviceId, ownerId)
        .orElseThrow(DeviceNotFoundException::new);
  }
}
