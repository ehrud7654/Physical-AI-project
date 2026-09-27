package com.anssa8bu.ssafy.device.repository;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  List<Device> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  List<Device> findAllByOwnerIdAndDeviceTypeOrderByCreatedAtDesc(
      UUID ownerId, DeviceType deviceType);

  Optional<Device> findByIdAndOwnerId(UUID id, UUID ownerId);

  boolean existsByHandleNumber(String handleNumber);

  List<Device> findAllByDeviceTypeAndConnectionStatusAndLastSeenAtBefore(
      DeviceType type, com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus status,
      java.time.Instant cutoff);
}
