package com.anssa8bu.ssafy.gate.application;
import static com.anssa8bu.ssafy.gate.dto.GateApiModels.*;
import com.anssa8bu.ssafy.common.exception.*;
import com.anssa8bu.ssafy.common.idempotency.IdempotencyGuard;
import com.anssa8bu.ssafy.deployment.repository.DeploymentItemRepository;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import com.anssa8bu.ssafy.model.repository.SkillVersionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SkillVersionGateService {
  private final SkillVersionRepository versions; private final DeploymentItemRepository deployments;
  private final IdempotencyGuard idempotency;
  public SkillVersionGateService(SkillVersionRepository versions, DeploymentItemRepository deployments,
      IdempotencyGuard idempotency) {
    this.versions=versions;this.deployments=deployments;this.idempotency=idempotency;
  }
  @Transactional
  public GateResultResponse record(UUID id, String key, GateResultRequest request) {
    String path="/internal/skill-versions/"+id+"/gate-result";
    var claim=idempotency.claim("GPU",id,"POST",path,key,request);
    if (claim.existingResourceId()!=null) {
      SkillVersion existing=versions.findById(id).orElseThrow(() -> new ResourceNotFoundException("SkillVersion"));
      boolean passed="rollout_success".equals(request.metric()) && request.n()>=100 && request.runs()>=3
          && request.value()>0.20 && request.ciLow()>0.20;
      return new GateResultResponse(id,existing.getStatus().name(),passed,request.metric(),request.n(),
          request.runs(),request.value(),request.ciLow(),request.ciHigh(),request.measuredAt());
    }
    if (request.ciLow()>request.ciHigh())
      throw new BusinessConflictException("INVALID_CONFIDENCE_INTERVAL", "ciLow는 ciHigh보다 클 수 없습니다.");
    if (deployments.existsBySkillVersionId(id))
      throw new BusinessConflictException("STATE_CONFLICT", "배포에서 참조 중인 버전의 게이트는 변경할 수 없습니다.");
    SkillVersion version=versions.findById(id).orElseThrow(() -> new ResourceNotFoundException("SkillVersion"));
    version.recordGate(request.metric(),request.n(),request.runs(),request.value(),request.ciLow(),request.ciHigh(),request.measuredAt());
    boolean passed="rollout_success".equals(request.metric()) && request.n()>=100 && request.runs()>=3
        && request.value()>0.20 && request.ciLow()>0.20;
    if (claim.existingResourceId()==null)
      idempotency.complete("GPU",id,"POST",path,claim,200,"SKILL_VERSION",id);
    return new GateResultResponse(id,version.getStatus().name(),passed,request.metric(),request.n(),
        request.runs(),request.value(),request.ciLow(),request.ciHigh(),request.measuredAt());
  }
}
