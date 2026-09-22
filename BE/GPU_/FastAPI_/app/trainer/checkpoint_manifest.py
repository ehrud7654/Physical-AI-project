import json
import os
from typing import Any


def build_metadata(request: dict[str, Any]) -> dict[str, Any]:
    return {
        "architecture": os.getenv("MODEL_ARCHITECTURE", "DIFFUSION_UNET_TIMM_UMI"),
        "framework": os.getenv("MODEL_FRAMEWORK", "PyTorch"),
        "frameworkVersion": os.getenv(
            "MODEL_FRAMEWORK_VERSION", "umi-d095ba9590df789df5189eea5ee7e431689038a6"
        ),
        "contractVersion": os.getenv("MODEL_CONTRACT_VERSION", "umi-policy-v1"),
        "robotProfileId": os.getenv(
            "ROBOT_PROFILE_ID", "52000000-0000-4000-8000-000000000001"
        ),
        "actionSpace": os.getenv("MODEL_ACTION_SPACE", "EEF_RELATIVE_ROT6D"),
        "actionSpec": json.loads(os.getenv(
            "MODEL_ACTION_SPEC_JSON",
            '{"schemaVersion":1,"dim":10,"horizon":16,"nActionSteps":8,"rateHz":10,"layout":["dx","dy","dz","r00","r01","r02","r10","r11","r12","gap_m"],"rotation":"ROTATION_MATRIX_ROWS_0_1","compose":"T_next = T_cur @ A_relative","gapUnit":"m","gapRange":[0,0.09]}'
        )),
        "runtimeSpec": json.loads(os.getenv(
            "MODEL_RUNTIME_SPEC_JSON",
            '{"consumerMustConvert":true,"convertedDim":7,"convertedLayout":["x","y","z","rx","ry","rz","gap_m"],"convertedRotation":"AXIS_ANGLE_ROTVEC","convertedFrame":"ABSOLUTE","execSlice":{"startInclusive":1,"endExclusive":5},"execSliceAppliesTo":"converted","actionPointRateHz":10,"reobserveRateHz":2.5}'
        )),
        "nParams": int(os.getenv("MODEL_PARAMETER_COUNT", "1")),
        "controlRateHz": int(os.getenv("MODEL_CONTROL_RATE_HZ", "10")),
        "cameraNames": json.loads(os.getenv("MODEL_CAMERA_NAMES_JSON", '["camera0_rgb"]')),
        "inputSchema": json.loads(os.getenv(
            "MODEL_INPUT_SCHEMA_JSON",
            '{"camera0_rgb":{"shape":[2,3,224,224]},"robot0_eef_pos":{"shape":[2,3]},"robot0_eef_rot_axis_angle":{"shape":[2,6]},"robot0_eef_rot_axis_angle_wrt_start":{"shape":[2,6]},"robot0_gripper_width":{"shape":[2,1]}}',
        )),
        "outputSchema": json.loads(os.getenv("MODEL_OUTPUT_SCHEMA_JSON", '{"actionShape":[16,10]}')),
    }

