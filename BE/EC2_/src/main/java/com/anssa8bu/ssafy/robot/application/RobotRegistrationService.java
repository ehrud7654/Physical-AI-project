package com.anssa8bu.ssafy.robot.application;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.device.exception.DeviceAlreadyRegisteredException;
import com.anssa8bu.ssafy.device.repository.DeviceRepository;
import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.model.repository.RobotProfileRepository;
import com.anssa8bu.ssafy.robot.dto.RobotCreateRequest;
import com.anssa8bu.ssafy.robot.entity.Robot;
import com.anssa8bu.ssafy.robot.entity.RobotConnectionStatus;
import com.anssa8bu.ssafy.robot.repository.RobotRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotRegistrationService {

  private final DeviceRepository deviceRepository;
  private final RobotRepository robotRepository;
  private final RobotProfileRepository robotProfileRepository;

  public RobotRegistrationService(
      DeviceRepository deviceRepository,
      RobotRepository robotRepository,
      RobotProfileRepository robotProfileRepository) {
    this.deviceRepository = deviceRepository;
    this.robotRepository = robotRepository;
    this.robotProfileRepository = robotProfileRepository;
  }

  @Transactional
  public Robot create(UUID ownerId, RobotCreateRequest request) {
    String handleNumber = request.handleNumber().trim();
    if (deviceRepository.existsByHandleNumber(handleNumber)) {
      throw new DeviceAlreadyRegisteredException();
    }

    RobotProfile profile = resolveProfile(request.robotProfileId());
    String name = request.name().trim();

    Device device =
        new Device(
            UUID.randomUUID(),
            ownerId,
            DeviceType.JETSON,
            handleNumber,
            name,
            DeviceConnectionStatus.OFFLINE);
    device.changeLocation(request.location());
    Device savedDevice = deviceRepository.save(device);

    return robotRepository.save(
        new Robot(
            UUID.randomUUID(),
            ownerId,
            savedDevice.getId(),
            profile.getId(),
            name,
            RobotConnectionStatus.OFFLINE));
  }

  private RobotProfile resolveProfile(UUID requestedProfileId) {
    if (requestedProfileId != null) {
      return robotProfileRepository
          .findById(requestedProfileId)
          .orElseThrow(
              () ->
                  new BusinessConflictException(
                      "ROBOT_PROFILE_NOT_CONFIGURED", "등록에 사용할 로봇 프로필이 없습니다."));
    }
    return robotProfileRepository
        .findFirstByOrderByCreatedAtAsc()
        .orElseThrow(
            () ->
                new BusinessConflictException(
                    "ROBOT_PROFILE_NOT_CONFIGURED", "등록에 사용할 로봇 프로필이 없습니다."));
  }
}
