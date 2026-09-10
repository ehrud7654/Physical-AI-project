package com.anssa8bu.ssafy.episode.vo;

import java.util.Map;

public record QualitySummary(Map<String, Object> values) {
  public QualitySummary {
    values = Map.copyOf(values);
  }
}
