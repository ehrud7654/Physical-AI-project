package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import com.anssa8bu.ssafy.common.redis.dto.SessionCache;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.time.Duration;
import java.util.List;

@Repository
public class SessionCacheRepository extends JsonRedisRepository<SessionCache> {
  private final RedisKeyGenerator keys;
  private final RedisJsonCodec codec;
  private static final DefaultRedisScript<Long> REPLACE = new DefaultRedisScript<>(
      "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end "
          + "redis.call('SET',KEYS[1],ARGV[2],'PX',ARGV[3]); return 1", Long.class);
  private static final DefaultRedisScript<Long> DELETE_MATCHING = new DefaultRedisScript<>(
      "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end "
          + "return redis.call('DEL',KEYS[1])", Long.class);

  public SessionCacheRepository(StringRedisTemplate r, RedisJsonCodec c, RedisKeyGenerator k) {
    super(r, c, SessionCache.class);
    keys = k;
    codec = c;
  }

  protected String key(UUID id) {
    return keys.session(id);
  }

  public boolean replace(SessionCache expected, SessionCache updated, Duration ttl) {
    if (ttl.isNegative() || ttl.isZero()) return false;
    return Long.valueOf(1).equals(redis.execute(REPLACE, List.of(key(expected.sessionId())),
        codec.encode(expected), codec.encode(updated), Long.toString(Math.max(1, ttl.toMillis()))));
  }

  public boolean deleteMatching(SessionCache expected) {
    return Long.valueOf(1).equals(redis.execute(DELETE_MATCHING,
        List.of(key(expected.sessionId())), codec.encode(expected)));
  }
}
