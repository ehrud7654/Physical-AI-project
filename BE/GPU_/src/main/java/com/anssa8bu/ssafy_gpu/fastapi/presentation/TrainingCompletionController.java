package com.anssa8bu.ssafy_gpu.fastapi.presentation;

import static com.anssa8bu.ssafy_gpu.fastapi.dto.FastApiTrainingModels.CompletionRequest;

import com.anssa8bu.ssafy_gpu.common.config.FastApiProperties;
import com.anssa8bu.ssafy_gpu.common.response.ApiResponse;
import com.anssa8bu.ssafy_gpu.fastapi.application.TrainingCompletionOrchestrator;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/training-runs")
public class TrainingCompletionController {
  private final TrainingCompletionOrchestrator completion;
  private final FastApiProperties properties;

  public TrainingCompletionController(
      TrainingCompletionOrchestrator completion, FastApiProperties properties) {
    this.completion = completion;
    this.properties = properties;
  }

  @PostMapping("/{runId}/completion")
  public ResponseEntity<ApiResponse<TrainingCompletionOrchestrator.CompletionResponse>> complete(
      @PathVariable String runId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @Valid @RequestBody CompletionRequest request) {
    authenticate(authorization);
    return ResponseEntity.accepted().body(new ApiResponse<>(completion.complete(runId, request)));
  }

  private void authenticate(String authorization) {
    String expected = "Bearer " + properties.callbackToken();
    if (authorization == null
        || !MessageDigest.isEqual(
            authorization.getBytes(StandardCharsets.UTF_8),
            expected.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "유효한 FastAPI callback token이 필요합니다.");
  }
}
