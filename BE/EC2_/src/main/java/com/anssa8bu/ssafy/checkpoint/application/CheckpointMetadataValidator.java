package com.anssa8bu.ssafy.checkpoint.application;

import com.anssa8bu.ssafy.checkpoint.dto.CheckpointApiModels.Metadata;
import com.anssa8bu.ssafy.common.exception.BusinessConflictException;
import com.anssa8bu.ssafy.model.entity.RobotProfile;
import com.anssa8bu.ssafy.model.entity.SkillVersion;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CheckpointMetadataValidator {
  private final ObjectMapper objectMapper;

  public CheckpointMetadataValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void validate(Metadata metadata, RobotProfile robotProfile) {
    if (metadata.nParams() <= 1)
      conflict("nParams는 실제 CKPT에서 계산한 1보다 큰 값이어야 합니다.");
    if (!robotProfile.getContractVersion().equals(metadata.contractVersion()))
      conflict("RobotProfile contractVersion과 CKPT 계약 버전이 다릅니다.");
    Object actionType = robotProfile.getActionContract().get("actionSpace");
    if (actionType == null) actionType = robotProfile.getActionContract().get("type");
    if (actionType != null && !metadata.actionSpace().name().equals(actionType.toString()))
      conflict("RobotProfile action contract와 actionSpace가 다릅니다.");
    Object names = robotProfile.getCameraContract().get("cameraNames");
    if (names instanceof List<?> expected
        && !new HashSet<>(expected).equals(new HashSet<>(metadata.cameraNames())))
      conflict("RobotProfile camera contract와 cameraNames가 다릅니다.");
    if (metadata.inputSchema().isEmpty() || metadata.outputSchema().isEmpty())
      conflict("inputSchema와 outputSchema는 비어 있을 수 없습니다.");

    requireJsonContract(
        robotProfile.getActionContract().get("actionSpec"),
        metadata.actionSpec(),
        "RobotProfile actionSpec과 CKPT actionSpec이 다릅니다.");
    requireJsonContract(
        robotProfile.getActionContract().get("runtimeSpec"),
        metadata.runtimeSpec(),
        "RobotProfile runtimeSpec과 CKPT runtimeSpec이 다릅니다.");
    requireJsonContract(
        robotProfile.getStateContract(),
        metadata.inputSchema(),
        "RobotProfile state contract와 CKPT inputSchema가 다릅니다.");
    requireJsonContract(
        robotProfile.getActionContract().get("outputSchema"),
        metadata.outputSchema(),
        "RobotProfile output contract와 CKPT outputSchema가 다릅니다.");
  }

  public boolean matches(SkillVersion version, Metadata metadata) {
    return version.getRobotProfileId().equals(metadata.robotProfileId())
        && version.getArchitecture().equals(metadata.architecture())
        && version.getFramework().equals(metadata.framework())
        && version.getFrameworkVersion().equals(metadata.frameworkVersion())
        && version.getContractVersion().equals(metadata.contractVersion())
        && version.getActionSpace()==metadata.actionSpace()
        && jsonEquals(version.getActionSpec(), metadata.actionSpec())
        && jsonEquals(version.getRuntimeSpec(), metadata.runtimeSpec())
        && version.getParameterCount()==metadata.nParams()
        && version.getControlRateHz().compareTo(metadata.controlRateHz())==0
        && version.getCameraNames().equals(metadata.cameraNames())
        && jsonEquals(version.getInputSchema(), metadata.inputSchema())
        && jsonEquals(version.getOutputSchema(), metadata.outputSchema());
  }

  private void requireJsonContract(Object expected, Object actual, String message) {
    if (expected == null || !jsonEquals(expected, actual)) conflict(message);
  }

  private boolean jsonEquals(Object left, Object right) {
    return objectMapper.valueToTree(left).equals(objectMapper.valueToTree(right));
  }

  private static void conflict(String message) {
    throw new BusinessConflictException("CHECKPOINT_METADATA_MISMATCH", message);
  }
}
