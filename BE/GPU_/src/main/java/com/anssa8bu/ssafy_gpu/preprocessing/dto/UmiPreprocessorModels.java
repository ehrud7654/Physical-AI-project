package com.anssa8bu.ssafy_gpu.preprocessing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public final class UmiPreprocessorModels {
  private UmiPreprocessorModels() {}

  public record StartRequest(
      @JsonProperty("job_id") String jobId,
      @JsonProperty("dataset_name") String datasetName,
      @JsonProperty("calibration_profile") String calibrationProfile,
      @JsonProperty("require_mapping") boolean requireMapping) {}

  public record StartResponse(String jobId, String status) {}

  public record Artifact(
      String dataset,
      @JsonProperty("dataset_sha256") String datasetSha256,
      String report,
      @JsonProperty("report_sha256") String reportSha256,
      String archive,
      @JsonProperty("archive_sha256") String archiveSha256) {}

  public record JobStatus(String status, Artifact artifact, Map<String, Object> input, Object error) {}
}
