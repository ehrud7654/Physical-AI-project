package com.anssa8bu.ssafy.common.idempotency;

import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IdempotencyGuard {
  private static final Duration RETENTION = Duration.ofHours(24);
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public IdempotencyGuard(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Claim claim(UUID actorId, String method, String path, String key, Object request) {
    return claim("USER", actorId, method, path, key, request);
  }

  public Claim claim(
      String actorType, UUID actorId, String method, String path, String key, Object request) {
    if (key == null || key.isBlank() || key.length() > 200) {
      throw new IllegalArgumentException(
          "Idempotency-Key is required and must not exceed 200 characters");
    }
    String lockKey = actorId + ":" + method + ":" + path + ":" + key;
    jdbc.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, lockKey);
    String requestHash = sha256(request);
    Optional<Existing> existing =
        jdbc.query(
            "select request_hash, resource_id from idempotency_keys where actor_type=? and actor_id=? and http_method=? and request_path=? and idempotency_key=? and expires_at>now()",
            (ResultSet rs) ->
                rs.next()
                    ? Optional.of(new Existing(rs.getString(1), rs.getString(2)))
                    : Optional.empty(),
            actorType,
            actorId.toString(),
            method,
            path,
            key);
    if (existing.isPresent()) {
      if (!existing.get().requestHash().equals(requestHash)) {
        throw new BusinessConflictException(
            "IDEMPOTENCY_KEY_REUSED", "같은 멱등성 키에 다른 요청 본문을 사용할 수 없습니다.");
      }
      return new Claim(
          key,
          requestHash,
          existing.get().resourceId() == null
              ? null
              : UUID.fromString(existing.get().resourceId()));
    }
    return new Claim(key, requestHash, null);
  }

  public void complete(
      UUID actorId,
      String method,
      String path,
      Claim claim,
      int responseStatus,
      String resourceType,
      UUID resourceId) {
    complete("USER", actorId, method, path, claim, responseStatus, resourceType, resourceId);
  }

  public void complete(
      String actorType,
      UUID actorId,
      String method,
      String path,
      Claim claim,
      int responseStatus,
      String resourceType,
      UUID resourceId) {
    jdbc.update(
        "insert into idempotency_keys(id,actor_type,actor_id,http_method,request_path,idempotency_key,request_hash,response_status,response_body,resource_type,resource_id,expires_at) values (?,?,?,?,?,?,?,?,'{}'::jsonb,?,?,?)",
        UUID.randomUUID(),
        actorType,
        actorId.toString(),
        method,
        path,
        claim.key(),
        claim.requestHash(),
        responseStatus,
        resourceType,
        resourceId.toString(),
        Timestamp.from(Instant.now().plus(RETENTION)));
  }

  private String sha256(Object request) {
    try {
      byte[] json = objectMapper.writeValueAsBytes(request);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (Exception e) {
      throw new IllegalStateException("request hash calculation failed", e);
    }
  }

  public record Claim(String key, String requestHash, UUID existingResourceId) {}

  private record Existing(String requestHash, String resourceId) {}
}
