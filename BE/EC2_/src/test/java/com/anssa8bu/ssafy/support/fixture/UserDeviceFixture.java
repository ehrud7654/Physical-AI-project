package com.anssa8bu.ssafy.support.fixture;

import com.anssa8bu.ssafy.device.entity.Device;
import com.anssa8bu.ssafy.device.entity.DeviceConnectionStatus;
import com.anssa8bu.ssafy.device.entity.DeviceType;
import com.anssa8bu.ssafy.user.entity.User;
import com.anssa8bu.ssafy.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** FK를 만족하는 최소한의 사용자·장치 행. 호출자의 트랜잭션 안에서 persist만 하고 flush는 호출자가 결정한다. */
public final class UserDeviceFixture {
  private final EntityManager em;

  public UserDeviceFixture(EntityManager em) {
    this.em = em;
  }

  public User user() {
    UUID id = UUID.randomUUID();
    User user = new User(id, "fixture-" + id + "@test.local", "hash", "fixture", UserStatus.ACTIVE);
    em.persist(user);
    return user;
  }

  public Device umiCamera(UUID ownerId) {
    return device(ownerId, DeviceType.UMI_CAMERA);
  }

  public Device jetson(UUID ownerId) {
    return device(ownerId, DeviceType.JETSON);
  }

  public Device device(UUID ownerId, DeviceType type) {
    UUID id = UUID.randomUUID();
    Device device =
        new Device(id, ownerId, type, type + "-" + id, "fixture", DeviceConnectionStatus.OFFLINE);
    em.persist(device);
    return device;
  }
}
