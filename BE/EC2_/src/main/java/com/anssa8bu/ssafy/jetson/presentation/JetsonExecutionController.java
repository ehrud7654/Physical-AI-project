package com.anssa8bu.ssafy.jetson.presentation;

import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.execution.dto.ExecutionApiModels.ExecutionResponse;
import com.anssa8bu.ssafy.jetson.application.JetsonExecutionReportService;
import com.anssa8bu.ssafy.jetson.dto.JetsonExecutionReportRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/robots/me/executions")
public class JetsonExecutionController {
  private final JetsonExecutionReportService service;

  public JetsonExecutionController(JetsonExecutionReportService service) {
    this.service = service;
  }

  @PatchMapping("/{executionId}")
  public ApiResponse<ExecutionResponse> report(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID executionId,
      @Valid @RequestBody JetsonExecutionReportRequest request) {
    return ApiResponse.of(service.report(jwt, executionId, request));
  }
}
