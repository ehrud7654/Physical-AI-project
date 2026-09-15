package com.anssa8bu.ssafy.training.presentation.internal;

import static com.anssa8bu.ssafy.training.dto.internal.GpuTrainingApiModels.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.training.application.worker.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/training-jobs")
public class GpuTrainingController {
  private final TrainingClaimService claims; private final TrainingHeartbeatService heartbeats;
  private final TrainingEventService events;
  public GpuTrainingController(TrainingClaimService claims, TrainingHeartbeatService heartbeats,
      TrainingEventService events) { this.claims=claims; this.heartbeats=heartbeats; this.events=events; }
  @PostMapping("/claim")
  public ResponseEntity<ApiResponse<ClaimResponse>> claim(@Valid @RequestBody ClaimRequest request) {
    return claims.claim(request).map(v -> ResponseEntity.ok(ApiResponse.of(v)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
  @PostMapping("/{id}/heartbeat")
  public ApiResponse<HeartbeatResponse> heartbeat(@PathVariable UUID id,
      @Valid @RequestBody HeartbeatRequest request) { return ApiResponse.of(heartbeats.heartbeat(id,request)); }
  @PostMapping("/{id}/events")
  public ApiResponse<EventResponse> event(@PathVariable UUID id,
      @Valid @RequestBody EventRequest request) { return ApiResponse.of(events.accept(id,request)); }
}
