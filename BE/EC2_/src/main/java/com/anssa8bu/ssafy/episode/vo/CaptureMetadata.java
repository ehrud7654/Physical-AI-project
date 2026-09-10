package com.anssa8bu.ssafy.episode.vo;

import java.util.Map;

public record CaptureMetadata(
    Map<String, Object> device,
    Map<String, Object> camera,
    Map<String, Object> coordinateSystem,
    Map<String, Object> units) {
  public CaptureMetadata {
    device = Map.copyOf(device);
    camera = Map.copyOf(camera);
    coordinateSystem = Map.copyOf(coordinateSystem);
    units = Map.copyOf(units);
  }
}
