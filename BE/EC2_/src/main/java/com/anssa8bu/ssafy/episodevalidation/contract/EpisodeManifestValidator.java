package com.anssa8bu.ssafy.episodevalidation.contract;

import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class EpisodeManifestValidator {
  private static final Set<String> OUTCOMES = Set.of("success", "failure", "unrated");

  public ManifestValidationResult validate(Map<String, Object> manifest) {
    String schemaVersion = String.valueOf(required(manifest, "schema_version"));
    text(manifest, "app_version");
    Map<String, Object> camera = object(manifest, "camera");
    text(camera, "id");
    list(camera, "candidates");
    if (decimal(camera, "minimum_focus_distance") != 0D) {
      fail("CAMERA_SELECTION_INVALID", "선택 카메라는 minimum_focus_distance가 0인 고정 초점이어야 합니다.");
    }
    integer(camera, "sensor_orientation");
    equal(camera, "timestamp_source", EpisodeArchiveContract.TIMESTAMP_SOURCE);
    if (list(camera, "focal_lengths_mm").isEmpty()) {
      fail("CAMERA_INTRINSICS_INVALID", "camera.focal_lengths_mm이 비어 있습니다.");
    }
    if (decimal(camera, "horizontal_fov_estimate") <= 0D) {
      fail("CAMERA_INTRINSICS_INVALID", "camera.horizontal_fov_estimate는 양수여야 합니다.");
    }

    long startNs = positiveLong(manifest, "start_elapsed_ns");
    long stopNs = positiveLong(manifest, "stop_elapsed_ns");
    if (stopNs <= startNs)
      fail("SESSION_TIME_INVALID", "stop_elapsed_ns는 start_elapsed_ns보다 커야 합니다.");

    int width = positiveInteger(manifest, "width");
    int height = positiveInteger(manifest, "height");
    if (width != 1920 || height != 1080) {
      fail("VIDEO_GEOMETRY_INVALID", "video 크기는 1920x1080이어야 합니다.");
    }
    equal(manifest, "image_rotation_degrees", EpisodeArchiveContract.IMAGE_ROTATION_DEGREES);
    equal(manifest, "rotation_baked_into_pixels", true);
    equal(manifest, "decoder_rotation_degrees", EpisodeArchiveContract.DECODER_ROTATION_DEGREES);
    equal(manifest, "image_orientation_contract", EpisodeArchiveContract.ORIENTATION_CONTRACT);
    equal(manifest, "encoder_timestamp_base", EpisodeArchiveContract.TIMESTAMP_SOURCE);

    text(manifest, "imu_axes");
    int requestedHz = positiveInteger(manifest, "imu_requested_hz");
    required(manifest, "imu_rotation_applied");
    text(manifest, "accelerometer_name");
    text(manifest, "gyroscope_name");
    equal(manifest, "gripper_left_id", 0);
    equal(manifest, "gripper_right_id", 1);
    if (decimal(manifest, "marker_side_mm_user_reported") <= 0D) {
      fail("MARKER_CONTRACT_INVALID", "marker_side_mm_user_reported는 양수여야 합니다.");
    }
    equal(manifest, "marker_black_square_size_verified", true);

    text(manifest, "task");
    String outcome = text(manifest, "outcome");
    if (!OUTCOMES.contains(outcome)) {
      fail("OUTCOME_INVALID", "outcome은 success, failure, unrated 중 하나여야 합니다.");
    }
    equal(manifest, "training_ready", true);
    equal(manifest, "status", "recorded");
    Object error = manifest.get("error");
    if (error != null && !error.toString().isBlank()) {
      fail("CAPTURE_REPORTED_ERROR", "manifest.error가 설정된 세션은 학습에 사용할 수 없습니다.");
    }
    text(manifest, "calibration_id");
    required(manifest, "auto_exposure");
    required(manifest, "auto_white_balance");
    object(manifest, "device");
    required(manifest, "android");

    return new ManifestValidationResult(
        schemaVersion, startNs, stopNs, requestedHz, outcome,
        Collections.unmodifiableMap(new LinkedHashMap<>(camera)));
  }

  private Object required(Map<String, Object> source, String key) {
    Object value = source.get(key);
    if (value == null) fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 필요합니다.");
    return value;
  }

  private String text(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof String string) || string.isBlank()) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 문자열이어야 합니다.");
    }
    return value.toString();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof Map<?, ?>)) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 object여야 합니다.");
    }
    return (Map<String, Object>) value;
  }

  private List<?> list(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof List<?> list)) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 배열이어야 합니다.");
      return List.of();
    }
    return list;
  }

  private long positiveLong(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof Number number) || number.longValue() < 0) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 0 이상의 정수여야 합니다.");
    }
    return ((Number) value).longValue();
  }

  private int positiveInteger(Map<String, Object> source, String key) {
    long value = positiveLong(source, key);
    if (value < 1 || value > Integer.MAX_VALUE) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 양의 정수여야 합니다.");
    }
    return (int) value;
  }

  private int integer(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof Number number)) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 정수여야 합니다.");
    }
    return ((Number) value).intValue();
  }

  private double decimal(Map<String, Object> source, String key) {
    Object value = required(source, key);
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      fail("MANIFEST_FIELD_INVALID", "manifest의 " + key + "가 유한한 숫자여야 합니다.");
    }
    return ((Number) value).doubleValue();
  }

  private void equal(Map<String, Object> source, String key, Object expected) {
    Object actual = required(source, key);
    boolean matches =
        actual instanceof Number actualNumber && expected instanceof Number expectedNumber
            ? Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue()) == 0
            : expected.equals(actual);
    if (!matches) {
      fail("MANIFEST_CONTRACT_MISMATCH", key + "는 " + expected + "이어야 합니다.");
    }
  }

  private void fail(String code, String message) {
    throw new EpisodeArchiveValidationException(code, message);
  }

  public record ManifestValidationResult(
      String schemaVersion,
      long startElapsedNs,
      long stopElapsedNs,
      int imuRequestedHz,
      String outcome,
      Map<String, Object> camera) {}
}
