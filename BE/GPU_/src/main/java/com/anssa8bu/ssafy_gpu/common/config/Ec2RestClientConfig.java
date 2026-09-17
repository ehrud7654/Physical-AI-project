package com.anssa8bu.ssafy_gpu.common.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class Ec2RestClientConfig {
  @Bean("ec2RestClient")
  RestClient ec2RestClient(Ec2ApiProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(factory)
        .build();
  }

  @Bean
  HttpClient presignedHttpClient(Ec2ApiProperties properties) {
    return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
  }
}
