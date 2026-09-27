package com.anssa8bu.ssafy.storage.presentation.internal;

import static com.anssa8bu.ssafy.storage.dto.internal.StorageNodeApiModels.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.storage.application.StorageNodeService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/storage-nodes")
public class StorageNodeController {
  private final StorageNodeService service;
  public StorageNodeController(StorageNodeService service){this.service=service;}
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<NodeResponse>> register(@Valid @RequestBody RegisterRequest request) {
    var result=service.register(request);
    return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK)
        .body(ApiResponse.of(result.node()));
  }
  @PostMapping("/{id}/heartbeat")
  public ApiResponse<HeartbeatResponse> heartbeat(@PathVariable UUID id,
      @Valid @RequestBody HeartbeatRequest request) {
    return ApiResponse.of(service.heartbeat(id,request));
  }
}
