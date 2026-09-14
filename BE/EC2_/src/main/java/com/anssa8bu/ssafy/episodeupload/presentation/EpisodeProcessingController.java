package com.anssa8bu.ssafy.episodeupload.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.episodeupload.application.EpisodeUploadService;
import com.anssa8bu.ssafy.episodeupload.dto.EpisodeProcessingStatusResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/episode/data-upload-request")
public class EpisodeProcessingController {

  private final EpisodeUploadService service;

  public EpisodeProcessingController(EpisodeUploadService service) {
    this.service = service;
  }

  @GetMapping("/by-session/{sessionId}")
  public ApiResponse<EpisodeProcessingStatusResponse> status(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID sessionId) {
    return ApiResponse.of(
        service.status(
            UUID.fromString(jwt.getSubject()),
            UUID.fromString(jwt.getClaimAsString("deviceId")),
            sessionId));
  }
}
