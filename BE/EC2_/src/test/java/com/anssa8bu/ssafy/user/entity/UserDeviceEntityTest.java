package com.anssa8bu.ssafy.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import com.anssa8bu.ssafy.device.entity.DeviceCredentialStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserDeviceEntityTest {

  @Test
  void deviceTypeUsesDocumentedEnumNames() {
    assertThat(DeviceType.valueOf("UMI_CAMERA")).isEqualTo(DeviceType.UMI_CAMERA);
    assertThat(DeviceType.valueOf("JETSON")).isEqualTo(DeviceType.JETSON);
  }

  @Test
  void refreshSessionRequiresLowercaseSha256() {
    assertThatThrownBy(
            () ->
                new RefreshSession(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "invalid-hash",
                    Instant.parse("2026-09-08T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tokenHash must be a lowercase SHA-256 value");
  }

  @Test
  void newDeviceCredentialIsActive() {
    Instant enrolledAt = Instant.parse("2026-09-08T00:00:00Z");

    DeviceCredential credential =
        new DeviceCredential(UUID.randomUUID(), "device-secret-hash", enrolledAt);

    assertThat(credential.getStatus()).isEqualTo(DeviceCredentialStatus.ACTIVE);
    assertThat(credential.getEnrolledAt()).isEqualTo(enrolledAt);
    assertThat(credential.getLastAuthenticatedAt()).isNull();
  }
}
