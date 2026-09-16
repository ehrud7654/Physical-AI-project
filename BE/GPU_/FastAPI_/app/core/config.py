from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_token: str = Field("local-fastapi-token", alias="FASTAPI_SERVICE_TOKEN")
    gpu_spring_base_url: str = Field("http://gpu-backend:8081", alias="GPU_SPRING_BASE_URL")
    gpu_spring_callback_token: str = Field("local-callback-token", alias="GPU_SPRING_CALLBACK_TOKEN")
    worker_id: str = Field("ssafy_gpu_01", alias="WORKER_ID")
    storage_node_id: str = Field("18000000-0000-4000-8000-000000000002", alias="STORAGE_NODE_ID")
    shared_storage_root: Path = Field(Path("./data"), alias="SHARED_STORAGE_ROOT")
    run_journal_root: Path = Field(Path("./data/journal/training-runs"), alias="RUN_JOURNAL_ROOT")
    supported_algorithms: str = Field("ACT", alias="SUPPORTED_ALGORITHMS")
    gpu_count: int = Field(1, ge=1, alias="GPU_COUNT")
    training_command_duration_seconds: int = Field(5, ge=1, alias="TRAINING_COMMAND_DURATION_SECONDS")
    supervisor_interval_seconds: float = Field(1.0, gt=0, alias="SUPERVISOR_INTERVAL_SECONDS")
    completion_retry_seconds: float = Field(5.0, gt=0, alias="COMPLETION_RETRY_SECONDS")

    @property
    def algorithm_set(self) -> set[str]:
        return {value.strip() for value in self.supported_algorithms.split(",") if value.strip()}


@lru_cache
def get_settings() -> Settings:
    return Settings()
