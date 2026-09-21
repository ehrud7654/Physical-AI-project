package com.example.umi.preprocessing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreprocessingJobStatus(
        String status,
        String jobId,
        String report,
        JsonNode artifact,
        JsonNode error) {
}
