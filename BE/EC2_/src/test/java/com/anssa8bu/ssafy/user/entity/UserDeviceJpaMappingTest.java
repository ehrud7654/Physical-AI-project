package com.anssa8bu.ssafy.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import com.anssa8bu.ssafy.device.entity.DeviceCredentialStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserDeviceJpaMappingTest extends IntegrationTestBase {

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID userId;
  private UUID sessionId;
  private UUID deviceId;

  @Test
  void persistsFlushesClearsAndReadsUserAndDeviceDomain() {
    userId = UUID.randomUUID();
    sessionId = UUID.randomUUID();
    deviceId = UUID.randomUUID();
    Instant expiresAt = Instant.parse("2026-09-08T05:00:00.123456Z");

    User user =
        new User(
            userId,
            "entity-" + userId + "@example.com",
            "$2a$10$test-password-hash",
            "Entity 테스트 사용자",
            UserStatus.ACTIVE);
    RefreshSession session = new RefreshSession(sessionId, userId, "a".repeat(64), expiresAt);
    Device device =
        new Device(
            deviceId,
            userId,
            DeviceType.UMI_CAMERA,
            "UMI-" + deviceId,
            "Entity 테스트 UMI",
            DeviceConnectionStatus.OFFLINE);
    Instant enrolledAt = Instant.parse("2026-09-08T01:00:00.123456Z");
    DeviceCredential credential =
        new DeviceCredential(deviceId, "device-secret-hash", enrolledAt);

    entityManager.persist(user);
    entityManager.persist(session);
    entityManager.persist(device);
    entityManager.persist(credential);
    entityManager.flush();
    entityManager.clear();

    User foundUser = entityManager.find(User.class, userId);
    RefreshSession foundSession = entityManager.find(RefreshSession.class, sessionId);
    Device foundDevice = entityManager.find(Device.class, deviceId);
    DeviceCredential foundCredential = entityManager.find(DeviceCredential.class, deviceId);

    assertThat(foundUser.getId()).isEqualTo(userId);
    assertThat(foundUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(foundUser.getCreatedAt()).isNotNull();
    assertThat(foundUser.getUpdatedAt()).isNotNull();

    assertThat(foundSession.getUserId()).isEqualTo(userId);
    assertThat(foundSession.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(foundSession.getRevokedAt()).isNull();
    assertThat(foundSession.getReplacedBySessionId()).isNull();
    assertThat(foundSession.getLastUsedAt()).isNull();
    assertThat(foundSession.getCreatedAt()).isNotNull();

    assertThat(foundDevice.getOwnerId()).isEqualTo(userId);
    assertThat(foundDevice.getDeviceType()).isEqualTo(DeviceType.UMI_CAMERA);
    assertThat(foundDevice.getConnectionStatus()).isEqualTo(DeviceConnectionStatus.OFFLINE);
    assertThat(foundDevice.getFirmwareVersion()).isNull();
    assertThat(foundDevice.getRuntimeVersion()).isNull();
    assertThat(foundDevice.getLastSeenAt()).isNull();
    assertThat(foundDevice.getCreatedAt()).isNotNull();
    assertThat(foundDevice.getUpdatedAt()).isNotNull();

    assertThat(foundCredential.getDeviceId()).isEqualTo(deviceId);
    assertThat(foundCredential.getStatus()).isEqualTo(DeviceCredentialStatus.ACTIVE);
    assertThat(foundCredential.getEnrolledAt()).isEqualTo(enrolledAt);
    assertThat(foundCredential.getLastAuthenticatedAt()).isNull();
    assertThat(foundCredential.getSecretRotatedAt()).isNull();
    assertThat(foundCredential.getCreatedAt()).isNotNull();
    assertThat(foundCredential.getUpdatedAt()).isNotNull();

    Object databaseDeviceType =
        entityManager
            .createNativeQuery("SELECT device_type FROM devices WHERE id = :id")
            .setParameter("id", deviceId)
            .getSingleResult();
    assertThat(databaseDeviceType).isEqualTo("UMI_CAMERA");
  }

  @AfterTransaction
  void insertedRowsAreRolledBack() {
    if (userId == null) {
      return;
    }

    assertThat(count("SELECT count(*) FROM refresh_sessions WHERE id = ?", sessionId)).isZero();
    assertThat(count("SELECT count(*) FROM device_credentials WHERE device_id = ?", deviceId))
        .isZero();
    assertThat(count("SELECT count(*) FROM devices WHERE id = ?", deviceId)).isZero();
    assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
  }

  private long count(String sql, UUID id) {
    Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
    return count == null ? 0L : count;
  }
}
