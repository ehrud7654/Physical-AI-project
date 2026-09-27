package com.anssa8bu.ssafy.auth.dto;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, AuthenticatedUserResponse user,
    java.time.Instant sessionExpiresAt, java.time.Instant absoluteExpiresAt) {}
