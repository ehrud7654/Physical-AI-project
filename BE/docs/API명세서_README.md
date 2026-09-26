# 현재 구현 API 목록

기준: 2026-09-25 / Git `0e23128`. 현재 소스의 컨트롤러·라우트·DTO·Pydantic 모델을 기준으로 작성했습니다. 실행 중인 운영 서버 응답을 캡처한 문서는 아닙니다.

| 서버 | 직접 구현 API | 요청·응답 형식 |
|---|---:|---|
| EC2 | 71개 | [전체 명세 및 JSON](./EC2.md) |
| GPU | 1개 | [전체 명세 및 JSON](./GPU.md) |
| FastAPI | 6개 | [전체 명세 및 JSON](./FastAPI.md) |
| umi-preprocessor | 4개 | [전체 명세 및 JSON](./umi-preprocessor.md) |

각 상세 문서는 API별 한 줄 설명, 요청 body, 응답 형식, HTTP 상태, 헤더·쿼리를 포함합니다. EC2/GPU 표의 모델명을 누르면 전체 JSON 구조로 이동합니다. 문자열 자리표시자와 숫자는 형식 예시입니다. health는 FastAPI 2개·전처리 1개에 포함되며, Spring Actuator 및 자동 문서 경로는 각 문서에 별도 표기했습니다.

## EC2

| API | 수행 작업 |
|---|---|
| `POST /api/v1/auth/login` | 이메일·비밀번호를 검증하고 로그인 세션과 액세스 토큰을 발급합니다. |
| `POST /api/v1/auth/signup` | 회원 계정을 생성합니다. |
| `GET /api/v1/auth/email-availability` | 이메일 중복 여부를 조회합니다. |
| `POST /api/v1/auth/logout` | 현재 로그인 세션과 Refresh 쿠키를 폐기합니다. |
| `POST /api/v1/auth/refresh` | 쿠키로 Access Token을 재발급하고 Refresh Token을 회전합니다. |
| `POST /api/v1/auth/session/activity` | 사용자 활동에 따라 미사용 만료 시각을 갱신합니다. |
| `POST /api/v1/devices` | 수집 장치 또는 Jetson 장치를 등록합니다. |
| `GET /api/v1/devices` | 소유 장치를 조회합니다. |
| `GET /api/v1/devices/{deviceId}` | 장치 상세를 조회합니다. |
| `PATCH /api/v1/devices/{deviceId}` | 장치 이름·위치를 수정합니다. |
| `POST /api/v1/devices/{deviceId}/enrollment` | 장치 등록용 QR 정보를 발급합니다. |
| `POST /api/v1/device-auth/enroll` | 등록 토큰을 확인하고 장치 secret을 발급합니다. |
| `POST /api/v1/device-auth/token` | 장치 자격 증명으로 액세스 토큰을 발급합니다. |
| `POST /api/v1/device-auth/skills` | 장치 자격 증명으로 사용 가능한 스킬을 조회합니다. |
| `POST /api/v1/robots` | 로봇과 연결 장치를 등록합니다. |
| `GET /api/v1/robots` | 소유 로봇 목록을 조회합니다. |
| `GET /api/v1/robots/{robotId}` | 로봇 상세와 현재 상태를 조회합니다. |
| `GET /api/v1/dashboard` | 장치·학습·실행 집계와 최근 학습을 조회합니다. |
| `GET /api/v1/skills` | 사용 가능한 스킬 목록을 조회합니다. |
| `GET /api/v1/skills/overview` | 스킬별 최신 모델·학습 상태를 조회합니다. |
| `POST /api/v1/skills` | 사용자 스킬을 생성합니다. |
| `PATCH /api/v1/skills/{skillId}` | 스킬 설명을 수정합니다. |
| `GET /api/v1/skills/{skillId}/versions/latest` | 게이트를 통과한 최신 READY 모델 버전을 조회합니다. |
| `GET /api/v1/skills/{skillId}/training-requirements` | 스킬의 학습 입력 요구사항을 조회합니다. |
| `POST /api/v1/episode/data-upload-request` | Episode ZIP 업로드용 S3 Presigned URL을 발급합니다. |
| `POST /api/v1/episode/data-upload-request/{uploadSessionId}/complete` | S3 업로드 완료를 접수하고 비동기 검증을 시작합니다. |
| `POST /api/v1/episode/data-upload-request/{uploadSessionId}/renew` | 업로드 URL을 재발급합니다. |
| `DELETE /api/v1/episode/data-upload-request/{uploadSessionId}` | 업로드 세션을 취소합니다. |
| `GET /api/v1/episode/data-upload-request/by-session/{sessionId}` | 원본 sessionId로 등록·검증 상태를 조회합니다. |
| `GET /api/v1/episodes` | 소유 Episode를 조건별로 조회합니다. |
| `GET /api/v1/episodes/{episodeId}` | Episode 메타데이터·검증·보관 상세를 조회합니다. |
| `GET /api/v1/episodes/retention-policy` | 기본 보관 정책을 조회합니다. |
| `POST /api/v1/training-jobs` | 선택한 Episode로 학습 작업을 생성합니다. |
| `GET /api/v1/training-jobs` | 학습 작업 목록을 조회합니다. |
| `GET /api/v1/training-jobs/{id}` | 학습 진행 상태와 결과를 조회합니다. |
| `POST /api/v1/training-jobs/{id}/cancel` | 학습 취소를 요청합니다. |
| `GET /api/v1/training-jobs/{id}/episodes` | 학습에 연결된 Episode와 입력 순서를 조회합니다. |
| `DELETE /api/v1/training-jobs/{id}` | 종료된 학습 작업을 소프트 삭제합니다. |
| `POST /api/v1/tasks` | 작업과 선택적인 단계 구성을 생성합니다. |
| `GET /api/v1/tasks` | 작업 목록을 검색합니다. |
| `GET /api/v1/tasks/{taskId}` | 작업 상세를 조회합니다. |
| `PATCH /api/v1/tasks/{taskId}` | 작업 이름·설명·단계를 수정합니다. |
| `POST /api/v1/tasks/{taskId}/versions` | 단계 구성으로 새 작업 버전을 생성합니다. |
| `GET /api/v1/tasks/{taskId}/versions` | 작업 버전 목록을 조회합니다. |
| `GET /api/v1/tasks/{taskId}/versions/{versionId}` | 특정 작업 버전을 조회합니다. |
| `POST /api/v1/tasks/{taskId}/versions/{versionId}/validate` | 해당 로봇에서 작업을 실행할 준비가 됐는지 검증합니다. |
| `DELETE /api/v1/tasks/{taskId}` | 작업을 삭제합니다. |
| `POST /api/v1/robots/{robotId}/deployments` | 배포를 요청하고 로봇에 명령을 전달합니다. |
| `GET /api/v1/robots/{robotId}/deployments` | 배포 이력을 조회합니다. |
| `GET /api/v1/robots/{robotId}/deployments/{deploymentId}` | 배포 상세 상태를 조회합니다. |
| `POST /api/v1/robots/{robotId}/deployments/{deploymentId}/cancel` | 배포 취소를 요청합니다. |
| `POST /api/v1/robots/{robotId}/executions` | 실행을 요청하고 로봇에 명령을 전달합니다. |
| `GET /api/v1/robots/{robotId}/executions` | 실행 이력을 조회합니다. |
| `GET /api/v1/robots/{robotId}/executions/{executionId}` | 실행 상세 상태를 조회합니다. |
| `POST /api/v1/robots/{robotId}/executions/{executionId}/cancel` | 실행 취소를 요청합니다. |
| `POST /api/v1/devices/{deviceId}/heartbeat` | 장치 생존 상태와 런타임 버전을 보고합니다. |
| `GET /api/v1/robots/me/command-stream` | 로봇 명령을 SSE로 수신하고 재연결 시 재생합니다. |
| `GET /api/v1/robots/me/deployments/pending` | 대기 중 배포의 모델 다운로드 manifest를 조회합니다. |
| `POST /api/v1/robots/me/deployments/{deploymentId}/manifest` | 배포 manifest와 다운로드 URL을 갱신합니다. |
| `PATCH /api/v1/robots/me/deployments/{deploymentId}` | 모델 다운로드·검증·적용 상태를 보고합니다. |
| `PATCH /api/v1/robots/me/executions/{executionId}` | 로봇 실행 진행·완료·실패를 보고합니다. |
| `GET /api/v1/robots/me/deployment/current` | 현재 적용된 작업·스킬 배포를 조회합니다. |
| `GET /api/v1/robots/me/skills/active` | 활성 스킬 버전을 조회합니다. |
| `POST /internal/training-jobs/claim` | GPU가 작업을 할당받고 다운로드 URL·lease를 받습니다. |
| `POST /internal/training-jobs/{id}/heartbeat` | GPU가 작업 lease를 갱신하고 취소 여부를 확인합니다. |
| `POST /internal/training-jobs/{id}/events` | GPU가 학습 상태 이벤트를 보고합니다. |
| `POST /internal/training-jobs/{trainingJobId}/checkpoint-upload-session` | 체크포인트 업로드 URL과 모델 버전을 발급합니다. |
| `POST /internal/training-jobs/{trainingJobId}/checkpoint-complete` | 체크포인트 완료를 접수하고 모델 검증을 시작합니다. |
| `POST /internal/skill-versions/{id}/gate-result` | 모델 성능 평가 결과를 저장하고 게이트 통과 여부를 갱신합니다. |
| `POST /internal/storage-nodes/register` | GPU 저장 노드를 등록하거나 갱신합니다. |
| `POST /internal/storage-nodes/{id}/heartbeat` | 저장 노드의 용량·생존·작업 수를 갱신합니다. |

## GPU Spring

| API | 수행 작업 |
|---|---|
| `POST /internal/training-runs/{runId}/completion` | FastAPI 완료 콜백을 받아 체크포인트를 업로드하고 EC2에 보고합니다. |

## FastAPI

| API | 수행 작업 |
|---|---|
| `GET /health/live` | 프로세스 생존 여부를 확인합니다. |
| `GET /health/ready` | 공유 저장소 쓰기·삭제 가능 여부를 확인합니다. |
| `POST /internal/training-runs` | zarr 입력으로 학습 프로세스를 실행합니다. |
| `GET /internal/training-runs/{run_id}` | 학습 상태·진행률·체크포인트 결과를 조회합니다. |
| `POST /internal/training-runs/{run_id}/cancel` | 학습 프로세스 취소를 요청합니다. |
| `PATCH /internal/training-runs/{run_id}/lease` | 실행 lease 만료 시각을 갱신합니다. |

## umi-preprocessor

| API | 수행 작업 |
|---|---|
| `GET /health` | 전처리 서버 생존을 확인합니다. |
| `POST /internal/v1/jobs` | Mapping·Demonstration 입력으로 zarr 생성을 시작합니다. |
| `GET /internal/v1/jobs/{job_id}` | 전처리 상태와 산출물 경로·해시를 조회합니다. |
| `POST /internal/v1/jobs/{job_id}/cancel` | 전처리 및 ORB SLAM 프로세스를 취소합니다. |

## HTTP 오류 상태 및 대표 오류 코드

2026-09-25 현재 소스의 예외 처리와 실제 HTTP 반환 경로를 기준으로 정리했습니다. 아래 코드는 대표 사례이며, 서버 이름이 붙은 코드는 해당 서버에서 반환합니다. **고정 코드 없음**은 HTTP 오류가 발생해도 공통 `code` 필드가 보장되지 않는다는 뜻입니다.

| HTTP | 대표 오류 코드 | 의미 |
|---|---|---|
| 400 | 고정 코드 없음 | EC2/GPU의 JSON·필수 헤더·파라미터 바인딩 오류 등 프레임워크 오류. umi-preprocessor는 잘못된 경로·설정 또는 RAW 입력 누락을 `detail`로 반환합니다. |
| 401 | EC2: `INVALID_CREDENTIALS`, `INVALID_ENROLLMENT_TOKEN`, `AUTH_SESSION_EXPIRED`, `REFRESH_TOKEN_REUSED` / FastAPI: `UNAUTHORIZED` | 로그인·장치 인증 실패, 세션 만료, Refresh Token 재사용 또는 서비스 토큰 누락. Spring Security의 JWT 인증 실패와 GPU 콜백 인증 실패는 별도 고정 JSON 코드가 없습니다. |
| 403 | EC2·FastAPI: `FORBIDDEN` / FastAPI: `WORKER_SCOPE_MISMATCH` | 업로드 주체 제한, 잘못된 FastAPI 서비스 토큰 또는 worker·StorageNode 범위 불일치. EC2 권한 검사 및 세션 Origin·요청 헤더 검사에서 발생하는 403은 고정 JSON 코드가 보장되지 않습니다. |
| 404 | EC2: `RESOURCE_NOT_FOUND`, `DEVICE_NOT_FOUND` / FastAPI: `RUN_NOT_FOUND` | 리소스가 없거나 소유 범위 밖입니다. umi-preprocessor의 작업 조회 실패는 `detail: "job not found"`이며, 미등록 경로 등 프레임워크 404와는 응답 형식이 다를 수 있습니다. |
| 405 | 고정 코드 없음 | 해당 경로에서 지원하지 않는 HTTP 메서드입니다. 프레임워크 기본 응답을 사용합니다. |
| 409 | EC2: `STATE_CONFLICT`, `ROBOT_BUSY`, `ROBOT_OFFLINE`, `LEASE_EXPIRED`, `SKILL_VERSION_NOT_READY`, `SKILL_VERSION_GATE_FAILED`, `EPISODE_INVALID`, `EPISODE_NOT_AVAILABLE`, `EPISODE_ID_REUSED`, `MODEL_INCOMPATIBLE`, `EMAIL_ALREADY_EXISTS`, `DEVICE_ALREADY_REGISTERED`, `ENROLLMENT_NOT_ACTIVE`, `IDEMPOTENCY_KEY_REUSED`, `INVALID_CHECKPOINT_PACKAGE`, `CHECKPOINT_METADATA_MISMATCH`, `CHECKPOINT_RESERVATION_CONFLICT`, `OBJECT_SIZE_MISMATCH`, `OBJECT_CHECKSUM_MISMATCH`, `STORAGE_NODE_STALE`, `STORAGE_NODE_UNAVAILABLE`, `INSUFFICIENT_STORAGE`, `INSUFFICIENT_GPU_MEMORY`, `WORKER_BUSY`, `GPU_BUSY` / FastAPI: `RUN_REQUEST_CONFLICT`, `RUN_STATE_CONFLICT`, `EXECUTION_LEASE_EXPIRED`, `GPU_BUSY` | 상태·중복·임대·자원 할당 충돌. EC2는 일부 입력·모델 검증 실패도 `BusinessConflictException`으로 처리하여 409를 반환합니다. umi-preprocessor는 이미 실행 중인 작업 또는 취소된 작업의 재요청에 코드 없이 `detail`을 반환합니다. |
| 410 | EC2: `UPLOAD_SESSION_EXPIRED`, `ENROLLMENT_EXPIRED` | 업로드 세션 또는 장치 등록 요청이 만료되었습니다. |
| 413 | EC2: `PAYLOAD_TOO_LARGE` | Episode 업로드 URL 발급 요청의 `sizeBytes`가 서버의 최대 업로드 크기를 초과했습니다. 현재 GPU ingest 정책 오류로 구현된 항목은 아닙니다. |
| 415 | 고정 코드 없음 | EC2/GPU에서 요청의 Content-Type을 처리할 수 없는 경우 등 프레임워크 오류입니다. |
| 422 | EC2: `VALIDATION_ERROR`, `CHECKSUM_MISMATCH`, `UNSUPPORTED_DEVICE_TYPE` / FastAPI: `VALIDATION_ERROR`, `GPU_INDEX_INVALID`, `INVALID_TRAINING_MODE`, `BASE_MODEL_REQUIRED`, `BASE_MODEL_NOT_ALLOWED`, `BASE_MODEL_HASH_MISMATCH`, `INPUT_ORDER_DUPLICATED`, `STORAGE_KEY_INVALID`, `EPISODE_FILE_NOT_FOUND`, `EPISODE_HASH_MISMATCH`, `TRAINING_INPUT_SIZE_MISMATCH`, `TRAINING_INPUT_HASH_MISMATCH`, `TRAINING_REPORT_HASH_MISMATCH`, `TRAINING_REPORT_INVALID`, `TRAINING_INPUT_NOT_READY`, `TRAINING_INPUT_EPISODE_MISMATCH`, `TRAINING_INPUT_WORLD_FRAME_MISMATCH` | DTO·입력 파일·해시·전처리 결과·학습 설정 검증 실패. umi-preprocessor의 요청 모델 검증 오류는 `code` 없이 `detail` 배열로 반환됩니다. |
| 429 | 미구현: `RATE_LIMITED` | 네 서버의 현재 애플리케이션 코드에는 요청 제한 및 `Retry-After` 반환 처리가 구현되어 있지 않습니다. 프록시 등 외부 계층의 제한은 별도입니다. |
| 500 | 고정 코드 없음 (`INTERNAL_ERROR` 공통 응답 미구현) | 처리되지 않은 예외는 각 프레임워크 기본 오류 응답으로 반환됩니다. GPU 완료 콜백의 처리되지 않은 상태 충돌, FastAPI readiness 저장소 점검 실패도 현재 별도 코드·상태 매핑이 없습니다. |
| 503 | EC2: `AUTH_SESSION_UNAVAILABLE`, `RUNTIME_STATE_UNAVAILABLE` | Redis 로그인 세션 또는 장치·저장 노드의 런타임 상태 저장소에 접근할 수 없습니다. 기존 표의 GPU·S3·학습 프로필 관련 503 코드로 일괄 변환하는 처리는 없습니다. |

### 서버별 오류 응답 형식

| 서버 | 구현된 형식 |
|---|---|
| EC2 | `GlobalExceptionHandler`가 처리하는 업무 예외는 `{"code":"오류 코드","message":"오류 설명"}`입니다. 인증 필터·프레임워크 오류는 이 형식이 보장되지 않습니다. 인증 필터의 Redis 장애는 같은 형식의 `AUTH_SESSION_UNAVAILABLE`을 반환합니다. |
| GPU Spring | 공통 서버 예외 핸들러가 없어 Spring 기본 오류 응답을 사용합니다. `ApiErrorResponse`는 EC2 오류를 읽기 위한 모델이며, GPU 전체 오류를 해당 형식으로 반환한다는 의미는 아닙니다. |
| FastAPI | 도메인·요청 검증·명시적인 FastAPI `HTTPException`은 `{"error":{"code":"오류 코드","message":"오류 설명"}}`입니다. HTTP 예외의 코드는 401이면 `UNAUTHORIZED`, 403이면 `FORBIDDEN`, 그 외에는 `HTTP_ERROR`입니다. 처리되지 않은 예외나 라우터 기본 오류까지 이 형식을 보장하지는 않습니다. |
| umi-preprocessor | 명시적인 HTTP 예외는 `{"detail":"오류 설명"}`, 요청 모델 검증 오류는 `{"detail":[...]}`입니다. 별도 업무 오류 코드 체계는 없습니다. |
