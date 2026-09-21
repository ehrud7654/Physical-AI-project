package com.example.umi.preprocessing;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreprocessingJobRequest(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("dataset_name") String datasetName,
        @JsonProperty("calibration_profile") String calibrationProfile,
        @JsonProperty("require_mapping") boolean requireMapping) {
}
