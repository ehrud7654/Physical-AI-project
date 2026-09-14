package com.anssa8bu.ssafy.episodeupload.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(EpisodeStorageProperties.class)
public class EpisodeStorageConfig {
  @Bean S3Presigner episodeS3Presigner(EpisodeStorageProperties properties) {
    return S3Presigner.builder().region(Region.of(properties.region())).build();
  }
  @Bean S3Client episodeS3Client(EpisodeStorageProperties properties) {
    return S3Client.builder().region(Region.of(properties.region())).build();
  }
}
