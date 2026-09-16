from typing import Any, Literal

from pydantic import Field, field_validator

from app.schemas.common import ApiModel


class Hyperparameters(ApiModel):
    epochs: int = Field(ge=1)
    batch_size: int = Field(ge=1)
    seed: int
    learning_rate: float = Field(gt=0)
    optimizer: str = Field(min_length=1)


class TrainingProfileSnapshot(ApiModel):
    profile_key: str = Field(min_length=1)
    profile_version: int = Field(ge=1)
    algorithm: Literal["ACT"]
    hyperparameters: Hyperparameters
    config_sha256: str

    @field_validator("config_sha256")
    @classmethod
    def validate_hash(cls, value: str) -> str:
        if len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
            raise ValueError("configSha256 must be a lowercase SHA-256")
        return value

    def hash_input(self) -> dict[str, Any]:
        return {"algorithm": self.algorithm, "hyperparameters": self.hyperparameters.model_dump(by_alias=True)}

