package com.anssa8bu.ssafy.episodevalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anssa8bu.ssafy.episodeupload.entity.EpisodeUploadSession;
import com.anssa8bu.ssafy.episodevalidation.exception.EpisodeArchiveValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class EpisodeArchiveValidatorTest {
  private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final EpisodeArchiveValidator validator = new EpisodeArchiveValidator(objectMapper);

  @Test
  void validatesCurrentUmiArchiveContract() throws Exception {
    byte[] zip = archive(1_100_000L);

    var result = validator.validate(new ByteArrayInputStream(zip), session(), 10_000_000);

    assertThat(result.frameCount()).isEqualTo(1);
    assertThat(result.poseSampleCount()).isZero();
    assertThat(result.gripperSampleCount()).isZero();
    assertThat(result.imuSampleCount()).isEqualTo(4);
    assertThat(result.fileSha256()).containsKeys("manifest.json", "video.mp4");
    assertThat(result.qualityDetails()).containsEntry("encodedSampleCount", 1);
  }

  @Test
  void rejectsCameraAndEncoderTimestampMismatch() throws Exception {
    byte[] zip = archive(1_100_001L);

    assertThatThrownBy(
            () -> validator.validate(new ByteArrayInputStream(zip), session(), 10_000_000))
        .isInstanceOfSatisfying(
            EpisodeArchiveValidationException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("CAMERA_PTS_MISMATCH"));
  }

  private EpisodeUploadSession session() {
    return new EpisodeUploadSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        SESSION_ID,
        1_000,
        "a".repeat(64),
        Map.ofEntries(
            Map.entry("contractVersion", "umi-episode-v2"),
            Map.entry("schemaVersion", "2"),
            Map.entry("skillKey", "raise_arm"),
            Map.entry("source", "REAL"),
            Map.entry("labelStatus", "LABELED"),
            Map.entry("success", true),
            Map.entry("startedAt", "2026-09-21T00:00:00Z"),
            Map.entry("endedAt", "2026-09-21T00:00:01Z"),
            Map.entry("startMonotonicNs", 1_000_000L),
            Map.entry("timestampTimebase", "MONOTONIC_NS"),
            Map.entry("device", Map.of()),
            Map.entry("camera", Map.of())),
        Map.of(
            "schemaVersion", "2",
            "firstTimestampNs", 1_100_000L,
            "lastTimestampNs", 1_100_000L,
            "frameCount", 1,
            "poseSampleCount", 0,
            "imuSampleCount", 4,
            "gripperSampleCount", 0,
            "missingFrameCount", 0,
            "trackingGapCount", 0,
            "trackingLostDurationNs", 0),
        "bucket",
        "key",
        Instant.now().plusSeconds(60));
  }

  private byte[] archive(long ptsUs) throws Exception {
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("manifest.json", objectMapper.writeValueAsBytes(manifest()));
    files.put(
        "frames.csv",
        bytes(
            "frame_number,sensor_timestamp_ns,exposure_ns,frame_duration_ns,iso,rolling_shutter_skew_ns,focus_diopters,af_mode,eis_mode,ois_mode,crop_left,crop_top,crop_right,crop_bottom\n"
                + "0,1100000000,1000,33333333,100,100,0.0,OFF,OFF,OFF,0,0,1920,1080\n"));
    files.put(
        "encoded.csv", bytes("sample_index,pts_us,size_bytes,flags\n0," + ptsUs + ",1024,1\n"));
    files.put(
        "accelerometer.csv",
        bytes(
            "timestamp_ns,x_m_s2,y_m_s2,z_m_s2,accuracy\n"
                + "1050000000,0,0,9.8,3\n1150000000,0,0,9.8,3\n"));
    files.put(
        "gyroscope.csv",
        bytes(
            "timestamp_ns,x_rad_s,y_rad_s,z_rad_s,accuracy\n"
                + "1050000000,0,0,0,3\n1150000000,0,0,0,3\n"));
    files.put("video.mp4", new byte[] {0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      for (Map.Entry<String, byte[]> file : files.entrySet()) {
        CRC32 crc = new CRC32();
        crc.update(file.getValue());
        ZipEntry entry = new ZipEntry(file.getKey());
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(file.getValue().length);
        entry.setCompressedSize(file.getValue().length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(file.getValue());
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private Map<String, Object> manifest() {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", 2);
    manifest.put("app_version", "0.3");
    manifest.put(
        "camera",
        Map.of(
            "id",
            "2",
            "candidates",
            java.util.List.of(Map.of("id", "2")),
            "minimum_focus_distance",
            0,
            "sensor_orientation",
            90,
            "timestamp_source",
            "SENSOR",
            "focal_lengths_mm",
            java.util.List.of(2.2),
            "horizontal_fov_estimate",
            104.0));
    manifest.put("start_elapsed_ns", 1_000_000L);
    manifest.put("stop_elapsed_ns", 2_000_000_000L);
    manifest.put("width", 1920);
    manifest.put("height", 1080);
    manifest.put("image_rotation_degrees", 180);
    manifest.put("rotation_baked_into_pixels", true);
    manifest.put("decoder_rotation_degrees", 0);
    manifest.put("image_orientation_contract", "upright_v1");
    manifest.put("encoder_timestamp_base", "SENSOR");
    manifest.put("imu_axes", "ANDROID_DEVICE");
    manifest.put("imu_requested_hz", 200);
    manifest.put("imu_rotation_applied", false);
    manifest.put("accelerometer_name", "accelerometer");
    manifest.put("gyroscope_name", "gyroscope");
    manifest.put("gripper_left_id", 0);
    manifest.put("gripper_right_id", 1);
    manifest.put("marker_side_mm_user_reported", 16);
    manifest.put("marker_black_square_size_verified", true);
    manifest.put("task", "raise_arm");
    manifest.put("outcome", "success");
    manifest.put("training_ready", true);
    manifest.put("status", "recorded");
    manifest.put("error", null);
    manifest.put("calibration_id", "calibration-1");
    manifest.put("auto_exposure", false);
    manifest.put("auto_white_balance", false);
    manifest.put("device", Map.of("model", "test"));
    manifest.put("android", Map.of("sdk", 35));
    manifest.put("session_id", SESSION_ID.toString());
    return manifest;
  }

  private byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
