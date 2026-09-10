package com.anssa8bu.ssafy.event.publisher;

import com.anssa8bu.ssafy.event.config.OutboxProperties;
import com.anssa8bu.ssafy.event.entity.OutboxEvent;
import java.util.*;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisSseEventDispatcher implements EventDispatcher {
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final OutboxProperties props;

  public RedisSseEventDispatcher(StringRedisTemplate r, ObjectMapper m, OutboxProperties p) {
    redis = r;
    mapper = m;
    props = p;
  }

  public void dispatch(OutboxEvent e) {
    Object owner = e.getPayload().get("ownerId");
    if (owner == null) throw new IllegalArgumentException("SSE event requires ownerId");
    String key = "sse:events:" + owner;
    String body = mapper.writeValueAsString(e.getPayload());
    redis
        .opsForStream()
        .add(
            StreamRecords.newRecord()
                .ofMap(
                    Map.of("eventId", e.getEventId(), "eventType", e.getEventType(), "body", body))
                .withStreamKey(key));
    redis.opsForStream().trim(key, props.sseMaxLength(), true);
    redis.expire(key, props.sseTtl());
  }
}
