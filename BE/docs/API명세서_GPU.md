# GPU Spring 백엔드 구현 API

기준: 2026-09-25, Git `f3af673`. 성공 JSON은 `{"data": <응답 모델>}`입니다.

## FastAPI 콜백

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /internal/training-runs/{runId}/completion` | FastAPI 학습 완료를 받아 체크포인트를 S3에 올리고 EC2에 완료를 보고합니다. | [FastApiTrainingModels.CompletionRequest](#gpu--fastapitrainingmodels-completionrequest) | [TrainingCompletionOrchestrator.CompletionResponse](#gpu--trainingcompletionorchestrator-completionresponse) | 202 |

## 헤더·쿼리·분기

- **`POST /internal/training-runs/{runId}/completion`** — Authorization: Bearer <FastAPI callback token>

## 프레임워크 제공 경로

- `GET /actuator/health`: 상태 확인, 정상 예시 `{"status":"UP"}`.
- `GET /actuator/info`: actuator 노출 설정에는 포함되지만 현재 SecurityConfig의 denyAll로 접근이 차단됩니다. 호출 가능한 업무 API로 집계하지 않습니다.

EC2 claim/heartbeat/events, FastAPI 학습 실행, 전처리 요청은 이 서버가 **호출하는 API**이며 GPU Spring에 별도로 열린 경로가 아닙니다.

## 요청·응답 JSON 모델

문자열 자리표시자(`<uuid>`, `<string>`, `<ISO-8601 UTC>`)와 숫자는 형식을 보여주는 값이며 실제 데이터가 아닙니다. 응답 모델은 `data` 내부 형식입니다. `{}`는 자유 형식 객체, 재귀 `children: []`는 같은 단계 모델의 배열입니다. 필드별 null 허용 여부는 DTO 및 서비스 조건을 함께 따릅니다.

<a id="gpu--checkpointmodels-metadata"></a>

### CheckpointModels.Metadata

```json
{
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>",
  "robotProfileId": "<uuid>",
  "actionSpace": "JOINT_ABSOLUTE",
  "actionSpec": {},
  "runtimeSpec": {},
  "nParams": 1,
  "controlRateHz": 0.5,
  "cameraNames": [
    "<string>"
  ],
  "inputSchema": {},
  "outputSchema": {}
}
```

필드 타입: `architecture: String`, `framework: String`, `frameworkVersion: String`, `contractVersion: String`, `robotProfileId: UUID`, `actionSpace: ActionSpace`, `actionSpec: Map<String, Object>`, `runtimeSpec: Map<String, Object>`, `nParams: long`, `controlRateHz: BigDecimal`, `cameraNames: List<String>`, `inputSchema: Map<String, Object>`, `outputSchema: Map<String, Object>`

코드: [CheckpointModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/src/main/java/com/anssa8bu/ssafy_gpu/checkpoint/dto/CheckpointModels.java)

<a id="gpu--fastapitrainingmodels-checkpointresult"></a>

### FastApiTrainingModels.CheckpointResult

```json
{
  "checkpointStorageKey": "<string>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "metadata": {
    "architecture": "<string>",
    "framework": "<string>",
    "frameworkVersion": "<string>",
    "contractVersion": "<string>",
    "robotProfileId": "<uuid>",
    "actionSpace": "JOINT_ABSOLUTE",
    "actionSpec": {},
    "runtimeSpec": {},
    "nParams": 1,
    "controlRateHz": 0.5,
    "cameraNames": [
      "<string>"
    ],
    "inputSchema": {},
    "outputSchema": {}
  }
}
```

필드 타입: `checkpointStorageKey: String`, `sizeBytes: long`, `sha256: String`, `metadata: Metadata`

코드: [FastApiTrainingModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/src/main/java/com/anssa8bu/ssafy_gpu/fastapi/dto/FastApiTrainingModels.java)

<a id="gpu--fastapitrainingmodels-completionrequest"></a>

### FastApiTrainingModels.CompletionRequest

```json
{
  "eventId": "<string>",
  "trainingJobId": "<uuid>",
  "attempt": 1,
  "modelCompletedAt": "<ISO-8601 UTC>",
  "result": {
    "checkpointStorageKey": "<string>",
    "sizeBytes": 1,
    "sha256": "<소문자 SHA-256 64자리>",
    "metadata": {
      "architecture": "<string>",
      "framework": "<string>",
      "frameworkVersion": "<string>",
      "contractVersion": "<string>",
      "robotProfileId": "<uuid>",
      "actionSpace": "JOINT_ABSOLUTE",
      "actionSpec": {},
      "runtimeSpec": {},
      "nParams": 1,
      "controlRateHz": 0.5,
      "cameraNames": [
        "<string>"
      ],
      "inputSchema": {},
      "outputSchema": {}
    }
  }
}
```

필드 타입: `eventId: String`, `trainingJobId: UUID`, `attempt: int`, `modelCompletedAt: Instant`, `result: CheckpointResult`

코드: [FastApiTrainingModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/src/main/java/com/anssa8bu/ssafy_gpu/fastapi/dto/FastApiTrainingModels.java)

<a id="gpu--trainingcompletionorchestrator-completionresponse"></a>

### TrainingCompletionOrchestrator.CompletionResponse

```json
{
  "skillVersionId": "<uuid>",
  "version": 1,
  "status": "<string>"
}
```

필드 타입: `skillVersionId: java.util.UUID`, `version: int`, `status: String`

코드: [TrainingCompletionOrchestrator.java](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/src/main/java/com/anssa8bu/ssafy_gpu/fastapi/application/TrainingCompletionOrchestrator.java)


## 참조 enum 값

- `ActionSpace`: `JOINT_ABSOLUTE`, `JOINT_DELTA`, `EEF_RELATIVE_ROT6D`