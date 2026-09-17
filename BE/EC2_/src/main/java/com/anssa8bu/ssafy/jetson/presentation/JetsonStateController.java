package com.anssa8bu.ssafy.jetson.presentation;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.jetson.application.JetsonStateService;
import com.anssa8bu.ssafy.jetson.dto.JetsonStateModels.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/robots/me")
public class JetsonStateController {
  private final JetsonStateService service;
  public JetsonStateController(JetsonStateService s){service=s;}
  @GetMapping("/deployment/current") public ApiResponse<CurrentDeployment> current(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.current(jwt));}
  @GetMapping("/skills/active") public ApiResponse<List<ActiveSkill>> active(@AuthenticationPrincipal Jwt jwt){return ApiResponse.of(service.active(jwt));}
}
