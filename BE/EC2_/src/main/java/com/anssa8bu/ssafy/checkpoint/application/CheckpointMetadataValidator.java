package com.anssa8bu.ssafy.checkpoint.application;

import com.anssa8bu.ssafy.checkpoint.dto.CheckpointApiModels.Metadata;
import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CheckpointMetadataValidator {
  public void validate(Metadata metadata, RobotProfile robotProfile) {
    if (!robotProfile.getContractVersion().equals(metadata.contractVersion()))
      conflict("RobotProfile contractVersion과 CKPT 계약 버전이 다릅니다.");
    Object actionType=robotProfile.getActionContract().get("actionSpace");
    if (actionType == null) actionType=robotProfile.getActionContract().get("type");
    if (actionType!=null && !metadata.actionSpace().name().equals(actionType.toString()))
      conflict("RobotProfile action contract와 actionSpace가 다릅니다.");
    Object names=robotProfile.getCameraContract().get("cameraNames");
    if (names instanceof List<?> expected
        && !new HashSet<>(expected).equals(new HashSet<>(metadata.cameraNames())))
      conflict("RobotProfile camera contract와 cameraNames가 다릅니다.");
    if (metadata.inputSchema().isEmpty() || metadata.outputSchema().isEmpty())
      conflict("inputSchema와 outputSchema는 비어 있을 수 없습니다.");
  }

  public boolean matches(SkillVersion version, Metadata metadata) {
    return version.getRobotProfileId().equals(metadata.robotProfileId())
        && version.getArchitecture().equals(metadata.architecture())
        && version.getFramework().equals(metadata.framework())
        && version.getFrameworkVersion().equals(metadata.frameworkVersion())
        && version.getContractVersion().equals(metadata.contractVersion())
        && version.getActionSpace()==metadata.actionSpace()
        && version.getActionSpec().equals(metadata.actionSpec())
        && version.getRuntimeSpec().equals(metadata.runtimeSpec())
        && version.getParameterCount()==metadata.nParams()
        && version.getControlRateHz().compareTo(metadata.controlRateHz())==0
        && version.getCameraNames().equals(metadata.cameraNames())
        && version.getInputSchema().equals(metadata.inputSchema())
        && version.getOutputSchema().equals(metadata.outputSchema());
  }

  private static void conflict(String message) {
    throw new BusinessConflictException("CHECKPOINT_METADATA_MISMATCH",message);
  }
}
