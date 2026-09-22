package com.anssa8bu.ssafy.episodevalidation.contract;

import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class EpisodeCsvValidator {
  public CsvValidationResult validate(
      byte[] framesBytes,
      byte[] encodedBytes,
      byte[] accelerometerBytes,
      byte[] gyroscopeBytes,
      long startElapsedNs,
      long stopElapsedNs) {
    CsvTable frames = table(framesBytes, EpisodeArchiveContract.FRAME_HEADER, "frames.csv");
    CsvTable encoded = table(encodedBytes, EpisodeArchiveContract.ENCODED_HEADER, "encoded.csv");
    CsvTable accelerometer =
        table(accelerometerBytes, EpisodeArchiveContract.ACCELEROMETER_HEADER, "accelerometer.csv");
    CsvTable gyroscope =
        table(gyroscopeBytes, EpisodeArchiveContract.GYROSCOPE_HEADER, "gyroscope.csv");

    if (frames.rows().isEmpty() || encoded.rows().isEmpty()) {
      fail("CSV_EMPTY", "frames.csv와 encoded.csv에는 데이터 행이 필요합니다.");
    }
    if (frames.rows().size() != encoded.rows().size()) {
      fail("VIDEO_FRAME_COUNT_MISMATCH", "frames.csv와 encoded.csv 행 수가 일치하지 않습니다.");
    }
    if (accelerometer.rows().size() < 2 || gyroscope.rows().size() < 2) {
      fail("IMU_SAMPLE_INSUFFICIENT", "가속도계와 자이로 데이터는 각각 2행 이상이어야 합니다.");
    }

    long previousFrameNs = -1;
    long firstFrameNs = -1;
    long lastFrameNs = -1;
    int keyframeCount = 0;
    for (int index = 0; index < frames.rows().size(); index++) {
      String[] frame = frames.rows().get(index);
      String[] encodedRow = encoded.rows().get(index);
      requireSequential(frame[0], index, "frames.csv frame_number");
      requireSequential(encodedRow[0], index, "encoded.csv sample_index");
      long sensorTimestampNs = positiveLong(frame[1], "frames.csv sensor_timestamp_ns", false);
      long ptsUs = positiveLong(encodedRow[1], "encoded.csv pts_us", false);
      if (sensorTimestampNs <= previousFrameNs) {
        fail("TIMESTAMP_NOT_MONOTONIC", "frames.csv sensor_timestamp_ns가 단조 증가하지 않습니다.");
      }
      if (ptsUs != sensorTimestampNs / 1_000L) {
        fail(
            "CAMERA_PTS_MISMATCH",
            "frames.csv sensor_timestamp_ns/1000과 encoded.csv pts_us가 일치하지 않습니다: "
                + (index + 2)
                + "번 행");
      }
      if (sensorTimestampNs < startElapsedNs || sensorTimestampNs > stopElapsedNs) {
        fail("TIMESTAMP_RANGE_MISMATCH", "카메라 타임스탬프가 세션 시간 범위를 벗어났습니다.");
      }
      positiveLong(frame[2], "frames.csv exposure_ns", true);
      positiveLong(frame[3], "frames.csv frame_duration_ns", false);
      positiveLong(frame[4], "frames.csv iso", false);
      positiveLong(frame[5], "frames.csv rolling_shutter_skew_ns", true);
      finite(frame[6], "frames.csv focus_diopters");
      requireDisabled(frame[7], "af_mode");
      requireDisabled(frame[8], "eis_mode");
      requireDisabled(frame[9], "ois_mode");
      int left = integer(frame[10], "crop_left");
      int top = integer(frame[11], "crop_top");
      int right = integer(frame[12], "crop_right");
      int bottom = integer(frame[13], "crop_bottom");
      if (left < 0 || top < 0 || right <= left || bottom <= top) {
        fail("CAMERA_CROP_INVALID", "frames.csv crop 영역이 올바르지 않습니다.");
      }
      positiveLong(encodedRow[2], "encoded.csv size_bytes", false);
      int flags = integer(encodedRow[3], "encoded.csv flags");
      if (flags < 0) fail("ENCODED_FLAGS_INVALID", "encoded.csv flags는 0 이상이어야 합니다.");
      if ((flags & 1) == 1) keyframeCount++;
      if (firstFrameNs < 0) firstFrameNs = sensorTimestampNs;
      lastFrameNs = sensorTimestampNs;
      previousFrameNs = sensorTimestampNs;
    }
    if (keyframeCount == 0) {
      fail("VIDEO_KEYFRAME_MISSING", "encoded.csv에 키프레임(flags bit 0)이 없습니다.");
    }

    SensorStats accelerometerStats =
        sensorStats(accelerometer, "accelerometer.csv", startElapsedNs, stopElapsedNs);
    SensorStats gyroscopeStats =
        sensorStats(gyroscope, "gyroscope.csv", startElapsedNs, stopElapsedNs);
    if (accelerometerStats.lastNs() < firstFrameNs
        || accelerometerStats.firstNs() > lastFrameNs
        || gyroscopeStats.lastNs() < firstFrameNs
        || gyroscopeStats.firstNs() > lastFrameNs) {
      fail("IMU_VIDEO_NO_OVERLAP", "IMU와 카메라 데이터의 시간 구간이 겹치지 않습니다.");
    }
    return new CsvValidationResult(
        frames.rows().size(),
        encoded.rows().size(),
        accelerometer.rows().size(),
        gyroscope.rows().size(),
        keyframeCount,
        firstFrameNs,
        lastFrameNs,
        accelerometerStats,
        gyroscopeStats);
  }

  private SensorStats sensorStats(CsvTable table, String name, long startNs, long stopNs) {
    long previous = -1;
    long first = -1;
    long last = -1;
    for (int index = 0; index < table.rows().size(); index++) {
      String[] row = table.rows().get(index);
      long timestamp = positiveLong(row[0], name + " timestamp_ns", false);
      if (timestamp <= previous) {
        fail("TIMESTAMP_NOT_MONOTONIC", name + " timestamp_ns가 단조 증가하지 않습니다.");
      }
      if (timestamp < startNs || timestamp > stopNs) {
        fail("TIMESTAMP_RANGE_MISMATCH", name + " timestamp_ns가 세션 시간 범위를 벗어났습니다.");
      }
      finite(row[1], name + " x");
      finite(row[2], name + " y");
      finite(row[3], name + " z");
      integer(row[4], name + " accuracy");
      if (first < 0) first = timestamp;
      last = timestamp;
      previous = timestamp;
    }
    double observedHz =
        last == first ? 0D : (table.rows().size() - 1) * 1_000_000_000D / (last - first);
    return new SensorStats(first, last, observedHz);
  }

  private CsvTable table(byte[] bytes, List<String> expectedHeader, String name) {
    String content = new String(bytes, StandardCharsets.UTF_8).strip();
    if (content.isEmpty()) fail("CSV_EMPTY", name + "가 비어 있습니다.");
    String[] lines = content.split("\\R");
    List<String> actualHeader = Arrays.asList(lines[0].split(",", -1));
    if (!actualHeader.equals(expectedHeader)) {
      fail("CSV_HEADER_INVALID", name + " header 이름·순서·개수가 계약과 다릅니다.");
    }
    List<String[]> rows =
        Arrays.stream(lines)
            .skip(1)
            .map(line -> line.split(",", -1))
            .peek(
                row -> {
                  if (row.length != expectedHeader.size()) {
                    fail("CSV_COLUMN_INVALID", name + " column 개수가 계약과 다릅니다.");
                  }
                })
            .toList();
    return new CsvTable(rows);
  }

  private void requireSequential(String value, int expected, String field) {
    if (integer(value, field) != expected) {
      fail("SEQUENCE_INVALID", field + "가 0부터 연속 증가하지 않습니다.");
    }
  }

  private void requireDisabled(String value, String field) {
    String normalized = value.trim().toUpperCase();
    if (!normalized.equals("OFF") && !normalized.equals("0")) {
      fail("CAMERA_MODE_INVALID", field + "는 OFF여야 합니다.");
    }
  }

  private long positiveLong(String value, String field, boolean allowZero) {
    try {
      long parsed = Long.parseLong(value);
      if (allowZero ? parsed < 0 : parsed <= 0) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      fail("FIELD_INVALID", field + "가 올바른 정수가 아닙니다.");
      return 0;
    }
  }

  private int integer(String value, String field) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      fail("FIELD_INVALID", field + "가 정수가 아닙니다.");
      return 0;
    }
  }

  private double finite(String value, String field) {
    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed)) throw new NumberFormatException();
      return parsed;
    } catch (NumberFormatException exception) {
      fail("FIELD_INVALID", field + "가 유한한 숫자가 아닙니다.");
      return 0;
    }
  }

  private void fail(String code, String message) {
    throw new EpisodeArchiveValidationException(code, message);
  }

  private record CsvTable(List<String[]> rows) {}

  public record SensorStats(long firstNs, long lastNs, double observedHz) {}

  public record CsvValidationResult(
      int frameCount,
      int encodedSampleCount,
      int accelerometerSampleCount,
      int gyroscopeSampleCount,
      int keyframeCount,
      long firstFrameTimestampNs,
      long lastFrameTimestampNs,
      SensorStats accelerometer,
      SensorStats gyroscope) {}
}
