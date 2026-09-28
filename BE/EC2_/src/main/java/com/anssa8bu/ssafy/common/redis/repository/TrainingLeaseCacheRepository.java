package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import com.anssa8bu.ssafy.common.redis.dto.TrainingLeaseCache;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingLeaseCacheRepository extends JsonRedisRepository<TrainingLeaseCache> {
  private final RedisKeyGenerator keys;

  public TrainingLeaseCacheRepository(
      StringRedisTemplate r, RedisJsonCodec c, RedisKeyGenerator k) {
    super(r, c, TrainingLeaseCache.class);
    keys = k;
  }

  protected String key(UUID id) {
    return keys.trainingLease(id);
  }
}
