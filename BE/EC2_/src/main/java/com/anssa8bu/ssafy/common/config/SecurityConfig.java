package com.anssa8bu.ssafy.common.config;

import com.anssa8bu.ssafy.auth.security.SessionAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/api/v1/auth/signup",
    "/api/v1/auth/email-availability",
    "/api/v1/device-auth/token",
    "/api/v1/device-auth/skills",
    "/api/v1/device-auth/enroll",
    "/actuator/health",
    "/actuator/health/**"
  };

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, SessionAuthenticationEntryPoint entryPoint)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(
                        "/internal/training-jobs/**",
                        "/internal/skill-versions/**",
                        "/internal/storage-nodes/**")
                    .permitAll()
                    .requestMatchers("/api/v1/episode", "/api/v1/episode/**")
                    .hasAuthority("SCOPE_device")
                    .requestMatchers("/api/v1/devices/*/heartbeat")
                    .hasAuthority("SCOPE_device")
                    .requestMatchers("/api/v1/robots/me/**")
                    .hasAuthority("SCOPE_device")
                    .anyRequest()
                    .hasAuthority("SCOPE_user"))
        .oauth2ResourceServer(
            oauth2 -> {
              var bearerResolver = new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver();
              oauth2.bearerTokenResolver(request -> {
                String path = request.getServletPath();
                if (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/login")) return null;
                if (path.equals("/api/v1/auth/logout") && request.getCookies() != null
                    && java.util.Arrays.stream(request.getCookies()).anyMatch(c ->
                        com.anssa8bu.ssafy.auth.security.SessionCookieService.NAME.equals(c.getName()))) return null;
                return bearerResolver.resolve(request);
              });
              oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)).authenticationEntryPoint(entryPoint);
              // The default failure handler rethrows AuthenticationServiceException.
              // Route Redis outages to the explicit 503 response instead.
              oauth2.addObjectPostProcessor(
                  new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                    @Override
                    public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                      filter.setAuthenticationFailureHandler(entryPoint::commence);
                      return filter;
                    }
                  });
            });

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("#{'${app.cors.allowed-origins}'.split(',')}") List<String> allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Session-Request"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
