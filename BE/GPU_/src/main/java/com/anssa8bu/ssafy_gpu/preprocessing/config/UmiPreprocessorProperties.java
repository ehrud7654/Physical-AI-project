package com.anssa8bu.ssafy_gpu.preprocessing.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.umi-preprocessor")
public record UmiPreprocessorProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration pollInterval,
    Duration processingTimeout,
    String calibrationProfile,
    boolean requireMapping,
    int maxArchiveEntries,
    long maxExtractedBytes) {}
