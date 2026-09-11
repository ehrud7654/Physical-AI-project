package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionCacheRepository extends JsonRedisRepository<SessionCache> {
  private final RedisKeyGenerator keys;

  public SessionCacheRepository(StringRedisTemplate r, RedisJsonCodec c, RedisKeyGenerator k) {
    super(r, c, SessionCache.class);
    keys = k;
  }

  protected String key(UUID id) {
    return keys.session(id);
  }
}
