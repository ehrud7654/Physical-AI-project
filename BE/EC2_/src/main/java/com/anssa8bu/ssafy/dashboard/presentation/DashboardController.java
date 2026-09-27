package com.anssa8bu.ssafy.dashboard.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.dashboard.application.DashboardQueryService;
import com.anssa8bu.ssafy.dashboard.dto.DashboardResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final DashboardQueryService dashboardQueryService;

  public DashboardController(DashboardQueryService dashboardQueryService) {
    this.dashboardQueryService = dashboardQueryService;
  }

  @GetMapping
  public ApiResponse<DashboardResponse> getDashboard(@AuthenticationPrincipal Jwt jwt) {
    UUID ownerId = UUID.fromString(jwt.getSubject());
    return ApiResponse.of(dashboardQueryService.getDashboard(ownerId));
  }
}
