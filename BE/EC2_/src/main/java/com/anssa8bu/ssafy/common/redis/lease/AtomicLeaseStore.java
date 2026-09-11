package com.anssa8bu.ssafy.common.redis.lease;

import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AtomicLeaseStore {
  private static final DefaultRedisScript<Long> COMPARE_EXPIRE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end",
          Long.class);
  private static final DefaultRedisScript<Long> COMPARE_DELETE =
      new DefaultRedisScript<>(
          "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
          Long.class);
  private final StringRedisTemplate redis;

  public AtomicLeaseStore(StringRedisTemplate r) {
    redis = r;
  }

  public boolean acquire(String key, String owner, Duration ttl) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, ttl));
  }

  public boolean renew(String key, String owner, Duration ttl) {
    Long v = redis.execute(COMPARE_EXPIRE, List.of(key), owner, String.valueOf(ttl.toMillis()));
    return v != null && v == 1;
  }

  public boolean release(String key, String owner) {
    Long v = redis.execute(COMPARE_DELETE, List.of(key), owner);
    return v != null && v == 1;
  }
}
