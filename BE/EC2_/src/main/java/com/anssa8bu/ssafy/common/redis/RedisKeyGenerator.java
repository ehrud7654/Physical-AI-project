package com.anssa8bu.ssafy.common.redis;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyGenerator {
  private final String prefix;

  public RedisKeyGenerator(
      @Value("${app.redis.environment:${spring.profiles.active:default}}") String env,
      @Value("${spring.application.name:ssafy}") String app) {
    prefix = safe(env) + ":" + safe(app);
  }

  private static String safe(String v) {
    if (v == null || !v.matches("[a-zA-Z0-9_-]+"))
      throw new IllegalArgumentException("invalid redis key segment");
    return v;
  }

  private String key(String type, Object id) {
    return prefix + ":" + type + ":" + id;
  }

  public String session(UUID id) {
    return key("session", id);
  }

  public String trainingLease(UUID id) {
    return key("training-lease", id);
  }

  public String purgeLease(UUID id) {
    return key("purge-lease", id);
  }

  public String activeTrainingProfile() {
    return prefix + ":training-profile:active";
  }

  public String devicePresence(UUID id) {
    return key("device-presence", id);
  }

  public String sseEvents(UUID userId) {
    return key("sse-events", userId);
  }

  public String idempotency(String actor, String key) {
    return this.prefix + ":idempotency:" + safe(actor) + ":" + safe(key);
  }

  public String rateLimit(String actor, String operation) {
    return prefix + ":rate-limit:" + safe(actor) + ":" + safe(operation);
  }

  public String prefix() {
    return prefix;
  }
}
