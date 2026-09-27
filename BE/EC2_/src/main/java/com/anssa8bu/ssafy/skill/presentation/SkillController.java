package com.anssa8bu.ssafy.skill.presentation;

import static com.anssa8bu.ssafy.skill.dto.SkillResponses.*;

import com.anssa8bu.ssafy.common.exception.ResourceNotFoundException;
import com.anssa8bu.ssafy.common.response.ApiResponse;
import com.anssa8bu.ssafy.model.entity.*;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import com.anssa8bu.ssafy.skill.application.SkillService;
import com.anssa8bu.ssafy.skill.dto.SkillApiModels.CreateCustomSkillRequest;
import com.anssa8bu.ssafy.skill.dto.SkillApiModels.UpdateSkillDescriptionRequest;
import com.anssa8bu.ssafy.skill.entity.Skill;
import com.anssa8bu.ssafy.skill.repository.SkillRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {
  private final SkillRepository skills;
  private final SkillVersionRepository versions;
  private final SkillService service;

  public SkillController(
      SkillRepository skills, SkillVersionRepository versions, SkillService service) {
    this.skills = skills;
    this.versions = versions;
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<SkillResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.of(service.listVisible(owner(jwt)));
  }

  @GetMapping("/overview")
  public ApiResponse<List<SkillOverviewResponse>> overview(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.of(service.listOverview(owner(jwt)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SkillResponse>> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateCustomSkillRequest request) {
    SkillResponse result = service.create(owner(jwt), idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/skills/" + result.id()))
        .body(ApiResponse.of(result));
  }

  @PatchMapping("/{skillId}")
  public ApiResponse<SkillResponse> updateDescription(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID skillId,
      @Valid @RequestBody UpdateSkillDescriptionRequest request) {
    return ApiResponse.of(service.updateDescription(owner(jwt), skillId, request));
  }

  @GetMapping("/{skillId}/versions/latest")
  public ApiResponse<SkillVersionResponse> latest(@PathVariable UUID skillId) {
    if (!skills.existsById(skillId)) throw new ResourceNotFoundException("Skill");
    SkillVersion version =
        versions
            .findFirstBySkillIdAndStatusAndGatePassedTrueOrderByVersionDesc(
                skillId, SkillVersionStatus.READY)
            .orElseThrow(() -> new ResourceNotFoundException("SkillVersion"));
    return ApiResponse.of(map(version));
  }

  @GetMapping("/{skillId}/training-requirements")
  public ApiResponse<TrainingRequirementsResponse> trainingRequirements(
      @PathVariable UUID skillId) {
    Skill skill =
        skills.findById(skillId).orElseThrow(() -> new ResourceNotFoundException("Skill"));
    int recommended =
        switch (skill.getSkillKey()) {
          case "pick_place", "present_inspect" -> 10;
          case "sort_two", "align_fixture" -> 15;
          case "line_up" -> 20;
          default -> 10;
        };
    int minimum = Math.max(1, recommended / 2);
    return ApiResponse.of(
        new TrainingRequirementsResponse(
            skill.getId(),
            skill.getSkillKey(),
            minimum,
            recommended,
            100,
            List.of("REAL"),
            false,
            "episode-archive-v1",
            true,
            1));
  }

  private SkillResponse map(Skill s) {
    return new SkillResponse(
        s.getId(),
        s.getSkillKey(),
        s.getTier(),
        s.getStatus(),
        s.getDisplayName(),
        s.getDescription(),
        s.getPersona(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }

  private UUID owner(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  private SkillVersionResponse map(SkillVersion v) {
    return new SkillVersionResponse(
        v.getId(),
        v.getSkillId(),
        v.getVersion(),
        v.getStatus().name(),
        v.getCheckpointBytes(),
        v.getCheckpointSha256(),
        v.getRobotProfileId(),
        v.getContractVersion(),
        v.isGatePassed(),
        v.getCreatedAt());
  }
}
