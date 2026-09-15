package com.anssa8bu.ssafy.checkpoint.presentation.internal;

import static com.anssa8bu.ssafy.checkpoint.dto.CheckpointApiModels.*;
import com.anssa8bu.ssafy.checkpoint.application.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/training-jobs/{trainingJobId}")
public class CheckpointController {
  private final CheckpointUploadSessionService uploads; private final CheckpointCompleteService completion;
  public CheckpointController(CheckpointUploadSessionService uploads, CheckpointCompleteService completion) {
    this.uploads=uploads;this.completion=completion;
  }
  @PostMapping("/checkpoint-upload-session")
  public ResponseEntity<ApiResponse<UploadSessionResponse>> issue(@PathVariable UUID trainingJobId,
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody UploadSessionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(uploads.issue(trainingJobId,key,request)));
  }
  @PostMapping("/checkpoint-complete")
  public ResponseEntity<ApiResponse<CompleteResponse>> complete(@PathVariable UUID trainingJobId,
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CompleteRequest request) {
    return ResponseEntity.accepted().body(ApiResponse.of(completion.complete(trainingJobId,key,request)));
  }
}
