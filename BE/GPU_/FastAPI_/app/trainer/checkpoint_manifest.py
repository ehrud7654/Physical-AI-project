import json
import os
from typing import Any


def build_metadata(request: dict[str, Any]) -> dict[str, Any]:
    return {
        "architecture": os.getenv("MODEL_ARCHITECTURE", "DIFFUSION_UNET_TIMM_UMI"),
        "framework": os.getenv("MODEL_FRAMEWORK", "PyTorch"),
        "frameworkVersion": os.getenv("MODEL_FRAMEWORK_VERSION", "integration-pending"),
        "contractVersion": os.getenv("MODEL_CONTRACT_VERSION", "umi-policy-v1"),
        "robotProfileId": os.getenv(
            "ROBOT_PROFILE_ID", "52000000-0000-4000-8000-000000000001"
        ),
        "actionSpace": os.getenv("MODEL_ACTION_SPACE", "EEF_RELATIVE_ROT6D"),
        "actionSpec": json.loads(os.getenv(
            "MODEL_ACTION_SPEC_JSON",
            '{"dim":10,"horizon":8,"rateHz":10,"layout":["dx","dy","dz","r00","r01","r02","r10","r11","r12","gap_m"],"rotation":"rotmat_first_two_ROWS","compose":"T_next = T_cur @ A_relative","gapUnit":"m","execSlice":[1,5]}'
        )),
        "nParams": int(os.getenv("MODEL_PARAMETER_COUNT", "1")),
        "controlRateHz": int(os.getenv("MODEL_CONTROL_RATE_HZ", "10")),
        "cameraNames": json.loads(os.getenv("MODEL_CAMERA_NAMES_JSON", '["front"]')),
        "inputSchema": json.loads(os.getenv("MODEL_INPUT_SCHEMA_JSON", '{"stateShape":[6]}')),
        "outputSchema": json.loads(os.getenv("MODEL_OUTPUT_SCHEMA_JSON", '{"actionShape":[6]}')),
    }

