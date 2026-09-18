package com.anssa8bu.ssafy.device.repository;

import com.anssa8bu.ssafy.device.entity.DeviceEnrollment;
import com.anssa8bu.ssafy.device.entity.DeviceEnrollmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceEnrollmentRepository extends JpaRepository<DeviceEnrollment, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from DeviceEnrollment e where e.deviceId = :deviceId and e.status = :status")
  Optional<DeviceEnrollment> findByDeviceIdAndStatusForUpdate(
      @Param("deviceId") UUID deviceId, @Param("status") DeviceEnrollmentStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from DeviceEnrollment e where e.id = :id")
  Optional<DeviceEnrollment> findByIdForUpdate(@Param("id") UUID id);
}
