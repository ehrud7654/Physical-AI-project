package com.anssa8bu.ssafy.device.application;

import com.anssa8bu.ssafy.device.dto.DeviceCreateRequest;
import com.anssa8bu.ssafy.device.dto.DeviceResponse;
import com.anssa8bu.ssafy.device.dto.DeviceUpdateRequest;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.exception.DeviceAlreadyRegisteredException;
import com.anssa8bu.ssafy.device.exception.DeviceNotFoundException;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  private final DeviceRepository deviceRepository;

  public DeviceService(DeviceRepository deviceRepository) {
    this.deviceRepository = deviceRepository;
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
    return DeviceResponse.from(deviceRepository.save(device));
  }

  @Transactional(readOnly = true)
  public List<DeviceResponse> findAll(UUID ownerId, DeviceType deviceType) {
    List<Device> devices =
        deviceType == null
            ? deviceRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
            : deviceRepository.findAllByOwnerIdAndDeviceTypeOrderByCreatedAtDesc(
                ownerId, deviceType);
    return devices.stream().map(DeviceResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DeviceResponse findOne(UUID ownerId, UUID deviceId) {
    return DeviceResponse.from(findOwnedDevice(ownerId, deviceId));
  }

  @Transactional
  public DeviceResponse update(UUID ownerId, UUID deviceId, DeviceUpdateRequest request) {
    Device device = findOwnedDevice(ownerId, deviceId);
    device.rename(request.name().trim());
    device.changeLocation(request.location());
    return DeviceResponse.from(device);
  }

  private Device findOwnedDevice(UUID ownerId, UUID deviceId) {
    return deviceRepository
        .findByIdAndOwnerId(deviceId, ownerId)
        .orElseThrow(DeviceNotFoundException::new);
  }
}
