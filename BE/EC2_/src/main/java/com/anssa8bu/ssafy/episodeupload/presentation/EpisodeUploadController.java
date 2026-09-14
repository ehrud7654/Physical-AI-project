package com.anssa8bu.ssafy.episodeupload.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.episodeupload.application.EpisodeUploadService;
import com.anssa8bu.ssafy.episodeupload.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-jobs/{trainingJobId}/episode-upload-sessions")
public class EpisodeUploadController {
  private final EpisodeUploadService service;
  public EpisodeUploadController(EpisodeUploadService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<ApiResponse<EpisodeUploadSessionResponse>> create(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID trainingJobId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody EpisodeUploadSessionRequest request) {
    EpisodeUploadSessionResponse result = service.create(owner(jwt), device(jwt), trainingJobId, key, request);
    URI location = URI.create("/api/v1/training-jobs/" + trainingJobId
        + "/episode-upload-sessions/" + result.uploadSessionId());
    return ResponseEntity.created(location).body(ApiResponse.of(result));
  }

  @PostMapping("/{uploadSessionId}/complete")
  public ResponseEntity<ApiResponse<EpisodeUploadCompleteResponse>> complete(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID trainingJobId,
      @PathVariable UUID uploadSessionId, @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody EpisodeUploadCompleteRequest request) {
    EpisodeUploadCompleteResponse result = service.complete(owner(jwt), device(jwt), trainingJobId,
        uploadSessionId, key, request);
    return result.duplicate() ? ResponseEntity.ok(ApiResponse.of(result))
        : ResponseEntity.accepted().body(ApiResponse.of(result));
  }

  @DeleteMapping("/{uploadSessionId}")
  public ResponseEntity<Void> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID trainingJobId,
      @PathVariable UUID uploadSessionId) {
    service.cancel(owner(jwt), device(jwt), trainingJobId, uploadSessionId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{uploadSessionId}/renew")
  public ApiResponse<EpisodeUploadSessionResponse> renew(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID trainingJobId,
      @PathVariable UUID uploadSessionId) {
    return ApiResponse.of(service.renew(owner(jwt), device(jwt), trainingJobId, uploadSessionId));
  }
  private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
  private UUID device(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("deviceId")); }
}
