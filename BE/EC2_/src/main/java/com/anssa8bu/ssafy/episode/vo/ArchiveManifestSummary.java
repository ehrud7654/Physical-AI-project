package com.anssa8bu.ssafy.episode.vo;

import java.util.Map;

public record ArchiveManifestSummary(
    Map<String, String> fileSha256, int frameEntryCount, Map<String, Object> details) {
  public ArchiveManifestSummary {
    fileSha256 = Map.copyOf(fileSha256);
    details = Map.copyOf(details);
    if (frameEntryCount < 1)
      throw new IllegalArgumentException("frameEntryCount must be at least one");
  }
}
