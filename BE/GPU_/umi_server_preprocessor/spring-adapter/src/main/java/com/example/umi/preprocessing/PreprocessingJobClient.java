package com.example.umi.preprocessing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PreprocessingJobClient {
    private final RestClient client;

    public PreprocessingJobClient(
            RestClient.Builder builder,
            @Value("${umi.preprocessor.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public PreprocessingJobStatus start(PreprocessingJobRequest request) {
        return client.post()
                .uri("/internal/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PreprocessingJobStatus.class);
    }

    public PreprocessingJobStatus get(String jobId) {
        return client.get()
                .uri("/internal/v1/jobs/{jobId}", jobId)
                .retrieve()
                .body(PreprocessingJobStatus.class);
    }

    public void cancel(String jobId) {
        client.post()
                .uri("/internal/v1/jobs/{jobId}/cancel", jobId)
                .retrieve()
                .toBodilessEntity();
    }
}
