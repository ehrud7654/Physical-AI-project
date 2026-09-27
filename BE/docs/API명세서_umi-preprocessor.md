# umi-preprocessor 구현 API

기준: 2026-09-25, Git `f3af673`. 호출 주체는 GPU Spring입니다. JSON에 `data` 래퍼가 없으며, 요청은 **snake_case**입니다. 현재 서버 코드에는 API 인증 검사가 없습니다.

## `GET /health`

전처리 HTTP 서버의 생존 여부를 확인합니다. HTTP 200.

요청 body: 없음.

응답:

```json
{"status":"ok"}
```

## `POST /internal/v1/jobs`

공유 디렉터리의 Mapping·Demonstration 데이터를 처리해 학습용 zarr를 생성합니다. HTTP 202.

요청 body — Mapping 세션 사용:

```json
{
  "job_id": "<job-id>",
  "dataset_name": "dataset",
  "calibration_profile": "s22_20260921",
  "require_mapping": true,
  "mapping_session_id": "<mapping-session-id>",
  "demonstration_session_ids": ["<demonstration-session-id>"],
  "external_atlas_path": null,
  "external_alignment_path": null,
  "external_atlas_sha256": null,
  "external_alignment_sha256": null
}
```

외부 Atlas 사용 시 `mapping_session_id=null`로 보내고 다음 네 필드를 채웁니다.

```json
{
  "external_atlas_path": "<job 디렉터리 기준 atlas 상대 경로>",
  "external_alignment_path": "<job 디렉터리 기준 alignment 상대 경로>",
  "external_atlas_sha256": "<SHA-256>",
  "external_alignment_sha256": "<SHA-256>"
}
```

`job_id`는 필수이며, 나머지 모델 기본값은 위 값 또는 null입니다. 다만 실행 검증에서 `require_mapping=true`, 비어 있지 않은 `demonstration_session_ids`, Mapping 또는 외부 Atlas 지정이 필요합니다. 입력 파일은 미리 `<JOBS_ROOT>/<job_id>/raw` 등에 준비되어 있어야 합니다.

신규 접수 응답:

```json
{
  "jobId": "<job-id>",
  "status": "accepted"
}
```

동일 job이 실행 중이면 HTTP 202로 현재 상태 파일 내용을 반환합니다. 다른 job 실행 중 또는 취소된 job이면 HTTP 409입니다.

## `GET /internal/v1/jobs/{job_id}`

전처리 상태와 생성된 산출물 경로·해시를 조회합니다. HTTP 200; job 디렉터리가 없으면 404.

요청 body: 없음.

상태 파일 생성 전:

```json
{"status":"not_started"}
```

실행 중:

```json
{
  "status": "running",
  "startedUtc": "<ISO-8601 UTC>"
}
```

완료 응답:

```json
{
  "status": "ready",
  "startedUtc": "<ISO-8601 UTC>",
  "completedUtc": "<ISO-8601 UTC>",
  "input": {
    "root": "<job 절대 경로>",
    "demonstration_sessions": 2,
    "demonstration_session_ids": ["<demo-session-1>", "<demo-session-2>"],
    "mapping_sessions": 1,
    "mapping_session_id": "<mapping-session-id>",
    "role_source": "mapping_session_id",
    "success_sessions": 2,
    "mapping_outcome_is_training_label": false
  },
  "report": "<job>/pipeline_report.json",
  "artifact": {
    "schema_version": 1,
    "dataset": "<절대 경로>/dataset.zarr.zip",
    "dataset_sha256": "<SHA-256>",
    "report": "<절대 경로>/dataset.zarr.report.json",
    "report_sha256": "<SHA-256>",
    "manifest": "<절대 경로>/dataset_gpu_input.manifest.json",
    "manifest_sha256": "<SHA-256>",
    "archive": "<절대 경로>/dataset_gpu_input.tgz",
    "archive_sha256": "<SHA-256>"
  },
  "workerExitCode": 0
}
```

`workerExitCode`는 이 서버가 추적 중인 프로세스가 종료됐을 때만 추가됩니다. 외부 Atlas를 사용하면 `mapping_session_id`는 null, `role_source`는 `external_atlas`입니다. HTTP 결과에 zarr Episode 개수 자체는 없으며, GPU Spring이 `artifact.report`의 `episodes`/`episode_ends`를 읽습니다.

실패 응답 — job 상태 조회 자체는 HTTP 200:

```json
{
  "status": "failed",
  "startedUtc": "<ISO-8601 UTC>",
  "completedUtc": "<ISO-8601 UTC>",
  "error": {
    "type": "RuntimeError",
    "message": "<실제 예외 메시지>"
  }
}
```

실패 단계에 따라 `input`과 `workerExitCode`도 포함될 수 있습니다.

## `POST /internal/v1/jobs/{job_id}/cancel`

취소 의도를 기록하고 실행 중인 전처리·SLAM 프로세스들을 종료합니다. HTTP 204.

요청 body: 없음. 응답 body: 없음. 이후 상태 조회의 `status`는 **`cancelled`**이며 기존 상태 필드는 유지됩니다.

## 공통 오류·자동 제공 경로

HTTP 400/404/409:

```json
{"detail":"<오류 메시지>"}
```

Pydantic 요청 형식 오류 HTTP 422:

```json
{
  "detail": [
    {"type":"missing","loc":["body","job_id"],"msg":"Field required","input":{}}
  ]
}
```

자동 제공: `GET /openapi.json`(OpenAPI JSON), `GET /docs`(Swagger HTML), `GET /redoc`(ReDoc HTML), `GET /docs/oauth2-redirect`(Swagger 보조 HTML). 위 4개 API 집계에 포함하지 않습니다.

코드: [server.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/umi_server_preprocessor/app/server.py), [run_preprocess.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/umi_server_preprocessor/app/run_preprocess.py), [packaging.py](/D:/Eunchan_/Git_/S15P21A103/BE/GPU_/umi_server_preprocessor/app/src/umi_preprocessor/packaging.py).
