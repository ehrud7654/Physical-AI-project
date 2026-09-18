package com.anssa8bu.ssafy_gpu.common.config;

import java.net.http.HttpClient;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class Ec2RestClientConfig {
  private static final Logger log = LoggerFactory.getLogger(Ec2RestClientConfig.class);

  @Bean("ec2RestClient")
  RestClient ec2RestClient(Ec2ApiProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(factory)
        .requestInterceptor(
            (request, body, execution) -> {
              String path = request.getURI().getPath();
              boolean heartbeat = path.endsWith("/heartbeat");
              if (!heartbeat) log.info("EC2 API 요청: method={}, path={}", request.getMethod(), path);
              var response = execution.execute(request, body);
              if (!heartbeat)
                log.info(
                    "EC2 API 응답: method={}, path={}, status={}",
                    request.getMethod(),
                    path,
                    response.getStatusCode().value());
              return response;
            })
        .build();
  }

  @Bean
  HttpClient presignedHttpClient(Ec2ApiProperties properties) {
    return HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(properties.connectTimeout())
        .build();
  }

  @Bean(name = "trainingPreparationExecutor", destroyMethod = "close")
  ExecutorService trainingPreparationExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
