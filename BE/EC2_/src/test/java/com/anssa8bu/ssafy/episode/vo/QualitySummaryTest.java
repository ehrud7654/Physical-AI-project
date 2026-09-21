package com.anssa8bu.ssafy.episode.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualitySummaryTest {

  @Test
  void preservesNullableMetadataAsAnImmutableSnapshot() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("calibrationId", null);
    source.put("trainingReady", false);

    QualitySummary summary = new QualitySummary(source);
    source.put("calibrationId", "changed-after-creation");

    assertThat(summary.values())
        .containsEntry("calibrationId", null)
        .containsEntry("trainingReady", false);
    assertThatThrownBy(() -> summary.values().put("another", true))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
