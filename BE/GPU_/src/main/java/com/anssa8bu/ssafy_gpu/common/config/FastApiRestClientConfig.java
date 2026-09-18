package com.anssa8bu.ssafy_gpu.common.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FastApiRestClientConfig {
  @Bean("fastApiRestClient")
  RestClient fastApiRestClient(FastApiProperties properties) {
    HttpClient client =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceToken())
        .build();
  }
}
