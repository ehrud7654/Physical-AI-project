package com.anssa8bu.ssafy.user.entity;

import com.anssa8bu.ssafy.device.entity.DeviceCredential;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDeviceEntityTest {

    @Test
    void deviceTypeConvertsDocumentedDatabaseValue() {
        assertThat(DeviceType.UMI_CAMERA.getDatabaseValue()).isEqualTo("UMI_Camera");
        assertThat(DeviceType.fromDatabaseValue("JETSON")).isEqualTo(DeviceType.JETSON);
        assertThatThrownBy(() -> DeviceType.fromDatabaseValue("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshSessionRequiresLowercaseSha256() {
        assertThatThrownBy(() -> new RefreshSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "invalid-hash",
                Instant.parse("2026-09-08T00:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenHash must be a lowercase SHA-256 value");
    }

    @Test
    void registrationCodeHashAndExpirationAreProvidedTogether() {
        assertThatThrownBy(() -> new DeviceCredential(
                UUID.randomUUID(),
                "device-secret-hash",
                "registration-code-hash",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registration code hash and expiration must be provided together");
    }
}
