package com.anssa8bu.ssafy.episodevalidation.contract;

import java.util.List;
import java.util.Set;

public final class EpisodeArchiveContract {
  public static final String MANIFEST = "manifest.json";
  public static final String FRAMES = "frames.csv";
  public static final String ENCODED = "encoded.csv";
  public static final String ACCELEROMETER = "accelerometer.csv";
  public static final String GYROSCOPE = "gyroscope.csv";
  public static final String VIDEO = "video.mp4";

  public static final Set<String> REQUIRED_FILES =
      Set.of(MANIFEST, FRAMES, ENCODED, ACCELEROMETER, GYROSCOPE, VIDEO);

  public static final List<String> FRAME_HEADER =
      List.of(
          "frame_number",
          "sensor_timestamp_ns",
          "exposure_ns",
          "frame_duration_ns",
          "iso",
          "rolling_shutter_skew_ns",
          "focus_diopters",
          "af_mode",
          "eis_mode",
          "ois_mode",
          "crop_left",
          "crop_top",
          "crop_right",
          "crop_bottom");
  public static final List<String> ENCODED_HEADER =
      List.of("sample_index", "pts_us", "size_bytes", "flags");
  public static final List<String> ACCELEROMETER_HEADER =
      List.of("timestamp_ns", "x_m_s2", "y_m_s2", "z_m_s2", "accuracy");
  public static final List<String> GYROSCOPE_HEADER =
      List.of("timestamp_ns", "x_rad_s", "y_rad_s", "z_rad_s", "accuracy");

  public static final String TIMESTAMP_SOURCE = "SENSOR";
  public static final String ORIENTATION_CONTRACT = "upright_v1";
  public static final int IMAGE_ROTATION_DEGREES = 180;
  public static final int DECODER_ROTATION_DEGREES = 0;

  private EpisodeArchiveContract() {}
}
