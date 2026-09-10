package com.anssa8bu.ssafy.training.presentation;

import static com.anssa8bu.ssafy.training.dto.TrainingJobApiModels.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.training.application.TrainingJobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-jobs")
public class TrainingJobController {
  private final TrainingJobService service;
  public TrainingJobController(TrainingJobService service){this.service=service;}
  @PostMapping public ResponseEntity<ApiResponse<JobResponse>> create(@AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateRequest request){
    JobResponse result=service.create(owner(jwt),key,request);
    return ResponseEntity.created(URI.create("/api/v1/training-jobs/"+result.id())).body(ApiResponse.of(result));
  }
  @GetMapping public ApiResponse<List<JobResponse>> list(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required=false) UUID skillId,@RequestParam(required=false) String skillKey,
      @RequestParam(required=false) UUID umiDeviceId,@RequestParam(required=false) String status){
    return ApiResponse.of(service.list(owner(jwt),skillId,skillKey,umiDeviceId,status));}
  @GetMapping("/{id}") public ApiResponse<JobResponse> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    return ApiResponse.of(service.get(owner(jwt),id));}
  @PostMapping("/{id}/cancel") public ApiResponse<JobResponse> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    return ApiResponse.of(service.cancel(owner(jwt),id));}
  @GetMapping("/{id}/episodes") public ApiResponse<List<EpisodeInputResponse>> episodes(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    return ApiResponse.of(service.inputEpisodes(owner(jwt),id));}
  private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
