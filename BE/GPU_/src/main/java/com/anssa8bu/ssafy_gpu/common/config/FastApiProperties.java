package com.anssa8bu.ssafy_gpu.common.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.fastapi")
public record FastApiProperties(
    URI baseUrl,
    String serviceToken,
    String callbackToken,
    Duration connectTimeout,
    Duration readTimeout,
    Duration monitorInterval) {}
