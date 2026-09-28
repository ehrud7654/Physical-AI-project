package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.RedisJsonCodec;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class JsonRedisRepository<T> {
  protected final StringRedisTemplate redis;
  private final RedisJsonCodec codec;
  private final Class<T> type;

  protected JsonRedisRepository(StringRedisTemplate r, RedisJsonCodec c, Class<T> t) {
    redis = r;
    codec = c;
    type = t;
  }

  protected abstract String key(UUID id);

  public void save(UUID id, T value, Duration ttl) {
    redis.opsForValue().set(key(id), codec.encode(value), ttl);
  }

  public Optional<T> find(UUID id) {
    String json = redis.opsForValue().get(key(id));
    return json == null ? Optional.empty() : Optional.of(codec.decode(json, type));
  }

  public boolean delete(UUID id) {
    return Boolean.TRUE.equals(redis.delete(key(id)));
  }
}
