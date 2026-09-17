import json
import os
from typing import Any


def build_metadata(request: dict[str, Any]) -> dict[str, Any]:
    profile = request["trainingProfile"]
    return {
        "architecture": profile["algorithm"],
        "framework": os.getenv("MODEL_FRAMEWORK", "PyTorch"),
        "frameworkVersion": os.getenv("MODEL_FRAMEWORK_VERSION", "integration-pending"),
        "contractVersion": os.getenv("MODEL_CONTRACT_VERSION", "umi-policy-v1"),
        "robotProfileId": os.getenv(
            "ROBOT_PROFILE_ID", "52000000-0000-4000-8000-000000000001"
        ),
        "trainingProfileKey": profile["profileKey"],
        "trainingProfileVersion": profile["profileVersion"],
        "trainingConfigSha256": profile["configSha256"],
        "actionSpace": os.getenv("MODEL_ACTION_SPACE", "JOINT_ABSOLUTE"),
        "nParams": int(os.getenv("MODEL_PARAMETER_COUNT", "1")),
        "controlRateHz": int(os.getenv("MODEL_CONTROL_RATE_HZ", "30")),
        "cameraNames": json.loads(os.getenv("MODEL_CAMERA_NAMES_JSON", '["front"]')),
        "inputSchema": json.loads(os.getenv("MODEL_INPUT_SCHEMA_JSON", '{"stateShape":[6]}')),
        "outputSchema": json.loads(os.getenv("MODEL_OUTPUT_SCHEMA_JSON", '{"actionShape":[6]}')),
    }

