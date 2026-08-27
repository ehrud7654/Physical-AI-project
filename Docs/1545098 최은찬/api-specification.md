# UMI 로봇팔 작업 학습 플랫폼 API 명세서

> 기능별 설명·완료 기준·중요도(P0/P1/P2)는 [표 형식 기능명세서](functional-specification.md)를 참고한다. 이 문서는 API 요청·응답 상세를 유지한다.

- 문서 버전: 0.1.0 (구현 전 초안)
- 작성일: 2026-08-27
- 기준: 제공된 시스템 아키텍처 및 앞서 제안한 API 목록
- 범위: 웹 클라이언트, EC2 백엔드, UMI 장치, GPU 워커, Jetson 간 인터페이스
- 이 문서는 구현 결과가 아닌 제안 명세다. 인증 방식, 저장소, 학습 정책 등은 아래 가정을 팀과 확정해야 한다.

## 1. 설계 가정과 범위

| 항목 | 초안의 결정 |
|---|---|
| API 접두사 | `/api/v1`; 내부 API는 `/api/v1/internal` |
| 전송 | HTTPS, JSON; 이벤트 스트림은 SSE |
| 사용자 인증 | Bearer 액세스 토큰 + HttpOnly refresh 쿠키 |
| 장치 인증 | UMI 및 Jetson별 자격 증명으로 장치 토큰 발급 |
| GPU 인증 | 별도 발급한 서비스 토큰; 발급·회전은 운영 절차로 관리 |
| 소유 범위 | 사용자 → UMI → 시연 데이터/학습/모델. Jetson은 사용자 소유 로봇에 등록 |
| 파일 | 비공개 파일 저장소에 저장하고 제한 시간 업로드/다운로드 URL 발급 |
| 큐 | 파일 검증 완료 이벤트를 전달; 중복 전달 가능성을 전제로 소비자가 중복 제거 |
| 학습 실행 | 사용자가 명시적으로 요청. GPU는 EC2에서 작업을 claim |
| 작업 단위 | 영상·IMU·메타데이터를 `episode`로 묶고, 여러 episode를 한 학습 실행에 포함 |
| 최신 모델 | 생성 시각상 마지막 파일이 아닌, 사용 가능한 배포 버전 중 최신 버전 |
| Scratch | 작업 흐름 저장·검증과 상위 실행 명령을 지원하는 선택 기능 |
| Jetson 명령 전달 | Jetson이 heartbeat 응답에서 배포·실행·중지 명령 수신 |
| 로봇 등록 | MVP에서는 운영자가 로봇 ID, 소유자, 장치 자격 증명을 사전 등록. 셀프 등록 API는 범위 밖 |

### 구현 전 확정할 사항

1. 저장소 제품, 업로드 크기 제한, 보관 기간, 멀티파트 업로드 필요 여부.
2. IMU 파일 스키마, 영상 코덱, 시간 동기화 방식, 좌표계·보정 데이터.
3. 지원 학습 알고리즘, 설정 범위, GPU 자원 배정, 자동 학습 여부.
4. 체크포인트에서 Jetson 실행 파일로 변환하는 주체와 지원 런타임.
5. 회원가입 공개 여부, 회원 탈퇴·장치 해제·데이터 삭제 정책.
6. 로봇 안전 조건, 통신 두절 대응, 로컬 비상 정지 및 실행 권한.

## 2. 공통 규칙

### 2.1 인증 및 권한 표기

| 표기 | 호출 주체 | 허용 범위 |
|---|---|---|
| 공개 | 인증 전 사용자/장치 | 로그인·회원가입·장치 인증만 |
| 사용자 | `Authorization: Bearer <userAccessToken>` | 자신 소유 리소스 |
| UMI | `Authorization: Bearer <deviceAccessToken>` | 토큰에 지정된 UMI 및 그 데이터 |
| GPU | `Authorization: Bearer <serviceAccessToken>` | 할당받은 작업과 파일 |
| Jetson | `Authorization: Bearer <deviceAccessToken>` | 연결된 robot 및 승인된 배포/실행 |

핸들 번호는 식별자이지 인증 수단이 아니다. 경로·본문의 ID가 토큰 소유 범위와 일치하는지 서버에서 검사한다. 다른 사용자 리소스는 존재 여부가 드러나지 않도록 `404`로 응답한다. 내부 API는 서비스 인증에 더해 네트워크 접근을 제한한다.

### 2.2 요청·응답

- JSON 요청은 `Content-Type: application/json`을 사용한다.
- ID는 서버 발급 불투명 문자열이다. 예시의 `ep_001` 등은 설명용이다.
- 시간은 RFC 3339 UTC 문자열, 예: `2026-08-27T01:00:00Z`.
- 필드 표에서 `필수`가 아닌 요청 필드는 선택이다. `?`는 선택, `| null`은 명시적 null 허용이다.
- PATCH는 전달된 필드만 변경한다. 허용되지 않은 필드는 `422`로 거부한다.
- 성공 응답은 `{ "data": ... }`, 목록은 `{ "data": [...], "page": { "nextCursor": null, "hasNext": false } }`.
- `204`는 응답 본문이 없다. 파일 저장소 직접 PUT/GET은 이 JSON 규칙을 적용하지 않는다.
- 목록 공통 쿼리: `cursor?: string`, `limit?: integer` (기본 20, 범위 1~100). 기본 정렬은 `createdAt DESC, id DESC`.
- 모든 응답에 `X-Request-Id`를 반환한다. 민감한 토큰과 서명 URL은 로그에서 마스킹한다.
- 큰 파일 처리·학습·배포·실행은 비동기 처리한다. 요청 접수와 작업 완료를 구분한다.

### 2.3 멱등성·동시성

- episode 생성, upload-complete, 학습 생성/재시도, 배포 생성, 실행 생성은 `Idempotency-Key` 필수다.
- 키는 호출 주체와 API 경로 범위에서 구분하며 기본 보관 기간은 24시간이다. 동일 키·동일 본문은 최초 상태 코드와 응답을 재사용하고, 다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`다.
- `(deviceId, clientEpisodeId)`는 영구 유일하다. 같은 시연을 다시 등록하면 동일 내용은 기존 episode를 `200`으로 반환하고, 다른 내용은 `409`다.
- GPU 이벤트는 `(jobId, attempt, eventId)`, 장치 보고는 각 `reportId`로 중복 제거한다.
- 워크플로 수정은 `expectedRevision`이 현재 revision과 다르면 `409 REVISION_CONFLICT`다.
- 중복 명령 전달을 허용하되 Jetson은 `commandId`와 `executionId`를 지속 저장하여 같은 작업을 다시 실행하지 않는다.

### 2.4 오류

```json
{
  "error": {
    "code": "EPISODE_NOT_READY",
    "message": "검증이 완료되지 않은 시연 데이터가 포함되어 있습니다.",
    "details": [{ "field": "episodeIds", "reason": "ep_002 is VALIDATING" }],
    "requestId": "req_001"
  }
}
```

| HTTP | 대표 code | 의미 |
|---|---|---|
| 400 | `INVALID_REQUEST` | JSON 파싱·쿼리 형식 오류 |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS` | 인증 실패 |
| 403 | `FORBIDDEN` | 인증 주체의 역할에 허용되지 않은 기능 |
| 404 | `RESOURCE_NOT_FOUND` | 리소스 없음 또는 소유 범위 밖 |
| 409 | `STATE_CONFLICT`, `REVISION_CONFLICT`, `LEASE_EXPIRED`, `ROBOT_BUSY` | 상태·동시성 충돌 |
| 413 | `PAYLOAD_TOO_LARGE` | 업로드 정책상 허용 크기 초과 |
| 422 | `VALIDATION_ERROR`, `MODEL_INCOMPATIBLE` | 필드 또는 호환성 검증 실패 |
| 429 | `RATE_LIMITED` | 요청 제한; `Retry-After` 반환 |
| 500 | `INTERNAL_ERROR` | 예기치 않은 서버 오류 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 저장소 등 필수 의존 서비스 사용 불가 |

각 API에 적은 오류는 추가적인 주요 오류이며, 위 공통 오류도 적용한다. GPU 부족만으로 학습 생성을 실패시키지 않고 QUEUED 상태로 유지한다.

## 3. 공통 응답 모델

아래 타입 표기는 JSON 구조를 설명하기 위한 것이며 실제 응답은 JSON이다. 응답 필드의 `?`는 해당 단계에서 생략될 수 있음을 뜻한다.

```typescript
type User = {
  id: string; email: string; name: string; createdAt: string;
};
type Device = {
  id: string; handleNumber: string; name: string;
  connectionStatus: 'ONLINE' | 'OFFLINE';
  lastSeenAt: string | null; createdAt: string;
};
type FileDescriptor = {
  id: string; type: 'VIDEO' | 'IMU' | 'CALIBRATION';
  filename: string; contentType: string; sizeBytes: number; sha256: string;
};
type Episode = {
  id: string; deviceId: string; clientEpisodeId: string;
  taskName: string; recordedAt: string; createdAt: string;
  status: 'REGISTERED' | 'UPLOADING' | 'VALIDATING' | 'READY' | 'INVALID';
  files: FileDescriptor[]; metadata: EpisodeMetadata;
  validationErrors: { fileId: string | null; code: string; message: string }[];
};
type EpisodeMetadata = {
  schemaVersion: '1'; durationSeconds: number; fps: number;
  imuSamplingRateHz: number;
  timeSync: {
    clockDomain: string; timestampUnit: 'ns' | 'us' | 'ms';
    videoStartTimestamp: string; imuStartTimestamp: string;
    imuToVideoOffsetNs: string;
  };
  coordinateFrame: string; calibrationFileId?: string;
};
type TrainingJob = {
  id: string; deviceId: string; episodeIds: string[];
  baseModelVersionId: string | null; config: TrainingConfig;
  status: 'QUEUED' | 'PREPARING' | 'RUNNING' | 'CANCEL_REQUESTED'
        | 'CANCELED' | 'SUCCEEDED' | 'FAILED';
  attempt: number; retryOfJobId: string | null;
  progress: { stage: string; percent: number; epoch: number | null;
              totalEpochs: number; receivedEpisodeCount: number };
  modelVersionId: string | null;
  error: { code: string; message: string } | null;
  createdAt: string; startedAt: string | null; finishedAt: string | null;
};
type TrainingConfig = {
  algorithm: string; epochs: number; batchSize: number; seed?: number;
};
type Model = { id: string; deviceId: string; name: string; createdAt: string };
type ModelVersion = {
  id: string; modelId: string; trainingJobId: string; version: number;
  status: 'PROCESSING' | 'READY' | 'FAILED'; createdAt: string;
  deployable: boolean;
  artifacts: { id: string; kind: 'CHECKPOINT' | 'DEPLOYMENT' | 'METRICS';
    format: string; sizeBytes: number; sha256: string;
    compatibility: { architecture: string; runtime: string;
      runtimeVersion: string; inputSchemaVersion: string } | null }[];
};
type Deployment = {
  id: string; robotId: string; modelVersionId: string; artifactId: string;
  status: 'PENDING' | 'DOWNLOADING' | 'VERIFYING' | 'APPLYING' | 'SUCCEEDED' | 'FAILED';
  progressPercent: number; error: { code: string; message: string } | null;
  createdAt: string; updatedAt: string;
};
type Workflow = {
  id: string; name: string; schemaVersion: '1'; revision: number;
  graph: WorkflowGraph; createdAt: string; updatedAt: string;
};
type WorkflowGraph = {
  entryNodeId: string;
  nodes: { id: string; type: 'RUN_POLICY' | 'WAIT';
    params: { modelVersionId?: string; durationMs?: number; timeoutMs?: number } }[];
  edges: { from: string; to: string }[];
};
type Execution = {
  id: string; robotId: string; workflowId: string; workflowRevision: number;
  status: 'PENDING' | 'RUNNING' | 'STOP_REQUESTED' | 'STOPPED' | 'SUCCEEDED' | 'FAILED';
  currentNodeId: string | null; createdAt: string;
  startedAt: string | null; finishedAt: string | null;
  error: { code: string; message: string } | null;
};
```

`sha256`은 소문자 16진수 64자리다. 나노초 타임스탬프는 JavaScript 정밀도 손실을 피하도록 문자열로 전달한다. 본 초안에서 시간 변환은 `video 기준 IMU 시각(ns) = IMU 원본 시각을 ns로 변환한 값 + imuToVideoOffsetNs`이며 실제 동기화 방식은 장치팀과 확정한다.

## 4. 인증 및 사용자 API

### AUTH-01 로그인

- `POST /auth/login` · 공개
- 요청: `email: string` 필수, `password: string` 필수.
- 성공 `200`: `data = { accessToken, tokenType: "Bearer", expiresIn: 900, user: User }`.
- 응답 쿠키: `refreshToken=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth`.
- 실패: `401 INVALID_CREDENTIALS`, `429 RATE_LIMITED`.
- 초안 만료 정책: 액세스 토큰 15분, refresh 세션 14일. 비밀번호 및 토큰 원문 저장·로그 기록 금지.

### AUTH-02 로그아웃

- `POST /auth/logout` · refresh 쿠키로 현재 세션 식별. 액세스 토큰이 만료되어도 허용.
- 요청 본문 없음. 현재 refresh 세션 폐기 및 쿠키 삭제.
- 성공 `204`. 이미 로그아웃했어도 `204`.
- 액세스 토큰은 만료 시까지 유효할 수 있다. 즉시 차단이 필요하면 세션 폐기 조회를 추가한다.

### AUTH-03 토큰 갱신

- `POST /auth/refresh` · refresh 쿠키 필수, 요청 본문 없음.
- 성공 `200`: `data = { accessToken, tokenType: "Bearer", expiresIn: 900 }`; refresh 토큰 회전.
- 실패 `401 UNAUTHENTICATED`. 회전 이전 토큰 재사용 감지 시 해당 세션 폐기.
- 쿠키를 사용하는 refresh/logout은 허용 Origin 검사로 CSRF를 방지한다. 웹/API의 사이트 구성이 다르면 쿠키·CSRF 정책 재설계가 필요하다.

### AUTH-04 회원가입 (선택)

- `POST /auth/signup` · 공개
- 요청: `email: string` 필수, `password: string` 필수, `name: string` 필수(1~50자).
- 성공 `201`: `User`. 실패 `409 EMAIL_ALREADY_EXISTS`, `422 VALIDATION_ERROR`.
- 비밀번호 정책과 이메일 인증 흐름은 별도 확정. 비밀번호 해시는 검증된 라이브러리로 처리한다.

### USER-01 내 정보 조회

- `GET /users/me` · 사용자
- 성공 `200`: `User`.

### USER-02 내 정보 수정

- `PATCH /users/me` · 사용자
- 요청: `name: string` 필수(1~50자).
- 성공 `200`: 수정된 `User`. 이메일·비밀번호 변경은 이 API에서 지원하지 않는다.

## 5. UMI 장치 관리 API

### DEVICE-01 장치 등록

- `POST /devices` · 사용자
- 요청: `handleNumber: string`, `registrationCode: string`, `name: string` 모두 필수.
- 성공 `201`: `Device`.
- 실패: `409 DEVICE_ALREADY_REGISTERED`, `422 INVALID_REGISTRATION_CODE`.
- 일회용 등록 코드는 사전 프로비저닝된 장치와 연결된다. 회원 등록 API는 장치 인증용 secret을 반환하지 않는다.

### DEVICE-02 내 장치 목록

- `GET /devices` · 사용자
- 쿼리: 공통 페이지네이션.
- 성공 `200`: `Device[]` 목록.

### DEVICE-03 장치 상세

- `GET /devices/{deviceId}` · 사용자
- 성공 `200`: `Device`.

### DEVICE-04 장치 정보 수정

- `PATCH /devices/{deviceId}` · 사용자
- 요청: `name: string` 필수(1~100자).
- 성공 `200`: 수정된 `Device`. 소유자·핸들 번호 변경은 불가.

### DEVICE-05 장치 토큰 발급

- `POST /device-auth/token` · 공개 경로, 자격 증명 검증 필수
- 요청: `deviceId: string`, `deviceSecret: string` 모두 필수. Jetson도 사전 등록한 장치 ID 사용.
- 성공 `200`: `data = { accessToken, tokenType: "Bearer", expiresIn: 900, deviceType: "UMI" | "JETSON" }`.
- 실패 `401 INVALID_CREDENTIALS`. secret은 본문·추적 로그에 남기지 않는다.
- 장치 유형과 접근 범위는 서버 등록 정보로 결정하며 클라이언트가 지정할 수 없다.

### DEVICE-06 UMI heartbeat

- `POST /devices/{deviceId}/heartbeat` · UMI
- 요청: `firmwareVersion?: string`, `pendingUploadCount?: integer >= 0`.
- 성공 `200`: `data = { serverTime, nextHeartbeatSeconds: 15 }`.
- 서버 수신 시각으로 lastSeenAt을 갱신. 초안 기준 60초 동안 미수신하면 OFFLINE.

## 6. 시연 데이터 및 업로드 API

### EPISODE-01 시연 데이터 등록

- `POST /episodes` · UMI 또는 사용자 · `Idempotency-Key` 필수
- 요청:

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| deviceId | string | 예 | 인증 주체의 장치 |
| clientEpisodeId | string | 예 | 장치 내 유일한 캡처 ID |
| taskName | string | 예 | 1~100자 |
| recordedAt | string | 예 | RFC 3339 |
| files | object[] | 예 | VIDEO 1개, IMU 1개 필수, CALIBRATION 최대 1개 |
| files[].id | string | 예 | 요청 내 유일한 파일 ID; 이후 업로드에서 사용 |
| files[].type | enum | 예 | VIDEO / IMU / CALIBRATION |
| files[].filename | string | 예 | 표시용 이름. 저장 경로로 직접 사용하지 않음 |
| files[].contentType | string | 예 | 서버 허용 목록과 일치 |
| files[].sizeBytes | integer | 예 | 양수, 서버 크기 제한 이내 |
| files[].sha256 | string | 예 | 64자리 소문자 hex |
| metadata | EpisodeMetadata | 예 | 재생 길이·주파수 양수, 파일 참조 유효 |

- 성공 `201`: `Episode`, 최초 상태 `REGISTERED`. 동일 clientEpisodeId 재등록은 공통 멱등성 규칙 적용.
- 실패 `409 EPISODE_ALREADY_EXISTS`, `413 PAYLOAD_TOO_LARGE`, `422 VALIDATION_ERROR`.
- CALIBRATION 파일을 첨부하면 `metadata.calibrationFileId`가 해당 파일 ID를 가리켜야 한다.

### EPISODE-02 업로드 세션 발급

- `POST /episodes/{episodeId}/upload-sessions` · UMI 또는 사용자
- 요청: `fileIds: string[]` 필수, 비어 있지 않아야 함.
- 성공 `201`:

```json
{
  "data": {
    "uploadSessionId": "upl_001",
    "expiresAt": "2026-08-27T01:15:00Z",
    "files": [{
      "fileId": "video_001",
      "method": "PUT",
      "url": "https://storage.example/upload-signed-url",
      "headers": { "Content-Type": "video/mp4" }
    }]
  }
}
```

- 상태 `UPLOADING`으로 변경. URL 만료 시 미완료 파일에 대해 다시 발급 가능.
- 파일별 독립 저장 키를 사용하고, 완료 후 검증한 파일 버전을 고정하여 덮어쓰기 영향을 방지한다.
- 실패 `409 STATE_CONFLICT` (READY/VALIDATING인 데이터에 새 세션 요청).
- `INVALID` 데이터는 새 세션을 발급받아 재전송할 수 있다. 파일 기술 정보 자체를 바꾸려면 새 episode 생성.
- 초안은 단일 PUT이다. 멀티파트 재개 기능은 별도 확장 명세가 필요하다.

### EPISODE-03 업로드 완료 통보

- `POST /episodes/{episodeId}/upload-complete` · UMI 또는 사용자 · `Idempotency-Key` 필수
- 요청: `uploadedFileIds: string[]` 필수. 등록된 필수 파일 전체를 포함해야 한다.
- 성공 `202`: `data = { episodeId, status: "VALIDATING" }`.
- 서버가 실제 파일 존재·크기·해시·형식·시간 정렬을 검증한다. 클라이언트의 완료 선언만으로 READY 처리하지 않는다.
- 검증 통과 시 READY 및 `episode.ready` 이벤트 발행. 실패 시 INVALID와 validationErrors 기록.
- READY인 episode의 동일 완료 요청은 `200`: `{ episodeId, status: "READY" }`.
- 실패 `409 UPLOAD_INCOMPLETE`.

### EPISODE-04 전송 상태 조회

- `GET /episodes/{episodeId}/transfer-status` · UMI 또는 사용자
- 성공 `200`: `data = { episodeId, status, files: [{ fileId, present, verified }], validationErrors }`.
- `present`/`verified`는 서버가 관측한 값이다. 직접 업로드 중 바이트 진행률은 업로더가 측정하며 이 API가 보장하지 않는다.

### EPISODE-05 장치별 시연 목록

- `GET /devices/{deviceId}/episodes` · 사용자
- 쿼리: `status?: Episode.status`, `taskName?: string` (완전 일치), 공통 페이지네이션.
- 성공 `200`: `Episode[]` 목록.

### EPISODE-06 시연 상세

- `GET /episodes/{episodeId}` · 사용자 또는 해당 UMI
- 성공 `200`: `Episode`. 파일 저장소 자격 증명이나 영구 공개 URL은 반환하지 않는다.
- 영상 재생용 URL 발급은 현재 목록에 없는 기능이다. 웹 미리보기 요구가 확정되면 별도 추가한다.

## 7. 학습 관리 API

### TRAIN-01 학습 생성

- `POST /training-jobs` · 사용자 · `Idempotency-Key` 필수
- 요청:

```json
{
  "deviceId": "umi_001",
  "episodeIds": ["ep_001", "ep_002"],
  "baseModelVersionId": null,
  "config": { "algorithm": "configured-policy", "epochs": 100, "batchSize": 16, "seed": 42 }
}
```

- `deviceId`, `episodeIds`, `config` 필수. episodeIds는 중복 없는 비어 있지 않은 배열.
- `baseModelVersionId` 선택, 기본 null. 지정 시 접근 가능한 동일 UMI의 호환 체크포인트여야 함.
- algorithm은 서버 허용 목록 값이어야 하고 epochs/batchSize는 양수 및 알고리즘별 제한을 만족해야 함. 예시 알고리즘명은 실제 지원 여부를 뜻하지 않는다.
- 모든 episode가 해당 UMI의 READY 데이터여야 함. 입력 파일 버전·목록·설정을 생성 시점에 고정한다.
- 성공 `201`: `TrainingJob` (`QUEUED`), `Location: /api/v1/training-jobs/{jobId}`.
- 실패 `409 EPISODE_NOT_READY`, `422 MODEL_INCOMPATIBLE`.

### TRAIN-02 학습 목록

- `GET /training-jobs` · 사용자
- 쿼리: `deviceId?: string`, `status?: TrainingJob.status`, 공통 페이지네이션.
- 성공 `200`: `TrainingJob[]` 목록.

### TRAIN-03 학습 상세

- `GET /training-jobs/{jobId}` · 사용자
- 성공 `200`: `TrainingJob`. 진행률은 현재 attempt 기준이며 GPU 보고가 없으면 정체될 수 있다.

### TRAIN-04 취소 요청

- `POST /training-jobs/{jobId}/cancel` · 사용자, 본문 없음.
- QUEUED는 즉시 `200` CANCELED. PREPARING/RUNNING은 `202` CANCEL_REQUESTED.
- 응답 `TrainingJob`. 이미 CANCELED는 `200`, 이미 CANCEL_REQUESTED는 `202`.
- SUCCEEDED/FAILED는 `409 STATE_CONFLICT`. 요청 수락만으로 GPU 중단을 보장하지 않는다.

### TRAIN-05 재시도

- `POST /training-jobs/{jobId}/retry` · 사용자 · `Idempotency-Key` 필수, 본문 없음.
- FAILED/CANCELED 작업만 가능. 동일 입력 스냅샷과 설정으로 새 job을 생성하고 retryOfJobId 기록.
- 성공 `201`: 새 `TrainingJob` (`QUEUED`). 기존 작업 이력은 변경하지 않는다.
- 실패 `409 STATE_CONFLICT`, 입력 파일이 소실됐다면 `409 INPUT_UNAVAILABLE`.

### TRAIN-06 장치별 진행도

- `GET /devices/{deviceId}/progress` · 사용자
- 성공 `200`:

```json
{
  "data": {
    "deviceId": "umi_001",
    "episodeCounts": {
      "registeredTotal": 20,
      "uploaded": 18,
      "ready": 17,
      "includedInAnyJob": 15,
      "includedInSucceededJob": 12
    },
    "jobCounts": { "queued": 1, "active": 1, "succeeded": 3, "failed": 1, "canceled": 0 },
    "latestJobId": "job_006",
    "updatedAt": "2026-08-27T01:00:00Z"
  }
}
```

- episodeCounts는 episode ID 기준 DISTINCT 집계다. uploaded는 전체 파일 업로드를 서버가 한 번 이상 확인한 수이며 검증 실패도 포함할 수 있다.
- includedInAnyJob은 모든 상태의 작업에 포함된 데이터 수, includedInSucceededJob은 성공한 작업에 포함된 데이터 수다.
- GPU 수신 수는 전역 집계하지 않고 각 TrainingJob의 receivedEpisodeCount로 제공한다.
- 학습 완료 영상 수는 영상별 독립 학습 완료가 아니라 `includedInSucceededJob` 의미로 표시한다.

## 8. GPU 내부 API

내부 API의 경로도 `/api/v1` 뒤에 붙인다. 작업을 수정하는 모든 요청에는 현재 attempt와 leaseToken이 필요하다. 임대가 만료되거나 이전 attempt이면 `409 LEASE_EXPIRED`다.

### GPU-01 작업 할당

- `POST /internal/training-jobs/claim` · GPU
- 요청: `workerId: string`, `supportedAlgorithms: string[]`, `availableMemoryMiB: integer` 필수.
- workerId는 인증된 서비스에 등록된 값이어야 함. 서버는 원자적으로 작업 하나를 할당한다.
- 성공 `200`: `data = { job: TrainingJob, leaseToken, leaseExpiresAt, heartbeatIntervalSeconds: 15 }`.
- 대기 작업 없음 `204`. 할당 시 PREPARING, attempt 증가. 임대 기본 60초.
- 통신 실패 후 재호출 시 유효한 기존 할당이 있으면 동일 작업을 반환한다. 초안은 워커당 활성 작업 1개.

### GPU-02 입력 manifest

- `GET /internal/training-jobs/{jobId}/manifest` · GPU
- 헤더: `X-Lease-Token` 필수. 쿼리 `attempt: integer` 필수.
- 성공 `200`: `data = { jobId, attempt, config, episodes: [{ episodeId, metadata, files: [{ fileId, type, downloadUrl, expiresAt, sizeBytes, sha256 }] }], baseModelArtifact: null | { downloadUrl, expiresAt, sizeBytes, sha256 } }`.
- URL 만료 시 동일 API로 재발급. 버전 고정 입력을 반환한다.

### GPU-03 작업 heartbeat

- `POST /internal/training-jobs/{jobId}/heartbeat` · GPU
- 요청: `attempt: integer`, `leaseToken: string` 필수.
- 성공 `200`: `data = { leaseExpiresAt, cancelRequested: boolean, serverTime }`.
- 만료 전 임대를 갱신한다. cancelRequested=true이면 워커가 중단 후 CANCELED 이벤트를 보고한다.
- 임대가 만료된 워커는 학습 결과를 게시할 수 없다. 기본 자동 재할당은 최대 3 attempts, 초과 시 FAILED. 취소 중 임대가 만료되면 재할당하지 않고 CANCELED 처리한다.

### GPU-04 상태·진행 이벤트

- `POST /internal/training-jobs/{jobId}/events` · GPU
- 공통 필수 요청: `attempt`, `leaseToken`, `eventId: string`, `sequence: integer >= 1`, `type`, `occurredAt`, `payload`.
- sequence는 attempt 안에서 증가한다. 이전 sequence의 새 이벤트는 `409 STALE_EVENT`, 이미 처리한 동일 eventId·본문은 `200` 반환.

| type | payload | 처리 |
|---|---|---|
| `INPUT_RECEIVED` | `{ episodeId }` | manifest 내 데이터 수신·해시 검증 완료, 중복 없이 집계 |
| `STARTED` | `{}` | PREPARING → RUNNING |
| `PROGRESS` | `{ stage, percent, epoch, metrics? }` | percent 0~100, epoch 0~totalEpochs. metrics는 숫자 값 맵 |
| `SUCCEEDED` | `{ modelVersionId }` | 해당 작업의 검증 완료 CHECKPOINT가 포함된 READY 버전 확인 후 성공 |
| `FAILED` | `{ code, message }` | 실패 사유 기록 |
| `CANCELED` | `{}` | 중단 확인 |

- 성공 `200`: `data = { accepted: true, jobStatus, lastSequence }`.
- 실패 `409 STATE_CONFLICT`, `409 ARTIFACT_NOT_READY`.
- SUCCEEDED는 progress.percent를 100으로 확정한다. 학습 성공이 deployable=true를 의미하지는 않는다.
- CANCEL_REQUESTED 이후 SUCCEEDED 보고는 거부한다. 완료와 취소의 경쟁은 서버 상태 전이를 원자적으로 처리한다.

### GPU-05 산출물 업로드 세션

- `POST /internal/training-jobs/{jobId}/artifacts/upload-sessions` · GPU
- 요청: `attempt`, `leaseToken`, `artifacts: [{ clientArtifactId, kind, filename, contentType, sizeBytes, sha256 }]` 모두 필수.
- kind는 CHECKPOINT / DEPLOYMENT / METRICS.
- 성공 `201`: `data = { uploadSessionId, expiresAt, artifacts: [{ clientArtifactId, artifactId, method: "PUT", url, headers }] }`.
- 업로드 파일은 job 및 attempt에 귀속된다. PREPARING/RUNNING에서만 허용.

### GPU-06 산출물 등록

- `POST /internal/training-jobs/{jobId}/artifacts` · GPU
- 요청: `attempt`, `leaseToken`, `uploadSessionId`, `artifacts: [{ artifactId, format, compatibility }]` 필수.
- compatibility는 CHECKPOINT/METRICS에서 null 가능. DEPLOYMENT에는 ModelVersion의 compatibility 필드 전체 필수.
- 같은 attempt의 모든 최종 산출물을 한 번에 등록하며 CHECKPOINT가 최소 1개 필요하다. 중간 체크포인트는 이 초안의 범위 밖이다.
- 성공 `202`: `data = { modelId, modelVersionId, status: "PROCESSING" }`.
- 서버가 저장소 파일을 검증한 뒤 버전을 READY 또는 FAILED로 전환한다. 동일 세션·동일 본문 재요청은 같은 버전을 반환한다.
- 모델은 UMI당 기본 모델 하나를 자동 생성하고 버전을 증가시킨다. 서로 다른 모델 계열 관리는 향후 확장한다.
- GPU는 아래 버전 조회 API로 READY 확인 후 SUCCEEDED 이벤트를 보낸다. FAILED이면 FAILED 이벤트를 보낸다.
- 기존 attempt의 파일은 새 attempt 결과로 재사용하지 않는다. 사용되지 않는 파일 정리는 운영 정책으로 처리한다.

## 9. 모델 조회·배포 API

### MODEL-01 장치별 모델

- `GET /devices/{deviceId}/models` · 사용자
- 쿼리: 공통 페이지네이션. 성공 `200`: `Model[]` 목록.

### MODEL-02 버전 목록

- `GET /models/{modelId}/versions` · 사용자
- 쿼리: `status?: ModelVersion.status`, `deployable?: boolean`, 공통 페이지네이션.
- 성공 `200`: `ModelVersion[]`, `version DESC` 정렬.

### MODEL-03 버전 상세

- `GET /models/{modelId}/versions/{versionId}` · 사용자 / 할당 작업의 GPU / 해당 버전 배포를 승인받은 Jetson
- 성공 `200`: `ModelVersion`. GPU는 자신의 job 결과만 조회 가능.

### MODEL-04 최신 배포 가능 버전

- `GET /models/{modelId}/versions/latest` · 사용자
- 쿼리: `robotId?: string`. 지정 시 해당 로봇과 호환되는 버전으로 제한한다.
- 성공 `200`: READY이면서 deployable=true인 버전 중 version이 가장 큰 `ModelVersion`.
- 없음 `404 RESOURCE_NOT_FOUND`. latest는 versionId 동적 경로보다 먼저 매칭한다.
- Jetson은 latest를 임의 설치하지 않고 승인된 deployment의 고정 versionId를 사용한다.

### MODEL-05 다운로드 URL

- `POST /models/{modelId}/versions/{versionId}/download-url` · 사용자 또는 Jetson
- 요청: `artifactId: string` 필수, Jetson은 `deploymentId: string`도 필수.
- 성공 `200`: `data = { artifactId, method: "GET", url, expiresAt, sizeBytes, sha256 }`.
- 사용자에게는 자신의 READY 산출물, Jetson에게는 자신의 진행 중 배포에 지정된 DEPLOYMENT 파일만 허용.
- 실패 `409 ARTIFACT_NOT_READY`, `422 MODEL_INCOMPATIBLE`.

### DEPLOY-01 배포 생성

- `POST /robots/{robotId}/deployments` · 사용자 · `Idempotency-Key` 필수
- 요청: `modelVersionId: string`, `artifactId: string` 필수.
- 성공 `201`: `Deployment` (`PENDING`). 지정 버전·artifact를 고정한다.
- READY, DEPLOYMENT 종류, 호환성, 소유권 확인. 활성 실행 또는 다른 활성 배포가 있으면 `409 ROBOT_BUSY`.
- OFFLINE 로봇에는 PENDING으로 접수할 수 있다. 자동 만료 시간은 운영 정책으로 확정해야 한다.

### DEPLOY-02 배포 상태 보고

- `PATCH /robots/{robotId}/deployments/{deploymentId}` · Jetson
- 요청: `reportId: string`, `sequence: integer >= 1`, `status: Deployment.status`, `progressPercent: number` 필수; `error?: { code, message }`.
- 전이: PENDING → DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED. 비종료 상태 → FAILED 가능.
- 성공 `200`: `Deployment`. 실패 `409 STATE_CONFLICT`, `409 STALE_EVENT`.
- FAILED는 error 필수. SUCCEEDED는 파일 검증과 로컬 모델 로드가 성공한 후 보고하고 서버의 현재 모델을 갱신한다.
- 실패 시 기존 모델을 유지한다. 실제 롤백·원자적 교체는 Jetson 구현에서 보장해야 한다.

## 10. Scratch 작업 흐름 및 실행 API (선택)

### FLOW-01 작업 흐름 생성

- `POST /workflows` · 사용자
- 요청: `name: string`(1~100자), `schemaVersion: "1"`, `graph: WorkflowGraph` 필수.
- 성공 `201`: `Workflow`, revision=1.
- 저장 시 JSON 구조 검증. 미완성 그래프는 저장 가능하되 실행 검증을 통과해야 실행할 수 있다.

### FLOW-02 작업 흐름 조회

- `GET /workflows/{workflowId}` · 사용자
- 성공 `200`: `Workflow`.

### FLOW-03 작업 흐름 저장

- `PUT /workflows/{workflowId}` · 사용자
- 요청: `name`, `schemaVersion`, `graph`, `expectedRevision: integer` 모두 필수.
- 성공 `200`: 수정된 `Workflow`, revision 증가. 실패 `409 REVISION_CONFLICT`.
- 실행 중인 작업은 생성 당시 스냅샷을 사용하므로 수정의 영향을 받지 않는다.

### FLOW-04 실행 전 검증

- `POST /workflows/{workflowId}/validate` · 사용자
- 요청: `robotId: string`, `expectedRevision: integer` 필수.
- 성공 `200`: `data = { valid: boolean, revision, errors: [{ nodeId: string | null, code, message }], warnings: [{ nodeId: string | null, code, message }] }`.
- 문법상 유효한 요청의 그래프 검증 실패는 valid=false. 잘못된 요청은 `422`, revision 충돌은 `409`.
- MVP는 순차 비순환 그래프만 지원. 모든 노드가 entry에서 도달 가능하고, 분기·병합·루프·임의 코드 실행을 허용하지 않는다.
- RUN_POLICY: modelVersionId, timeoutMs 필수. WAIT: durationMs 필수. 시간은 양수 정수이며 서버 제한 적용.
- MVP에서는 RUN_POLICY가 로봇에 현재 적용된 동일 버전만 참조하도록 제한한다. 흐름 중 자동 모델 교체는 지원하지 않는다.

### EXEC-01 실행 요청

- `POST /robots/{robotId}/executions` · 사용자 · `Idempotency-Key` 필수
- 요청: `workflowId: string`, `workflowRevision: integer` 필수.
- 성공 `201`: `Execution` (`PENDING`). 그래프와 모델 버전을 고정한다.
- 서버가 검증을 다시 수행한다. 사용자의 사전 validate 호출만 신뢰하지 않는다.
- OFFLINE은 `409 ROBOT_OFFLINE`, 다른 활성 실행/배포는 `409 ROBOT_BUSY`, 미검증 흐름은 `422 WORKFLOW_INVALID`.
- 실행 시 Jetson이 로컬 안전 조건과 실제 적용 모델을 재검사한다.

### EXEC-02 실행 조회

- `GET /robots/{robotId}/executions/{executionId}` · 사용자 또는 해당 Jetson
- 성공 `200`: `Execution`. Jetson에는 추가로 `snapshot: { graph, workflowRevision }` 반환.

### EXEC-03 실행 중지

- `POST /robots/{robotId}/executions/{executionId}/stop` · 사용자, 본문 없음.
- 활성 실행은 `202`: `Execution` (`STOP_REQUESTED`). 이미 STOP_REQUESTED도 `202`.
- 이미 STOPPED는 `200`. SUCCEEDED/FAILED는 `409 STATE_CONFLICT`.
- 로봇의 중지 확인 후 STOPPED. HTTP 접수는 물리적인 중지 완료를 의미하지 않는다.
- STOP 명령은 START 명령보다 우선한다. 이미 발행된 START가 늦게 도착해도 Jetson은 중지된 실행을 시작하면 안 된다.

### ROBOT-01 Jetson heartbeat 및 명령 전달

- `POST /robots/{robotId}/heartbeat` · Jetson
- 요청:

```json
{
  "runtime": { "architecture": "aarch64", "name": "configured-runtime", "version": "1.0" },
  "currentModelVersionId": "ver_001",
  "acknowledgedCommandIds": ["cmd_001"],
  "executionReports": [{
    "reportId": "rep_001",
    "sequence": 1,
    "executionId": "exec_001",
    "status": "RUNNING",
    "currentNodeId": "node_001",
    "error": null
  }]
}
```

- runtime, currentModelVersionId (null 허용), acknowledgedCommandIds, executionReports 모두 필수. 배열은 비어 있어도 됨.
- 실행 보고의 status는 RUNNING / STOPPED / SUCCEEDED / FAILED. FAILED는 error 필수.
- 실행별 sequence 증가. 상태 충돌 보고는 항목별 오류로 반환하고 heartbeat 자체는 유지한다.
- 성공 `200`:

```json
{
  "data": {
    "serverTime": "2026-08-27T01:00:00Z",
    "nextHeartbeatSeconds": 5,
    "commands": [{
      "commandId": "cmd_002",
      "type": "DEPLOY_MODEL",
      "payload": {
        "deploymentId": "dep_001",
        "modelId": "model_001",
        "modelVersionId": "ver_002",
        "artifactId": "artifact_002"
      }
    }],
    "reportResults": [{ "reportId": "rep_001", "accepted": true, "error": null }]
  }
}
```

- command.type: DEPLOY_MODEL / START_EXECUTION / STOP_EXECUTION. 실행 명령 payload는 `{ executionId }`.
- ACK는 명령 수신 확인이며 완료 확인이 아니다. ACK까지 명령 재전달, 완료는 배포/실행 보고로 확인한다.
- Jetson은 명령을 로컬에 지속 저장한 후 ACK한다. 명령 복구 시에도 실제 로봇 동작을 중복 실행하지 않아야 한다.
- currentModelVersionId는 관측 정보다. 승인되지 않은 버전 보고만으로 서버의 배포 성공 상태를 갱신하지 않는다.
- 초안 OFFLINE 기준은 20초 미수신. 연결 상실은 실행 완료·중지의 증거가 아니다.
- 이 방식은 웹 수준 명령 전달용이다. 긴급 정지·모터 제어 주기는 로컬 제어 계층에서 처리해야 한다.

## 11. 실시간 이벤트 API

### EVENT-01 SSE 구독

- `GET /events` · 사용자
- 쿼리: `deviceId?: string`, `robotId?: string`. 둘 중 최대 하나만 허용. 미지정이면 자신의 전체 범위.
- 헤더: `Accept: text/event-stream`, `Authorization: Bearer ...`, 재연결 시 `Last-Event-ID` 선택.
- 성공 `200`, `Content-Type: text/event-stream`, `Cache-Control: no-cache`.
- 브라우저에서는 Authorization을 넣을 수 있는 fetch 스트림 클라이언트를 사용한다. 액세스 토큰을 URL 쿼리에 넣지 않는다.
- 토큰 만료 시 연결을 종료하고 클라이언트가 refresh 후 다시 연결한다.

```text
id: evt_001
event: training.progress
data: {"jobId":"job_001","deviceId":"umi_001","attempt":1,"sequence":8,"status":"RUNNING","percent":42,"occurredAt":"2026-08-27T01:00:00Z"}

```

| event | data |
|---|---|
| `episode.updated` | `{ episodeId, deviceId, status, occurredAt }` |
| `training.progress` | `{ jobId, deviceId, attempt, sequence, status, percent, occurredAt }` |
| `training.updated` | `{ jobId, deviceId, status, modelVersionId, occurredAt }` |
| `deployment.updated` | `{ robotId, deployment: Deployment, occurredAt }` |
| `execution.updated` | `{ robotId, execution: Execution, occurredAt }` |
| `device.connection` | `{ deviceId, connectionStatus, occurredAt }` |
| `robot.connection` | `{ robotId, connectionStatus, occurredAt }` |

- 15초마다 주석 heartbeat `: ping` 전송. 서버·프록시 스트림 버퍼링 해제 필요.
- 이벤트 재전달 가능: 이벤트 ID로 중복 제거. 기본 재생 보관 기간은 24시간.
- 재생 범위 밖 Last-Event-ID는 스트림 시작 전 `409 EVENT_CURSOR_EXPIRED`. 클라이언트는 REST 상태를 다시 조회한 뒤 재구독한다.
- 배포/로봇 상태 복구를 위해 아래 보완 조회 API를 사용한다. SSE만 상태의 유일한 근거로 삼지 않는다.

## 12. 통합에 필요한 보완 조회 API

앞서 나열한 API만으로는 새로고침 후 로봇·배포·워크플로 목록을 복구할 수 없으므로 다음 읽기 API를 추가 제안한다.

| ID | 메서드·경로 | 권한 | 요청 | 성공 data |
|---|---|---|---|---|
| QUERY-01 | `GET /robots` | 사용자 | 공통 페이지네이션 | `{ id, name, connectionStatus, lastSeenAt, currentModelVersionId }[]` |
| QUERY-02 | `GET /robots/{robotId}` | 사용자 | 없음 | `{ id, name, connectionStatus, lastSeenAt, currentModelVersionId, activeDeploymentId, activeExecutionId }` |
| QUERY-03 | `GET /robots/{robotId}/deployments/{deploymentId}` | 사용자/해당 Jetson | 없음 | `Deployment` |
| QUERY-04 | `GET /workflows` | 사용자 | 공통 페이지네이션 | `Workflow[]` |
| QUERY-05 | `GET /robots/{robotId}/executions` | 사용자 | 공통 페이지네이션, `status?` | `Execution[]` |

모두 성공 `200`, 다른 소유자 및 없는 리소스는 `404`. 목록은 공통 목록 응답 형식을 따른다. 로봇의 lastSeenAt/currentModelVersionId/active ID는 없으면 null이다.

## 13. 비동기 큐 메시지 계약

HTTP API와 별개다. 다음 예시는 파일 검증이 완료된 episode의 알림이다.

```json
{
  "eventId": "evt_001",
  "eventType": "episode.ready",
  "schemaVersion": "1",
  "occurredAt": "2026-08-27T01:00:00Z",
  "episodeId": "ep_001",
  "deviceId": "umi_001",
  "manifestRef": "manifest_001"
}
```

- 큐에는 대용량 파일과 만료되는 서명 URL을 넣지 않는다. manifestRef는 서버 내부의 고정 참조다.
- 본 초안에서는 `episode.ready`를 EC2 데이터 준비 소비자가 처리한다. GPU의 학습 할당은 claim API 한 곳에서 수행한다.
- GPU는 작업 manifest API로 파일을 받아 학습한다. 큐 메시지와 claim 양쪽에서 동일 학습을 시작하지 않는다.
- READY 저장과 이벤트 발행의 불일치를 줄이기 위해 outbox 패턴을 사용한다. 소비자는 eventId로 중복 제거한다.
- 일시 오류는 재시도, 한도 초과는 DLQ로 이동한다. 재시도 횟수·지연·DLQ 운영 정책은 구현 시 확정한다.

## 14. 상태 전이와 전체 흐름

### 상태 전이

```text
Episode: REGISTERED → UPLOADING → VALIDATING → READY
                                        └→ INVALID → UPLOADING

TrainingJob: QUEUED → PREPARING → RUNNING → SUCCEEDED
               │          │         ├→ FAILED
               │          └─────────┤
               └→ CANCELED          └→ CANCEL_REQUESTED → CANCELED

Deployment: PENDING → DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED
            각 비종료 단계 → FAILED

Execution: PENDING → RUNNING → SUCCEEDED
           각 비종료 단계 → FAILED
           PENDING/RUNNING → STOP_REQUESTED → STOPPED
```

- PREPARING에서도 실패·취소 요청 가능. 임대 만료 시 PREPARING/RUNNING → QUEUED 재할당 또는 한도 초과 FAILED.
- 실행 중 중지 요청과 완료 보고가 경쟁하면 먼저 확정된 종료 상태를 유지한다. STOP_REQUESTED 이후 실제 완료가 확인되면 SUCCEEDED/FAILED를 허용하며 중지했다고 표시하지 않는다.
- 모든 상태 전이는 원자적으로 검증한다. 종료 상태를 이전 상태로 되돌리지 않는다.

### 정상 흐름

1. 사용자가 로그인하고 등록 코드로 UMI를 자신의 계정에 연결한다.
2. UMI가 인증 후 episode를 등록한다.
3. 발급받은 URL로 영상·IMU를 업로드하고 완료를 통보한다.
4. 서버가 파일 검증 후 READY 처리 및 큐 이벤트를 발행한다.
5. 사용자가 READY episode를 선택하여 학습을 생성한다.
6. GPU가 작업을 claim하고 manifest에서 입력 파일을 받는다.
7. GPU가 heartbeat와 진행 이벤트를 전송하고 웹은 SSE로 확인한다.
8. GPU가 체크포인트 및 선택적 배포 파일을 업로드·등록한다.
9. 산출물 검증 후 GPU가 학습 성공을 보고한다.
10. 사용자가 배포 가능한 고정 버전을 지정해 Jetson 배포를 요청한다.
11. Jetson이 heartbeat로 명령을 받고 다운로드·검증·적용 상태를 보고한다.
12. 선택 기능: Scratch 흐름을 저장·검증하고 로봇 실행을 요청한다.

## 15. 구현·검증 체크리스트

- [ ] 모든 ID 경로에 사용자 소유권/장치 범위 검증.
- [ ] 사용자·UMI·GPU·Jetson 토큰을 서로 바꾸어 호출할 수 없는지 확인.
- [ ] 업로드 완료 통보만 하고 실제 파일이 없는 경우 READY 전이 차단.
- [ ] 같은 파일 URL 재사용으로 검증 후 데이터가 바뀌지 않는지 확인.
- [ ] 동일 캡처/학습/배포/실행 요청의 중복 처리 방지.
- [ ] GPU 임대 만료 및 이전 attempt의 늦은 완료 보고 차단.
- [ ] 큐 중복·재전달·DB 저장 후 발행 실패 복구.
- [ ] 학습 취소와 완료, 로봇 중지와 완료의 경쟁 조건 검증.
- [ ] 체크포인트만 있는 버전을 Jetson에 배포하지 못하도록 차단.
- [ ] Jetson 재시작 후 동일 실행 명령으로 물리 작업이 반복되지 않도록 확인.
- [ ] SSE 단절·토큰 만료·이벤트 보관 기간 초과 후 REST로 상태 복구.
- [ ] 모델 다운로드 해시·런타임 호환성 검사 및 배포 실패 시 기존 모델 유지.
- [ ] 민감한 토큰·장치 secret·서명 URL·영상 데이터의 로그 노출 방지.
- [ ] 로봇 통신 두절 시 로컬 안전 동작 및 비상 정지 별도 검증.

---

이 문서는 프론트엔드·백엔드·장치·학습 담당자 간 인터페이스 합의를 위한 초안이다. 실제 API 구현 및 통합 테스트는 아직 수행하지 않았다.
