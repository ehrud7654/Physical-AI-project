package com.anssa8bu.ssafy.episodevalidation.application;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import com.anssa8bu.ssafy.episodevalidation.dto.EpisodeValidationResult;
import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class EpisodeArchiveValidator {
  private static final Set<String> REQUIRED =
      Set.of("meta.json", "summary.json", "frames.csv", "pose.csv", "imu.csv", "gripper.csv");
  private static final int MAX_ENTRIES = 100_000;
  private final ObjectMapper objectMapper;

  public EpisodeArchiveValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public EpisodeValidationResult validate(
      InputStream source, EpisodeUploadSession session, long maxUncompressedBytes) {
    Map<String, byte[]> files = new HashMap<>();
    Map<String, String> hashes = new LinkedHashMap<>();
    Set<String> names = new HashSet<>();
    int entryCount = 0;
    int frameEntryCount = 0;
    long totalBytes = 0;
    try (ZipInputStream zip = new ZipInputStream(source, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entryCount++;
        if (entryCount > MAX_ENTRIES) fail("ZIP_ENTRY_LIMIT", "ZIP entry 개수 제한을 초과했습니다.");
        String name = entry.getName();
        validateEntryName(name);
        if (!names.add(name)) fail("ZIP_DUPLICATE_ENTRY", "중복 ZIP entry가 있습니다: " + name);
        if (entry.isDirectory()) continue;
        if (entry.getMethod() != ZipEntry.STORED)
          fail("ZIP_COMPRESSION_NOT_ALLOWED", "모든 ZIP entry는 STORE 방식이어야 합니다.");
        byte[] bytes = readBounded(zip, maxUncompressedBytes - totalBytes);
        totalBytes += bytes.length;
        hashes.put(name, sha256(bytes));
        if (REQUIRED.contains(name)) files.put(name, bytes);
        if (name.startsWith("frames/") && name.endsWith(".jpg")) frameEntryCount++;
      }
    } catch (IOException exception) {
      throw new EpisodeArchiveValidationException("ZIP_READ_FAILED", "ZIP 파일을 읽을 수 없습니다.");
    }
    if (!files.keySet().containsAll(REQUIRED)) {
      fail("ZIP_REQUIRED_ENTRY_MISSING", "필수 ZIP entry가 누락되었습니다.");
    }
    if (frameEntryCount < 1) fail("FRAME_ENTRY_MISSING", "frames 디렉터리에 JPEG가 없습니다.");

    Map<String, Object> meta = json(files.get("meta.json"), "meta.json");
    Map<String, Object> summary = json(files.get("summary.json"), "summary.json");
    validateIdentity(meta, summary, session);
    validateLabelsAndQuality(meta, summary);

    CsvResult frames = validateFramesCsv(files.get("frames.csv"), names);
    CsvResult pose = validateTimedCsv(files.get("pose.csv"), "t_ns,px,py,pz,qw,qx,qy,qz,tracking_state");
    CsvResult imu = validateTimedCsv(files.get("imu.csv"), "t_ns,ax,ay,az,gx,gy,gz");
    CsvResult gripper = validateTimedCsv(files.get("gripper.csv"), "t_ns,openness,tracking_state");
    validateCountsAndTimes(summary, frames, pose, imu, gripper, frameEntryCount);
    validateInternalChecksums(summary, hashes);

    return new EpisodeValidationResult(
        text(meta, "contract_version"), text(meta, "schema_version"), text(summary, "schema_version"),
        text(meta, "skill_key"), nullableBoolean(meta, "success"), text(meta, "label_status"),
        instant(meta, "started_at"), instant(meta, "ended_at"), number(meta, "start_monotonic_ns"),
        number(summary, "first_timestamp_ns"), number(summary, "last_timestamp_ns"),
        integer(summary, "frame_count"), integer(summary, "pose_sample_count"),
        integer(summary, "imu_sample_count"), integer(summary, "gripper_sample_count"),
        integer(summary, "missing_frame_count"), integer(summary, "tracking_gap_count"),
        number(summary, "tracking_lost_duration_ns"), text(summary, "quality_status"),
        map(meta, "device"), map(meta, "camera"), optionalMap(meta, "coordinate_system"),
        optionalMap(meta, "units"), Map.copyOf(hashes), totalBytes, entryCount);
  }

  private void validateIdentity(
      Map<String, Object> meta, Map<String, Object> summary, EpisodeUploadSession session) {
    UUID metaSession = uuid(meta, "session_id");
    UUID summarySession = uuid(summary, "session_id");
    if (!session.getSessionId().equals(metaSession) || !metaSession.equals(summarySession))
      fail("SESSION_ID_MISMATCH", "요청과 ZIP 내부 session_id가 일치하지 않습니다.");
    Map<String, Object> requested = session.getMetadata();
    if (!text(meta, "skill_key").equals(requested.get("skillKey"))
        || !instant(meta, "started_at").toString().equals(requested.get("startedAt"))
        || !instant(meta, "ended_at").toString().equals(requested.get("endedAt"))
        || number(meta, "start_monotonic_ns")
            != ((Number) requested.get("startMonotonicNs")).longValue())
      fail("METADATA_MISMATCH", "요청 메타데이터와 meta.json이 일치하지 않습니다.");
    Map<String, Object> requestedSummary = session.getSummary();
    assertRequestedNumber(summary, requestedSummary, "first_timestamp_ns", "firstTimestampNs");
    assertRequestedNumber(summary, requestedSummary, "last_timestamp_ns", "lastTimestampNs");
    assertRequestedNumber(summary, requestedSummary, "frame_count", "frameCount");
    assertRequestedNumber(summary, requestedSummary, "pose_sample_count", "poseSampleCount");
    assertRequestedNumber(summary, requestedSummary, "imu_sample_count", "imuSampleCount");
    assertRequestedNumber(summary, requestedSummary, "gripper_sample_count", "gripperSampleCount");
  }

  private void assertRequestedNumber(
      Map<String, Object> actual, Map<String, Object> requested, String actualKey, String requestKey) {
    Object requestedValue = requested.get(requestKey);
    if (!(requestedValue instanceof Number)
        || number(actual, actualKey) != ((Number) requestedValue).longValue())
      fail("SUMMARY_MISMATCH", "요청 summary와 summary.json이 일치하지 않습니다.");
  }

  private void validateLabelsAndQuality(Map<String, Object> meta, Map<String, Object> summary) {
    if (!"REAL".equals(text(meta, "source")))
      fail("SOURCE_INVALID", "UMI 업로드 source는 REAL이어야 합니다.");
    String labelStatus = text(meta, "label_status");
    Boolean success = nullableBoolean(meta, "success");
    if (("LABELED".equals(labelStatus) && success == null)
        || ("UNLABELED".equals(labelStatus) && success != null)
        || (!"LABELED".equals(labelStatus) && !"UNLABELED".equals(labelStatus)))
      fail("LABEL_INVALID", "label_status와 success 조합이 올바르지 않습니다.");
    String quality = text(summary, "quality_status");
    if ("INVALID".equals(quality))
      fail("QUALITY_INVALID", "수집 데이터 품질 상태가 INVALID입니다.");
    if (!"VALID".equals(quality) && !"WARNING".equals(quality))
      fail("QUALITY_STATUS_INVALID", "quality_status가 올바르지 않습니다.");
  }

  private void validateCountsAndTimes(
      Map<String, Object> summary,
      CsvResult frames,
      CsvResult pose,
      CsvResult imu,
      CsvResult gripper,
      int imageCount) {
    if (frames.count != integer(summary, "frame_count") || frames.count != imageCount
        || pose.count != integer(summary, "pose_sample_count")
        || imu.count != integer(summary, "imu_sample_count")
        || gripper.count != integer(summary, "gripper_sample_count"))
      fail("SAMPLE_COUNT_MISMATCH", "summary와 실제 파일의 sample 개수가 다릅니다.");
    long first = number(summary, "first_timestamp_ns");
    long last = number(summary, "last_timestamp_ns");
    for (CsvResult value : List.of(frames, pose, imu, gripper)) {
      if (value.first < first || value.last > last)
        fail("TIMESTAMP_RANGE_MISMATCH", "CSV timestamp가 summary 범위를 벗어났습니다.");
    }
  }

  private CsvResult validateFramesCsv(byte[] bytes, Set<String> entries) {
    String[] lines = lines(bytes);
    if (!lines[0].equals("frame_index,t_ns,file_name"))
      fail("CSV_HEADER_INVALID", "frames.csv header가 올바르지 않습니다.");
    long previous = -1;
    long first = -1;
    for (int index = 1; index < lines.length; index++) {
      String[] columns = lines[index].split(",", -1);
      if (columns.length != 3 || Integer.parseInt(columns[0]) != index - 1)
        fail("FRAME_INDEX_INVALID", "frame_index가 0부터 연속적이지 않습니다.");
      long timestamp = parseLong(columns[1], "frames.csv t_ns");
      if (timestamp <= previous) fail("TIMESTAMP_NOT_MONOTONIC", "frames.csv t_ns가 단조 증가하지 않습니다.");
      String filename = columns[2];
      if (filename.contains("/") || !entries.contains("frames/" + filename))
        fail("FRAME_FILE_MISSING", "frames.csv가 존재하지 않는 JPEG를 참조합니다.");
      if (first < 0) first = timestamp;
      previous = timestamp;
    }
    if (lines.length < 2) fail("CSV_EMPTY", "frames.csv에 데이터가 없습니다.");
    return new CsvResult(lines.length - 1, first, previous);
  }

  private CsvResult validateTimedCsv(byte[] bytes, String header) {
    String[] lines = lines(bytes);
    if (!lines[0].equals(header)) fail("CSV_HEADER_INVALID", header + " 계약과 일치하지 않습니다.");
    long previous = -1;
    long first = -1;
    int expectedColumns = header.split(",", -1).length;
    for (int index = 1; index < lines.length; index++) {
      String[] columns = lines[index].split(",", -1);
      if (columns.length != expectedColumns)
        fail("CSV_COLUMN_INVALID", "CSV column 개수가 계약과 다릅니다.");
      long timestamp = parseLong(columns[0], "CSV t_ns");
      if (timestamp <= previous) fail("TIMESTAMP_NOT_MONOTONIC", "CSV t_ns가 단조 증가하지 않습니다.");
      int numericEnd = header.endsWith("tracking_state") ? columns.length - 1 : columns.length;
      for (int column = 1; column < numericEnd; column++) parseFinite(columns[column]);
      if (header.startsWith("t_ns,openness")) {
        double openness = parseFinite(columns[1]);
        if (openness < 0 || openness > 1)
          fail("GRIPPER_RANGE_INVALID", "gripper openness는 0과 1 사이여야 합니다.");
      }
      if (header.startsWith("t_ns,px,py,pz")) {
        double qw = parseFinite(columns[4]);
        double qx = parseFinite(columns[5]);
        double qy = parseFinite(columns[6]);
        double qz = parseFinite(columns[7]);
        double norm = Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
        if (norm < 0.99 || norm > 1.01)
          fail("QUATERNION_INVALID", "pose quaternion은 정규화되어야 합니다.");
      }
      if (first < 0) first = timestamp;
      previous = timestamp;
    }
    if (lines.length < 2) fail("CSV_EMPTY", "CSV에 데이터가 없습니다.");
    return new CsvResult(lines.length - 1, first, previous);
  }

  private void validateInternalChecksums(Map<String, Object> summary, Map<String, String> actual) {
    Map<String, Object> expected = map(summary, "checksums");
    for (String name : List.of("meta.json", "frames.csv", "pose.csv", "imu.csv", "gripper.csv")) {
      if (!actual.get(name).equals(expected.get(name)))
        fail("INTERNAL_CHECKSUM_MISMATCH", name + " checksum이 일치하지 않습니다.");
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
    if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
        || name.contains("\\") || name.matches("^[A-Za-z]:.*")
        || List.of(name.split("/")).contains(".."))
      fail("ZIP_PATH_INVALID", "안전하지 않은 ZIP entry 경로입니다.");
    if (name.toLowerCase().endsWith(".zip")) fail("NESTED_ZIP_NOT_ALLOWED", "중첩 ZIP은 허용하지 않습니다.");
  }

  private Map<String, Object> json(byte[] bytes, String name) {
    try {
      return objectMapper.readValue(bytes, new TypeReference<>() {});
    } catch (RuntimeException exception) {
      throw new EpisodeArchiveValidationException("JSON_INVALID", name + " 형식이 올바르지 않습니다.");
    }
  }

  private String[] lines(byte[] bytes) {
    String value = new String(bytes, StandardCharsets.UTF_8).strip();
    if (value.isEmpty()) fail("CSV_EMPTY", "CSV가 비어 있습니다.");
    return value.split("\\R");
  }

  private String text(Map<String, Object> value, String key) {
    Object found = value.get(key);
    if (!(found instanceof String text) || text.isBlank()) fail("FIELD_INVALID", key + "가 필요합니다.");
    return (String) found;
  }
  private long number(Map<String, Object> value, String key) {
    Object found = value.get(key);
    if (!(found instanceof Number number)) fail("FIELD_INVALID", key + "가 숫자여야 합니다.");
    return ((Number) found).longValue();
  }
  private int integer(Map<String, Object> value, String key) { return Math.toIntExact(number(value, key)); }
  private Instant instant(Map<String, Object> value, String key) {
    try { return Instant.parse(text(value, key)); }
    catch (RuntimeException exception) { fail("FIELD_INVALID", key + "가 UTC 시각이어야 합니다."); return null; }
  }
  private UUID uuid(Map<String, Object> value, String key) {
    try { return UUID.fromString(text(value, key)); }
    catch (RuntimeException exception) { fail("FIELD_INVALID", key + "가 UUID여야 합니다."); return null; }
  }
  private Boolean nullableBoolean(Map<String, Object> value, String key) {
    Object found = value.get(key);
    if (found == null) return null;
    if (!(found instanceof Boolean)) fail("FIELD_INVALID", key + "가 boolean이어야 합니다.");
    return (Boolean) found;
  }
  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Map<String, Object> value, String key) {
    Object found = value.get(key);
    if (!(found instanceof Map<?, ?>)) fail("FIELD_INVALID", key + "가 object여야 합니다.");
    return (Map<String, Object>) found;
  }
  private Map<String, Object> optionalMap(Map<String, Object> value, String key) {
    return value.get(key) == null ? Map.of() : map(value, key);
  }
  private long parseLong(String value, String field) {
    try { return Long.parseLong(value); }
    catch (NumberFormatException exception) { fail("FIELD_INVALID", field + "가 정수여야 합니다."); return -1; }
  }
  private double parseFinite(String value) {
    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed)) fail("NUMBER_NOT_FINITE", "NaN과 Infinity는 허용하지 않습니다.");
      return parsed;
    } catch (NumberFormatException exception) {
      fail("FIELD_INVALID", "CSV 수치 필드가 올바르지 않습니다.");
      return Double.NaN;
    }
  }
  private String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
  }
  private void fail(String code, String message) { throw new EpisodeArchiveValidationException(code, message); }
  private record CsvResult(int count, long first, long last) {}
}
