package com.anssa8bu.ssafy.auth.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  @Bean
  SecretKey jwtSecretKey(JwtProperties properties) {
    return new SecretKeySpec(
        properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(
            JwtValidators.createDefaultWithIssuer(properties.issuer())));
    return decoder;
  }
}
