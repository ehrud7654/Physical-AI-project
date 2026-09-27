# FastAPI 학습 서버 구현 API

기준: 2026-09-25, Git `f3af673`. 내부 API 호출 주체는 GPU Spring입니다. `/internal/*`는 `Authorization: Bearer <service token>`이 필요하며 health는 공개입니다. JSON 요청은 camelCase 또는 snake_case를 허용하고 응답은 camelCase입니다.

오류: `{"error":{"code":"<code>","message":"<message>"}}`; 입력 검증 오류는 422입니다.

## `GET /health/live`

프로세스 생존 여부를 확인합니다. HTTP 200.

요청 body: 없음

응답:
```json
{
  "status": "UP"
}
```

## `GET /health/ready`

공유 저장소 쓰기·삭제 가능 여부를 확인합니다. HTTP 200.

요청 body: 없음

응답:
```json
{
  "status": "UP"
}
```

## `POST /internal/training-runs`

zarr 입력으로 학습 프로세스를 실행합니다. HTTP 신규 202 / 기존 200.

요청 body: 
```json
{
  "trainingJobId": "<string>",
  "attempt": 1,
  "workerId": "<string>",
  "storageNodeId": "<string>",
  "gpuIndex": 1,
  "trainingMode": "FROM_SCRATCH",
  "baseModel": null,
  "inputSnapshotSha256": "<string>",
  "episodes": [
    {
      "inputOrder": 1,
      "episodeId": "<string>",
      "storageKey": "<string>",
      "sha256": "<string>",
      "contractVersion": "<string>"
    }
  ],
  "trainingInput": {
    "storageKey": "<string>",
    "sizeBytes": 1,
    "sha256": "<string>",
    "reportStorageKey": "<string>",
    "reportSha256": "<string>",
    "episodeCount": 1,
    "trainingInputStatus": "ready",
    "format": "ZARR_ZIP",
    "worldFrame": "<string>"
  },
  "executionLeaseExpiresAt": "<ISO-8601 UTC>"
}
```

응답:
```json
{
  "data": {
    "runId": "<string>",
    "trainingJobId": "<string>",
    "attempt": 1,
    "status": "STARTING"
  }
}
```

요청 필드 조건: `trainingJobId: str` (필수); `attempt: int` (필수); `workerId: str` (필수); `storageNodeId: str` (필수); `gpuIndex: int` (필수); `trainingMode: str` (기본값: 'FROM_SCRATCH'); `baseModel: BaseModelInput | None` (기본값: None); `inputSnapshotSha256: str` (필수); `episodes: list[EpisodeInput]` (필수); `trainingInput: TrainingInput` (필수); `executionLeaseExpiresAt: datetime` (필수)

## `GET /internal/training-runs/{run_id}`

학습 상태·진행률·체크포인트 결과를 조회합니다. HTTP 200.

요청 body: 없음

응답:
```json
{
  "data": {
    "runId": "<string>",
    "trainingJobId": "<string>",
    "attempt": 1,
    "status": "COMPLETED",
    "sequence": 1,
    "stage": "<string>",
    "percent": 100,
    "epoch": 1,
    "totalEpochs": 1,
    "lastProgressAt": "<ISO-8601 UTC>",
    "lastProcessHeartbeatAt": "<ISO-8601 UTC>",
    "modelCompletedAt": "<ISO-8601 UTC>",
    "result": {
      "checkpointStorageKey": "<string>",
      "sizeBytes": 1,
      "sha256": "<string>",
      "metadata": {
        "architecture": "<string>",
        "framework": "<string>",
        "frameworkVersion": "<string>",
        "contractVersion": "<string>",
        "robotProfileId": "<string>",
        "actionSpace": "<string>",
        "actionSpec": {},
        "runtimeSpec": {},
        "nParams": 1,
        "controlRateHz": 1,
        "cameraNames": [
          "<string>"
        ],
        "inputSchema": {},
        "outputSchema": {}
      }
    },
    "error": null
  }
}
```

## `POST /internal/training-runs/{run_id}/cancel`

학습 프로세스 취소를 요청합니다. HTTP 접수 202 / 기종료 200.

요청 body: 
```json
{
  "reason": "<string>"
}
```

응답:
```json
{
  "data": {
    "runId": "<string>",
    "status": "STARTING"
  }
}
```

요청 필드 조건: `reason: str` (필수)

## `PATCH /internal/training-runs/{run_id}/lease`

실행 lease 만료 시각을 갱신합니다. HTTP 200.

요청 body: 
```json
{
  "executionLeaseExpiresAt": "<ISO-8601 UTC>"
}
```

응답:
```json
{
  "data": {
    "runId": "<string>",
    "executionLeaseExpiresAt": "<ISO-8601 UTC>"
  }
}
```

요청 필드 조건: `executionLeaseExpiresAt: datetime` (필수)

## 필드 조건·공통 모델

`trainingInput.episodeCount > 0`, `trainingInput.trainingInputStatus = ready`, `trainingInput.format = ZARR_ZIP`; hash는 소문자 SHA-256 64자리입니다. `trainingInput`은 ZIP 원본이 아닌 생성된 zarr를 가리킵니다. result/error 및 진행 시각·epoch 값은 상태에 따라 null입니다.

자동 제공: `GET /openapi.json`(OpenAPI JSON), `GET /docs`(Swagger HTML), `GET /redoc`(ReDoc HTML), `GET /docs/oauth2-redirect`(Swagger 보조 HTML). 업무 API 수에 포함하지 않습니다.

### BaseModelInput

```json
{
  "skillVersionId": "<string>",
  "version": 1,
  "storageKey": "<string>",
  "sha256": "<string>",
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>"
}
```

필드: `skillVersionId: str`; `version: int`; `storageKey: str`; `sha256: str`; `architecture: str`; `framework: str`; `frameworkVersion: str`; `contractVersion: str`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### CancelTrainingRunRequest

```json
{
  "reason": "<string>"
}
```

필드: `reason: str`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### CancelTrainingRunResponse

```json
{
  "runId": "<string>",
  "status": "STARTING"
}
```

필드: `runId: str`; `status: TrainingRunStatus`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### CheckpointMetadata

```json
{
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>",
  "robotProfileId": "<string>",
  "actionSpace": "<string>",
  "actionSpec": {},
  "runtimeSpec": {},
  "nParams": 1,
  "controlRateHz": 1,
  "cameraNames": [
    "<string>"
  ],
  "inputSchema": {},
  "outputSchema": {}
}
```

필드: `architecture: str`; `framework: str`; `frameworkVersion: str`; `contractVersion: str`; `robotProfileId: str`; `actionSpace: str`; `actionSpec: dict[str, Any]`; `runtimeSpec: dict[str, Any]`; `nParams: int`; `controlRateHz: int`; `cameraNames: list[str]`; `inputSchema: dict[str, Any]`; `outputSchema: dict[str, Any]`

코드: [checkpoint.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/checkpoint.py)

### CheckpointResult

```json
{
  "checkpointStorageKey": "<string>",
  "sizeBytes": 1,
  "sha256": "<string>",
  "metadata": {
    "architecture": "<string>",
    "framework": "<string>",
    "frameworkVersion": "<string>",
    "contractVersion": "<string>",
    "robotProfileId": "<string>",
    "actionSpace": "<string>",
    "actionSpec": {},
    "runtimeSpec": {},
    "nParams": 1,
    "controlRateHz": 1,
    "cameraNames": [
      "<string>"
    ],
    "inputSchema": {},
    "outputSchema": {}
  }
}
```

필드: `checkpointStorageKey: str`; `sizeBytes: int`; `sha256: str`; `metadata: CheckpointMetadata`

코드: [checkpoint.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/checkpoint.py)

### CompletionRequest

```json
{
  "eventId": "<string>",
  "trainingJobId": "<string>",
  "attempt": 1,
  "modelCompletedAt": "<ISO-8601 UTC>",
  "result": {
    "checkpointStorageKey": "<string>",
    "sizeBytes": 1,
    "sha256": "<string>",
    "metadata": {
      "architecture": "<string>",
      "framework": "<string>",
      "frameworkVersion": "<string>",
      "contractVersion": "<string>",
      "robotProfileId": "<string>",
      "actionSpace": "<string>",
      "actionSpec": {},
      "runtimeSpec": {},
      "nParams": 1,
      "controlRateHz": 1,
      "cameraNames": [
        "<string>"
      ],
      "inputSchema": {},
      "outputSchema": {}
    }
  }
}
```

필드: `eventId: str`; `trainingJobId: str`; `attempt: int`; `modelCompletedAt: datetime`; `result: CheckpointResult`

코드: [completion.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/completion.py)

### CreateTrainingRunRequest

```json
{
  "trainingJobId": "<string>",
  "attempt": 1,
  "workerId": "<string>",
  "storageNodeId": "<string>",
  "gpuIndex": 1,
  "trainingMode": "FROM_SCRATCH",
  "baseModel": null,
  "inputSnapshotSha256": "<string>",
  "episodes": [
    {
      "inputOrder": 1,
      "episodeId": "<string>",
      "storageKey": "<string>",
      "sha256": "<string>",
      "contractVersion": "<string>"
    }
  ],
  "trainingInput": {
    "storageKey": "<string>",
    "sizeBytes": 1,
    "sha256": "<string>",
    "reportStorageKey": "<string>",
    "reportSha256": "<string>",
    "episodeCount": 1,
    "trainingInputStatus": "ready",
    "format": "ZARR_ZIP",
    "worldFrame": "<string>"
  },
  "executionLeaseExpiresAt": "<ISO-8601 UTC>"
}
```

필드: `trainingJobId: str`; `attempt: int`; `workerId: str`; `storageNodeId: str`; `gpuIndex: int`; `trainingMode: str`; `baseModel: BaseModelInput | None`; `inputSnapshotSha256: str`; `episodes: list[EpisodeInput]`; `trainingInput: TrainingInput`; `executionLeaseExpiresAt: datetime`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### CreateTrainingRunResponse

```json
{
  "runId": "<string>",
  "trainingJobId": "<string>",
  "attempt": 1,
  "status": "STARTING"
}
```

필드: `runId: str`; `trainingJobId: str`; `attempt: int`; `status: TrainingRunStatus`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### EpisodeInput

```json
{
  "inputOrder": 1,
  "episodeId": "<string>",
  "storageKey": "<string>",
  "sha256": "<string>",
  "contractVersion": "<string>"
}
```

필드: `inputOrder: int`; `episodeId: str`; `storageKey: str`; `sha256: str`; `contractVersion: str`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### RenewLeaseRequest

```json
{
  "executionLeaseExpiresAt": "<ISO-8601 UTC>"
}
```

필드: `executionLeaseExpiresAt: datetime`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### RenewLeaseResponse

```json
{
  "runId": "<string>",
  "executionLeaseExpiresAt": "<ISO-8601 UTC>"
}
```

필드: `runId: str`; `executionLeaseExpiresAt: datetime`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### RunError

```json
{
  "code": "<string>",
  "message": "<string>"
}
```

필드: `code: str`; `message: str`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### TrainingInput

```json
{
  "storageKey": "<string>",
  "sizeBytes": 1,
  "sha256": "<string>",
  "reportStorageKey": "<string>",
  "reportSha256": "<string>",
  "episodeCount": 1,
  "trainingInputStatus": "ready",
  "format": "ZARR_ZIP",
  "worldFrame": "<string>"
}
```

필드: `storageKey: str`; `sizeBytes: int`; `sha256: str`; `reportStorageKey: str`; `reportSha256: str`; `episodeCount: int`; `trainingInputStatus: str`; `format: str`; `worldFrame: str`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)

### TrainingRunResponse

```json
{
  "runId": "<string>",
  "trainingJobId": "<string>",
  "attempt": 1,
  "status": "COMPLETED",
  "sequence": 1,
  "stage": "<string>",
  "percent": 100,
  "epoch": 1,
  "totalEpochs": 1,
  "lastProgressAt": "<ISO-8601 UTC>",
  "lastProcessHeartbeatAt": "<ISO-8601 UTC>",
  "modelCompletedAt": "<ISO-8601 UTC>",
  "result": {
    "checkpointStorageKey": "<string>",
    "sizeBytes": 1,
    "sha256": "<string>",
    "metadata": {
      "architecture": "<string>",
      "framework": "<string>",
      "frameworkVersion": "<string>",
      "contractVersion": "<string>",
      "robotProfileId": "<string>",
      "actionSpace": "<string>",
      "actionSpec": {},
      "runtimeSpec": {},
      "nParams": 1,
      "controlRateHz": 1,
      "cameraNames": [
        "<string>"
      ],
      "inputSchema": {},
      "outputSchema": {}
    }
  },
  "error": null
}
```

필드: `runId: str`; `trainingJobId: str`; `attempt: int`; `status: TrainingRunStatus`; `sequence: int`; `stage: str`; `percent: int`; `epoch: int | None`; `totalEpochs: int | None`; `lastProgressAt: datetime | None`; `lastProcessHeartbeatAt: datetime | None`; `modelCompletedAt: datetime | None`; `result: CheckpointResult | None`; `error: RunError | None`

코드: [training_run.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/FastAPI_/app/schemas/training_run.py)
