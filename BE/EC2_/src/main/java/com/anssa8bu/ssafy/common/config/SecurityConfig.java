package com.anssa8bu.ssafy.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/signup",
            "/api/v1/device-auth/token",
            "/actuator/health",
            "/actuator/health/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoderProvider
    ) throws Exception {
        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtDecoder == null) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    .anyRequest().denyAll());
        } else {
            http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.decoder(jwtDecoder)));
        }

        return http.build();
    }
}
