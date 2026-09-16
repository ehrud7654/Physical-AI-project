from typing import Any

from pydantic import Field, field_validator

from app.schemas.common import ApiModel


class CheckpointMetadata(ApiModel):
    architecture: str
    framework: str
    framework_version: str
    contract_version: str
    robot_profile_id: str
    training_profile_key: str
    training_profile_version: int = Field(ge=1)
    training_config_sha256: str
    action_space: str
    n_params: int = Field(ge=0)
    control_rate_hz: int = Field(gt=0)
    camera_names: list[str]
    input_schema: dict[str, Any]
    output_schema: dict[str, Any]


class CheckpointResult(ApiModel):
    checkpoint_storage_key: str = Field(min_length=1)
    size_bytes: int = Field(gt=0)
    sha256: str
    metadata: CheckpointMetadata

    @field_validator("sha256")
    @classmethod
    def validate_hash(cls, value: str) -> str:
        if len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
            raise ValueError("sha256 must be a lowercase SHA-256")
        return value

