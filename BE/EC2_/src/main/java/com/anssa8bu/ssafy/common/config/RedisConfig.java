package com.anssa8bu.ssafy.common.config;

import com.anssa8bu.ssafy.common.redis.RedisTtlProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(RedisTtlProperties.class)
public class RedisConfig {

  @Bean
  public RedisTemplate<String, Object> redisTemplate(
      RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    StringRedisSerializer keySerializer = new StringRedisSerializer();
    GenericJacksonJsonRedisSerializer valueSerializer =
        new GenericJacksonJsonRedisSerializer(objectMapper);

    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(keySerializer);
    template.setHashKeySerializer(keySerializer);
    template.setValueSerializer(valueSerializer);
    template.setHashValueSerializer(valueSerializer);
    template.afterPropertiesSet();
    return template;
  }
}
