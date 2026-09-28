package com.anssa8bu.ssafy.episode.vo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record QualitySummary(Map<String, Object> values) {
  public QualitySummary {
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }
}
