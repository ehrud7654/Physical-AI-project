package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyCacheRepository {
  private final StringRedisTemplate redis;
  private final RedisKeyGenerator keys;

  public IdempotencyCacheRepository(StringRedisTemplate r, RedisKeyGenerator k) {
    redis = r;
    keys = k;
  }

  public boolean reserve(String actor, String key, String requestHash, Duration ttl) {
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(keys.idempotency(actor, key), requestHash, ttl));
  }

  public Optional<String> find(String actor, String key) {
    return Optional.ofNullable(redis.opsForValue().get(keys.idempotency(actor, key)));
  }
}
