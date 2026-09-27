package com.anssa8bu.ssafy.episode.vo;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record CaptureMetadata(
    Map<String, Object> device,
    Map<String, Object> camera,
    Map<String, Object> coordinateSystem,
    Map<String, Object> units) {
  public CaptureMetadata {
    device = copyMetadata(device);
    camera = copyMetadata(camera);
    coordinateSystem = copyMetadata(coordinateSystem);
    units = copyMetadata(units);
  }

  private static Map<String, Object> copyMetadata(Map<String, Object> source) {
    // Keep optional JSON null values while taking a read-only snapshot of the map.
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
