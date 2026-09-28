package com.anssa8bu.ssafy.common.redis.repository;

import com.anssa8bu.ssafy.common.redis.*;
import com.anssa8bu.ssafy.common.redis.dto.SseEventCache;
import java.time.Duration;
import java.util.*;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SseEventReplayRepository {
  private final StringRedisTemplate redis;
  private final RedisKeyGenerator keys;
  private final RedisJsonCodec codec;

  public SseEventReplayRepository(StringRedisTemplate r, RedisKeyGenerator k, RedisJsonCodec c) {
    redis = r;
    keys = k;
    codec = c;
  }

  public String append(UUID userId, SseEventCache event, Duration ttl, long maxLength) {
    String key = keys.sseEvents(userId);
    RecordId id =
        redis
            .opsForStream()
            .add(
                StreamRecords.newRecord()
                    .ofMap(Map.of("eventId", event.eventId(), "body", codec.encode(event)))
                    .withStreamKey(key));
    redis.opsForStream().trim(key, maxLength, true);
    redis.expire(key, ttl);
    return id.getValue();
  }

  public List<SseEventCache> after(UUID userId, String lastId) {
    String key = keys.sseEvents(userId);
    List<MapRecord<String, Object, Object>> rows =
        redis.opsForStream().range(key, Range.rightOpen(lastId == null ? "-" : lastId, "+"));
    if (rows == null) return List.of();
    return rows.stream()
        .filter(r -> lastId == null || !r.getId().getValue().equals(lastId))
        .map(r -> codec.decode(String.valueOf(r.getValue().get("body")), SseEventCache.class))
        .toList();
  }
}
