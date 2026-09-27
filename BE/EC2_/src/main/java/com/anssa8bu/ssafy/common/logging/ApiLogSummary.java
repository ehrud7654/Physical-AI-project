package com.anssa8bu.ssafy.common.logging;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit allowlist: never serialize response bodies, credentials, URLs, or arbitrary maps. */
public final class ApiLogSummary {
  private static final Set<String> FIELDS =
      Set.of(
          "id",
          "userId",
          "deviceId",
          "umiDeviceId",
          "robotId",
          "episodeId",
          "uploadSessionId",
          "trainingJobId",
          "skillVersionId",
          "resultSkillVersionId",
          "taskId",
          "taskVersionId",
          "deploymentId",
          "executionId",
          "storageNodeId",
          "status",
          "jobStatus",
          "progressStage",
          "progressPercent",
          "version",
          "attempt",
          "episodeCount",
          "expectedEpisodeCount",
          "receivedEpisodeCount",
          "available",
          "accepted",
          "created",
          "trainable",
          "gatePassed",
          "cancelRequested",
          "expiresIn",
          "totalElements",
          "totalPages",
          "number",
          "size",
          "skillId",
          "enrollmentId",
          "deviceType",
          "valid",
          "readiness",
          "supported",
          "minimumEpisodeCount",
          "recommendedEpisodeCount",
          "maximumEpisodeCount",
          "defaultRetentionDays",
          "postTrainingGraceDays",
          "maxRetentionDays",
          "nextHeartbeatSeconds",
          "sessionExpiresAt",
          "absoluteExpiresAt",
          "expiresAt");
  private static final Set<String> NESTED = Set.of("user", "job", "node", "data");

  private ApiLogSummary() {}

  public static Object response(Object body) {
    return summarize(body instanceof ApiResponse<?> envelope ? envelope.data() : body, 0);
  }

  private static Object summarize(Object value, int depth) {
    if (value == null) return "empty";
    if (value instanceof Collection<?> collection) return Map.of("count", collection.size());
    if (!value.getClass().isRecord() || depth > 2) return "omitted";
    Map<String, Object> result = new LinkedHashMap<>();
    for (RecordComponent field : value.getClass().getRecordComponents()) {
      if (result.size() >= 12) break;
      boolean collection = Collection.class.isAssignableFrom(field.getType());
      if (!FIELDS.contains(field.getName()) && !NESTED.contains(field.getName()) && !collection) {
        continue;
      }
      try {
        Object item = field.getAccessor().invoke(value);
        if (item == null) continue;
        if (item instanceof Collection<?> items) {
          result.put(field.getName() + "Count", items.size());
        } else if (NESTED.contains(field.getName())) {
          result.put(field.getName(), summarize(item, depth + 1));
        } else if (item instanceof UUID
            || item instanceof Instant
            || item instanceof Number
            || item instanceof Boolean
            || item instanceof Enum<?>) {
          result.put(field.getName(), item);
        } else if (item instanceof String text) {
          result.put(field.getName(), safeText(text, 96));
        }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
        // Observability must not change the result of an API request.
      }
    }
    return result.isEmpty() ? "omitted" : result;
  }

  public static String safeText(String text, int limit) {
    if (text == null || text.isBlank()) return "unknown";
    String cleaned =
        text.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ")
            .replaceAll("(?i)https?://\\S+", "[URL]")
            .replaceAll("(?i)Bearer\\s+\\S+", "Bearer [REDACTED]")
            .replaceAll(
                "(?i)([\\w.-]*(?:token|secret|password|authorization|cookie|signature)[\\w.-]*[\\\"']?\\s*[:=]\\s*)[^,;\\s}]+",
                "$1[REDACTED]")
            .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[JWT]")
            .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[EMAIL]")
            .replaceAll("\\s+", " ")
            .trim();
    return cleaned.length() <= limit ? cleaned : cleaned.substring(0, limit) + "…";
  }

  public static String cause(Throwable error) {
    Throwable root = error;
    for (int i = 0; i < 20 && root.getCause() != null && root.getCause() != root; i++) {
      root = root.getCause();
    }
    // Arbitrary exception messages can contain SQL values, request bodies or credentials.
    // Record the exception type and application location; public error messages are logged
    // separately.
    String location = "";
    for (StackTraceElement frame : root.getStackTrace()) {
      if (frame.getClassName().startsWith("com.anssa8bu.ssafy.")) {
        location =
            " at "
                + frame.getClassName()
                + "."
                + frame.getMethodName()
                + ":"
                + frame.getLineNumber();
        break;
      }
    }
    return root.getClass().getSimpleName() + location;
  }
}
