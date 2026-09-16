from typing import Any


def build_metadata(request: dict[str, Any]) -> dict[str, Any]:
    profile = request["trainingProfile"]
    return {
        "architecture": profile["algorithm"],
        "framework": "PyTorch",
        "frameworkVersion": "integration-pending",
        "contractVersion": "umi-policy-v1",
        "robotProfileId": "so101-v1",
        "trainingProfileKey": profile["profileKey"],
        "trainingProfileVersion": profile["profileVersion"],
        "trainingConfigSha256": profile["configSha256"],
        "actionSpace": "JOINT_DELTA",
        "nParams": 0,
        "controlRateHz": 30,
        "cameraNames": ["cam_front"],
        "inputSchema": {"stateShape": [6]},
        "outputSchema": {"actionShape": [6]},
    }

