package com.anssa8bu.ssafy.gate.presentation.internal;
import static com.anssa8bu.ssafy.gate.dto.GateApiModels.*;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.gate.application.SkillVersionGateService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/skill-versions")
public class SkillVersionGateController {
  private final SkillVersionGateService service;
  public SkillVersionGateController(SkillVersionGateService service){this.service=service;}
  @PostMapping("/{id}/gate-result")
  public ApiResponse<GateResultResponse> record(@PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody GateResultRequest request) {
    return ApiResponse.of(service.record(id,key,request));
  }
}
