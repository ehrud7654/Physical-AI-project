package com.anssa8bu.ssafy_gpu.common.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ec2-api")
public record Ec2ApiProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {}
