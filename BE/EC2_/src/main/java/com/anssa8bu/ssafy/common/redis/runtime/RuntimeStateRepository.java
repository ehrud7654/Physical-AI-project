package com.anssa8bu.ssafy.common.redis.runtime;

import com.anssa8bu.ssafy.common.redis.RedisJsonCodec;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Timestamp and payload are one expiring hash, updated atomically and monotonically. */
@Repository
public class RuntimeStateRepository {
  private static final DefaultRedisScript<Long> SAVE =
      new DefaultRedisScript<>(
          """
      local previous = redis.call('hget', KEYS[1], 'observedAt')
      if previous and tonumber(previous) >= tonumber(ARGV[1]) then return 0 end
      redis.call('hset', KEYS[1], 'observedAt', ARGV[1], 'payload', ARGV[2])
      redis.call('pexpire', KEYS[1], ARGV[3])
      return 1
      """,
          Long.class);
  private final StringRedisTemplate redis;
  private final RedisJsonCodec codec;
  private final Clock clock;

  public RuntimeStateRepository(StringRedisTemplate redis, RedisJsonCodec codec, Clock clock) {
    this.redis = redis;
    this.codec = codec;
    this.clock = clock;
  }

  public void save(String key, Object state, Instant observedAt, Duration ttl) {
    // A delayed request must not receive a fresh full TTL at write time.
    long remaining = Duration.between(clock.instant(), observedAt.plus(ttl)).toMillis();
    if (remaining <= 0) return;
    try {
      redis.execute(
          SAVE,
          List.of(key),
          Long.toString(observedAt.toEpochMilli()),
          codec.encode(state),
          Long.toString(remaining));
    } catch (RuntimeException error) {
      throw new RuntimeStateUnavailableException(error);
    }
  }

  public <T> Optional<T> find(String key, Class<T> type) {
    try {
      Object value = redis.opsForHash().get(key, "payload");
      return value == null ? Optional.empty() : Optional.of(codec.decode(value.toString(), type));
    } catch (RuntimeException error) {
      throw new RuntimeStateUnavailableException(error);
    }
  }
}
