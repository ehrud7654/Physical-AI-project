package com.anssa8bu.ssafy.jetson.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class JetsonIdentityService {
  private final DeviceRepository devices;
  private final RobotRepository robots;

  public JetsonIdentityService(DeviceRepository devices, RobotRepository robots) {
    this.devices = devices;
    this.robots = robots;
  }

  public Device requireDevice(Jwt jwt) {
    Object value = jwt.getClaim("deviceId");
    if (value == null)
      throw new BusinessConflictException("INVALID_DEVICE_TOKEN", "장치 식별 정보가 없습니다.");
    UUID deviceId;
    try {
      deviceId = UUID.fromString(value.toString());
    } catch (IllegalArgumentException exception) {
      throw new BusinessConflictException("INVALID_DEVICE_TOKEN", "장치 식별 정보가 올바르지 않습니다.");
    }
    Device device =
        devices.findById(deviceId).orElseThrow(() -> new ResourceNotFoundException("Device"));
    if (device.getDeviceType() != DeviceType.JETSON) {
      throw new BusinessConflictException("DEVICE_TYPE_MISMATCH", "JETSON 장치 토큰이 필요합니다.");
    }
    if (!device.getOwnerId().toString().equals(jwt.getSubject()))
      throw new BusinessConflictException("INVALID_DEVICE_TOKEN", "장치 소유자 정보가 일치하지 않습니다.");
    return device;
  }

  public Robot requireRobot(Jwt jwt) {
    Device device = requireDevice(jwt);
    return robots
        .findByJetsonDeviceId(device.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Robot"));
  }
}
