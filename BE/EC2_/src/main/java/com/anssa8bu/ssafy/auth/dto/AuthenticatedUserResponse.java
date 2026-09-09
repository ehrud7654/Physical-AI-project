package com.anssa8bu.ssafy.auth.dto;

import com.anssa8bu.ssafy.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record AuthenticatedUserResponse(
    UUID id, String email, String name, Instant createdAt) {

  public static AuthenticatedUserResponse from(User user) {
    return new AuthenticatedUserResponse(
        user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
  }
}
