package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import com.anssa8bu.ssafy.common.redis.dto.DevicePresence;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DevicePresenceRepository extends JsonRedisRepository<DevicePresence> {
  private final RedisKeyGenerator keys;

  public DevicePresenceRepository(StringRedisTemplate r, RedisJsonCodec c, RedisKeyGenerator k) {
    super(r, c, DevicePresence.class);
    keys = k;
  }

  protected String key(UUID id) {
    return keys.devicePresence(id);
  }
}
