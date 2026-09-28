package com.anssa8bu.ssafy.episodevalidation.application;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import com.anssa8bu.ssafy.episodevalidation.contract.EpisodeArchiveContract;
import com.anssa8bu.ssafy.episodevalidation.contract.EpisodeCsvValidator;
import com.anssa8bu.ssafy.episodevalidation.contract.EpisodeCsvValidator.CsvValidationResult;
import com.anssa8bu.ssafy.episodevalidation.contract.EpisodeManifestValidator;
import com.anssa8bu.ssafy.episodevalidation.contract.EpisodeManifestValidator.ManifestValidationResult;
import com.anssa8bu.ssafy.episodevalidation.dto.EpisodeValidationResult;
import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EpisodeArchiveValidator {
  private static final int MAX_ENTRIES = 64;
  private final ObjectMapper objectMapper;
  private final EpisodeManifestValidator manifestValidator = new EpisodeManifestValidator();
  private final EpisodeCsvValidator csvValidator = new EpisodeCsvValidator();

  public EpisodeArchiveValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public EpisodeValidationResult validate(
      InputStream source, EpisodeUploadSession session, long maxUncompressedBytes) {
    ArchiveContents archive = readArchive(source, maxUncompressedBytes);
    Map<String, Object> manifest = json(archive.files().get(EpisodeArchiveContract.MANIFEST));
    ManifestValidationResult manifestResult = manifestValidator.validate(manifest);
    CsvValidationResult csv =
        csvValidator.validate(
            archive.files().get(EpisodeArchiveContract.FRAMES),
            archive.files().get(EpisodeArchiveContract.ENCODED),
            archive.files().get(EpisodeArchiveContract.ACCELEROMETER),
            archive.files().get(EpisodeArchiveContract.GYROSCOPE),
            manifestResult.startElapsedNs(),
            manifestResult.stopElapsedNs());
    validateVideo(archive.files().get(EpisodeArchiveContract.VIDEO));
    validateRequestConsistency(session, manifest, manifestResult, csv);

    Map<String, Object> requestMetadata = session.getMetadata();
    String outcome = manifestResult.outcome();
    Boolean success =
        switch (outcome) {
          case "success" -> true;
          case "failure" -> false;
          default -> null;
        };
    String labelStatus = success == null ? "UNLABELED" : "LABELED";

    Map<String, Object> quality = new LinkedHashMap<>();
    quality.put("trainingReady", true);
    quality.put("captureStatus", "recorded");
    quality.put("outcome", outcome);
    quality.put("encodedSampleCount", csv.encodedSampleCount());
    quality.put("keyframeCount", csv.keyframeCount());
    quality.put("accelerometerSampleCount", csv.accelerometerSampleCount());
    quality.put("gyroscopeSampleCount", csv.gyroscopeSampleCount());
    quality.put("accelerometerObservedHz", csv.accelerometer().observedHz());
    quality.put("gyroscopeObservedHz", csv.gyroscope().observedHz());
    quality.put("imuRequestedHz", manifestResult.imuRequestedHz());
    quality.put("calibrationId", manifest.get("calibration_id"));

    Map<String, Object> coordinateSystem = new LinkedHashMap<>();
    coordinateSystem.put("timestampBase", "elapsedRealtimeNanos");
    coordinateSystem.put("imuAxes", manifest.get("imu_axes"));
    coordinateSystem.put("imuRotationApplied", manifest.get("imu_rotation_applied"));
    coordinateSystem.put("imageOrientation", manifest.get("image_orientation_contract"));

    return new EpisodeValidationResult(
        string(requestMetadata, "contractVersion"),
        manifestResult.schemaVersion(),
        manifestResult.schemaVersion(),
        string(requestMetadata, "skillKey"),
        success,
        labelStatus,
        Instant.parse(string(requestMetadata, "startedAt")),
        Instant.parse(string(requestMetadata, "endedAt")),
        manifestResult.startElapsedNs(),
        csv.firstFrameTimestampNs(),
        csv.lastFrameTimestampNs(),
        csv.frameCount(),
        0,
        csv.accelerometerSampleCount() + csv.gyroscopeSampleCount(),
        0,
        0,
        0,
        0,
        0,
        0,
        "VALID",
        object(manifest, "device"),
        manifestResult.camera(),
        Map.copyOf(coordinateSystem),
        Map.of("time", "ns", "acceleration", "m/s^2", "angularVelocity", "rad/s"),
        Map.copyOf(quality),
        // Valid manifests may contain optional JSON null values (for example error: null).
        Collections.unmodifiableMap(new LinkedHashMap<>(manifest)),
        archive.hashes(),
        archive.uncompressedBytes(),
        archive.entryCount());
  }

  private ArchiveContents readArchive(InputStream source, long maxUncompressedBytes) {
    Map<String, byte[]> files = new HashMap<>();
    Map<String, String> hashes = new LinkedHashMap<>();
    Set<String> names = new HashSet<>();
    int entries = 0;
    long total = 0;
    try (ZipInputStream zip = new ZipInputStream(source, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) fail("ZIP_ENTRY_LIMIT", "ZIP entry 개수 제한을 초과했습니다.");
        String name = entry.getName();
        validateEntryName(name);
        if (!names.add(name)) fail("ZIP_DUPLICATE_ENTRY", "중복 ZIP entry가 있습니다: " + name);
        if (entry.isDirectory()) continue;
        if (!EpisodeArchiveContract.REQUIRED_FILES.contains(name)) {
          fail("ZIP_ENTRY_NOT_ALLOWED", "허용되지 않은 ZIP entry입니다: " + name);
        }
        if (entry.getMethod() != ZipEntry.STORED) {
          fail("ZIP_COMPRESSION_NOT_ALLOWED", "모든 ZIP entry는 STORE 방식이어야 합니다.");
        }
        byte[] bytes = readBounded(zip, maxUncompressedBytes - total);
        total += bytes.length;
        files.put(name, bytes);
        hashes.put(name, sha256(bytes));
      }
    } catch (IOException exception) {
      throw new EpisodeArchiveValidationException("ZIP_READ_FAILED", "ZIP 파일을 읽을 수 없습니다.");
    }
    if (!files.keySet().equals(EpisodeArchiveContract.REQUIRED_FILES)) {
      Set<String> missing = new HashSet<>(EpisodeArchiveContract.REQUIRED_FILES);
      missing.removeAll(files.keySet());
      fail("ZIP_REQUIRED_ENTRY_MISSING", "필수 ZIP entry가 누락되었습니다: " + missing);
    }
    return new ArchiveContents(Map.copyOf(files), Map.copyOf(hashes), total, entries);
  }

  private void validateRequestConsistency(
      EpisodeUploadSession session,
      Map<String, Object> manifest,
      ManifestValidationResult manifestResult,
      CsvValidationResult csv) {
    Map<String, Object> metadata = session.getMetadata();
    Map<String, Object> summary = session.getSummary();
    if (longValue(metadata, "startMonotonicNs") != manifestResult.startElapsedNs()) {
      fail("METADATA_MISMATCH", "요청 startMonotonicNs와 manifest start_elapsed_ns가 다릅니다.");
    }
    if (manifest.containsKey("session_id")
        && !session.getSessionId().toString().equals(String.valueOf(manifest.get("session_id")))) {
      fail("SESSION_ID_MISMATCH", "요청과 manifest session_id가 다릅니다.");
    }
    // manifest.task is a capture label and is not assumed to be the backend skillKey.
    // The authenticated upload reservation remains the source of truth for skill ownership.
    compareOptional(summary, "frameCount", csv.frameCount());
    compareOptional(summary, "firstTimestampNs", csv.firstFrameTimestampNs());
    compareOptional(summary, "lastTimestampNs", csv.lastFrameTimestampNs());
  }

  private void compareOptional(Map<String, Object> source, String key, long actual) {
    Object expected = source.get(key);
    if (expected instanceof Number number && number.longValue() != actual) {
      fail("SUMMARY_MISMATCH", "요청 " + key + "와 실제 파일 값이 다릅니다.");
    }
  }

  private void validateVideo(byte[] video) {
    if (video.length < 12
        || video[4] != 'f'
        || video[5] != 't'
        || video[6] != 'y'
        || video[7] != 'p') {
      fail("VIDEO_CONTAINER_INVALID", "video.mp4가 유효한 ISO BMFF/MP4 파일이 아닙니다.");
    }
  }

  private byte[] readBounded(InputStream input, long remaining) throws IOException {
    if (remaining < 0) fail("ZIP_SIZE_LIMIT", "압축 해제 크기 제한을 초과했습니다.");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    long total = 0;
    while ((read = input.read(buffer)) != -1) {
      total += read;
      if (total > remaining) fail("ZIP_SIZE_LIMIT", "압축 해제 크기 제한을 초과했습니다.");
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private void validateEntryName(String name) {
    if (name == null
        || name.isBlank()
        || name.startsWith("/")
        || name.startsWith("\\")
        || name.contains("\\")
        || name.matches("^[A-Za-z]:.*")
        || List.of(name.split("/")).contains("..")) {
      fail("ZIP_PATH_INVALID", "안전하지 않은 ZIP entry 경로입니다.");
    }
    if (name.toLowerCase().endsWith(".zip")) {
      fail("NESTED_ZIP_NOT_ALLOWED", "중첩 ZIP은 허용하지 않습니다.");
    }
  }

  private Map<String, Object> json(byte[] bytes) {
    try {
      return objectMapper.readValue(bytes, new TypeReference<>() {});
    } catch (RuntimeException exception) {
      throw new EpisodeArchiveValidationException("JSON_INVALID", "manifest.json 형식이 올바르지 않습니다.");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Map<String, Object> source, String key) {
    Object value = source.get(key);
    if (!(value instanceof Map<?, ?>)) fail("FIELD_INVALID", key + "가 object여야 합니다.");
    return (Map<String, Object>) value;
  }

  private String string(Map<String, Object> source, String key) {
    Object value = source.get(key);
    if (!(value instanceof String text) || text.isBlank()) fail("FIELD_INVALID", key + "가 필요합니다.");
    return value.toString();
  }

  private long longValue(Map<String, Object> source, String key) {
    Object value = source.get(key);
    if (!(value instanceof Number number)) fail("FIELD_INVALID", key + "가 숫자여야 합니다.");
    return ((Number) value).longValue();
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void fail(String code, String message) {
    throw new EpisodeArchiveValidationException(code, message);
  }

  private record ArchiveContents(
      Map<String, byte[]> files,
      Map<String, String> hashes,
      long uncompressedBytes,
      int entryCount) {}
}
