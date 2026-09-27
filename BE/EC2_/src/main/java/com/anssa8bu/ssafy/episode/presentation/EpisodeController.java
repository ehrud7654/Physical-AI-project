package com.anssa8bu.ssafy.episode.presentation;

import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.EpisodeResponse;
import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.EpisodeListResponse;
import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.ListQuery;
import static com.anssa8bu.ssafy.episode.dto.EpisodeApiModels.RetentionPolicyResponse;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.episode.application.EpisodeQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/episodes")
public class EpisodeController {
  private final EpisodeQueryService service;

  public EpisodeController(EpisodeQueryService service) {
    this.service = service;
  }

  /** EPISODE-01 */
  @GetMapping
  public ApiResponse<List<EpisodeListResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @ModelAttribute ListQuery query) {
    return ApiResponse.of(service.list(owner(jwt), query));
  }

  /** EPISODE-06. 리터럴 경로가 {episodeId}보다 우선 매칭된다. */
  @GetMapping("/retention-policy")
  public ApiResponse<RetentionPolicyResponse> retentionPolicy() {
    return ApiResponse.of(service.retentionPolicy());
  }

  /** EPISODE-02 */
  @GetMapping("/{episodeId}")
  public ApiResponse<EpisodeResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID episodeId) {
    return ApiResponse.of(service.get(owner(jwt), episodeId));
  }

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
