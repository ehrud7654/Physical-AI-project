# UMI 앱 연동 인터페이스 계약

> 버전: 1.3.0
> 작성일: 2026-09-14
> 적용 범위: UMI Android 앱 ↔ EC2 백엔드 ↔ Amazon S3
> 이전 버전: v1.2.0 (2026-09-11) 을 대체한다.

## 0. v1.3 변경점 (v1.2.0 대비)

- **meta.json / summary.json**: `episode-archive-v1` 스키마를 앱이 실제로 생성하도록 확정(파일별 sha256 포함). 스펙은 v1.2.0 과 동일하되 앱 구현과 일치시켰다.
- **CSV 포맷(pose·imu·gripper)**: 이상형(정규화된 openness·qw 우선 pose·wide imu) 대신 **현재 앱이 생성하는 실제 포맷을 계약으로 명시**한다. 이상형으로의 전환은 §11 확정 대기로 미룬다.
- **업로드 세션 요청**: `metadata` + `summary` 를 필수로 싣는다(앱 구현 완료).
- **S3 PUT**: 세션 응답의 `requiredHeaders`(`x-amz-checksum-sha256`·`If-None-Match` 등)를 이름·값 그대로 싣는다(앱 구현 완료).
- **complete**: `endedAt` 를 다시 보내지 않는다(앱 구현 완료).
- **ZIP 크기 한도**: 16 MiB 는 실 RGB 에피소드에 부족 → **상향 예정(값 확정 대기, §11)**.
- **미구현(앱 호출 대기)**: status(by-session)·renew·cancel 엔드포인트 호출, 장치 enroll 흐름은 BE 확정 후 앱에 붙인다(§12).

## 1. 목적과 기준 아키텍처

UMI 앱이 수집한 시연 데이터를 학습 작업에 안전하게 등록하기 위한 외부 인터페이스를 정의한다. GPU 서버는 외부 인바운드 포트를 열 수 없으므로 UMI 앱은 GPU 서버에 직접 접속하지 않고 S3 직접 업로드 방식을 쓴다.

```text
UMI 앱
  ├─ 1. EC2에 업로드 세션 요청
  ├─ 2. EC2가 발급한 서명 URL로 S3에 ZIP 직접 업로드
  └─ 3. EC2에 업로드 완료 통지

EC2 백엔드
  ├─ UMI·TrainingJob 권한 검증
  ├─ S3 object key와 서명 URL 생성
  ├─ 업로드 완료 후 객체 크기·checksum 검증
  └─ 검증된 Episode를 학습 입력으로 등록

GPU 워커
  └─ EC2에서 작업을 outbound polling한 뒤 S3에서 Episode 다운로드
```

## 2. 역할과 책임

| 주체 | 책임 |
|---|---|
| UMI 앱 | `sessionId` 생성, 센서 데이터 수집, ZIP 생성, 크기·SHA-256 계산, S3 업로드 및 완료 통지 |
| EC2 백엔드 | 장치 인증, 작업 권한·상태 확인, S3 key 생성, 서명 URL 발급, 객체 검증, Episode 상태 관리 |
| Amazon S3 | Episode ZIP 임시 보관, 서명 URL에 의해 제한된 PUT 허용 |
| GPU 워커 | EC2로 outbound polling, 할당된 Episode 다운로드·재검증·학습 |

UMI 앱은 `ownerId`, 내부 `deviceId`, `skillId`, S3 bucket 또는 object key를 결정하거나 요청에 포함하지 않는다. 서버는 Device Access Token과 TrainingJob에서 해당 값을 결정한다.

## 3. 공통 규칙

### 3.1 기준 URL과 형식

| 항목 | 계약 |
|---|---|
| EC2 API 기준 경로 | `/api/v1` |
| 전송 보안 | HTTPS 필수 |
| JSON | UTF-8, `Content-Type: application/json` |
| 시간 | RFC 3339 UTC, 예: `2026-09-14T05:30:00.123Z` |
| JSON 필드명 | lower camel case |
| ZIP 내부 필드명 | snake_case |
| SHA-256 | 소문자 hexadecimal 64자리 |
| 성공 응답 | `{ "data": ... }` |
| 오류 응답 | `{ "error": { "code", "message", "details", "requestId", "recoveryAction" } }` |

### 3.2 인증

EC2 API 요청은 UMI 장치에 발급된 Device Access Token을 `Authorization: Bearer <token>` 로 사용한다.

- 토큰은 사용자와 등록된 UMI 장치에 귀속되며, TrainingJob에 지정된 UMI와 일치해야 한다.
- 사용자 ID·장치 ID를 요청 본문으로 받아 인증을 대신하지 않는다.
- 토큰·S3 서명 URL은 평문 로그·분석·크래시 리포트에 기록하지 않는다.
- `401 TOKEN_EXPIRED` 이면 토큰을 갱신한 뒤 재시도한다.

### 3.3 `sessionId`

한 번의 수집 세션(=하나의 Episode)을 식별한다.

| 항목 | 계약 |
|---|---|
| 생성 주체 | UMI 앱 |
| 생성 시점 | 새로운 수집을 시작할 때 |
| 형식 | RFC 4122 UUID v4, 소문자, 하이픈 포함 36자 |
| 변경 | 생성 후 변경 불가 |
| 업로드 재시도 | 동일 `sessionId` 유지 |
| 중복 식별 범위 | 인증된 `(ownerId, umiDeviceId, sessionId)` |

정규식: `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`

`sessionId` 는 수집 시작 즉시 로컬에 영속화하고, 중단·재시도에도 저장된 ID로 재개한다.

### 3.4 멱등성

상태를 생성·변경하는 EC2 요청은 `Idempotency-Key: <UUID v4>` 를 쓴다. 같은 논리 요청 재전송은 같은 키·같은 본문을 쓰고, 동일 키·다른 본문은 `409 IDEMPOTENCY_KEY_REUSED` 다. 서명 URL 만료로 세션을 재발급하는 것은 새 논리 요청이므로 새 키를 쓰되 `sessionId` 는 유지한다.

## 4. 전체 정상 흐름

```text
UMI: UUID v4 sessionId 생성 → 데이터 수집 → ZIP 생성 → sizeBytes·SHA-256 계산
UMI → EC2 : POST episode-upload-sessions (metadata·summary 포함)
EC2 → UMI : uploadSessionId, uploadUrl, requiredHeaders, expiresAt
UMI → S3  : PUT uploadUrl (requiredHeaders 그대로 + ZIP binary)
UMI → EC2 : POST {uploadSessionId}/complete (sessionId·sizeBytes·sha256)
EC2       : HeadObject·checksum 검증 → Episode 접수
```

## 5. Episode ZIP 계약

### 5.1 파일 이름과 구조

파일명은 요청 필드로 보내지 않고 `{sessionId}.zip` 으로 파생한다. 모든 entry는 `STORE`(압축률 0). 암호화·symlink·nested ZIP·절대경로·드라이브 문자·`..`·중복 entry 불가.

```text
{sessionId}.zip   ZIP method: STORE
├── meta.json
├── summary.json
├── frames.csv
├── pose.csv
├── imu.csv
├── gripper.csv
└── frames/
    ├── 000000.jpg
    └── ...
```

### 5.2 meta.json (episode-archive-v1)

계약 버전·세션·스킬·라벨·수집 시간·장치·카메라·좌표계·단위를 담는다. snake_case.

```json
{
  "schema_version": "1.0",
  "contract_version": "episode-archive-v1",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "skill_key": "pick_place",
  "source": "REAL",
  "success": null,
  "label_status": "UNLABELED",
  "started_at": "2026-09-14T05:30:00.123Z",
  "ended_at": "2026-09-14T05:30:12.842Z",
  "start_monotonic_ns": 2810045000000,
  "timestamp_timebase": "MONOTONIC_NS",
  "device": {
    "device_id": "umi-device-001",
    "manufacturer": "Samsung",
    "model": "SM-S901N",
    "app_version": "1.0",
    "os_version": "Android 16",
    "arcore_version": "1.45.0"
  },
  "camera": { "width": 1920, "height": 1080, "fx": 1452.21, "fy": 1451.87, "cx": 960.34, "cy": 539.92 },
  "coordinate_system": { "handedness": "RIGHT_HANDED", "world_frame": "ARCORE_WORLD", "pose_frame": "CAMERA", "imu_frame": "DEVICE" },
  "units": {
    "position": "METER",
    "acceleration": "METER_PER_SECOND_SQUARED",
    "angular_velocity": "RADIAN_PER_SECOND",
    "gripper": "METER",
    "acceleration_includes_gravity": true
  }
}
```

라벨 불변식: `label_status=LABELED ↔ success∈{true,false}`, `label_status=UNLABELED ↔ success=null`. 앱은 라벨 기능 도입 전까지 `UNLABELED`/`null` 로 보낸다.

실수집 데이터의 `source` 는 `REAL`, `timestamp_timebase` 는 `MONOTONIC_NS`(ARCore frame.timestamp = elapsedRealtimeNanos, monotonic)로 고정한다. 모든 CSV의 시간축은 동일 monotonic clock을 쓴다. 쿼터니언 순서는 §5.4 참조.

> **주의(§11):** `units.gripper` 는 현재 앱이 `gap_m`(미터)로 기록하므로 `METER` 다. openness(0~1) 정규화로 전환 시 `NORMALIZED_0_TO_1` 로 되돌린다.

### 5.3 summary.json

개수·시간·품질 집계 + 내부 파일 sha256(자기 자신·최종 ZIP checksum 제외).

```json
{
  "schema_version": "1.0",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "frame_count": 382,
  "pose_sample_count": 382,
  "imu_sample_count": 2541,
  "gripper_sample_count": 382,
  "first_timestamp_ns": 2810045000000,
  "last_timestamp_ns": 2822769000000,
  "duration_ns": 12724000000,
  "missing_frame_count": 0,
  "tracking_gap_count": 1,
  "tracking_lost_duration_ns": 83000000,
  "quality_status": "WARNING",
  "checksums": {
    "meta.json": "…", "frames.csv": "…", "pose.csv": "…", "imu.csv": "…", "gripper.csv": "…"
  },
  "generated_at": "2026-09-14T05:30:13.010Z"
}
```

최종 ZIP SHA-256 은 ZIP 생성 후 별도 계산해 업로드 세션 요청에 전달한다(summary 에 넣지 않음). `quality_status` 는 참고값이며 최종 판정은 서버가 한다.

### 5.4 CSV 포맷 (현재 앱 기준)

v1.3 은 앱이 실제로 생성하는 포맷을 계약으로 확정한다. 이상형(정규화 openness·qw 우선 pose·wide imu)은 §11 후속.

| 파일 | 헤더(컬럼) | 비고 |
|---|---|---|
| `frames.csv` | `frame_index,t_ns,file_name` | pose 행에서 생성 |
| `pose.csv` | `index,timestamp_ns,tracking,x,y,z,qx,qy,qz,qw,jump_mm,jump_deg,image` | **쿼터니언 xyzw(scalar-last)**, 위치 m |
| `imu.csv` | `timestamp_ns,sensor,x,y,z` | long 포맷(sensor=accel/gyro), 200Hz |
| `gripper.csv` | `frame_index,gap_m,status` | 손끝 폭 미터, status=D/X (후처리 tools/gripper_gap.py) |
| `frames/*.jpg` | — | RGB JPEG, 동일 해상도, EXIF 회전에 의존하지 않는 픽셀 방향 |

### 5.5 S3 object key 명명 규칙

서버가 생성한다. `episodes/{owner UUID 소문자}/{yyyyMMdd'T'HHmmss'Z'}/{session UUID v4 소문자}.zip`

- `owner UUID`: Device Access Token 에서 확인, UMI 요청값 미사용.
- 시간: 세션 요청의 `endedAt` 을 UTC 초 단위로 정규화.
- 최초 세션 생성 시 `endedAt`·object key 고정. 재발급해도 같은 `sessionId`·`endedAt`·`sizeBytes`·`sha256` 이면 최초 key 재사용. 다른 `endedAt`/SHA-256 은 `409 EPISODE_ID_REUSED`.

## 6. API 계약

### 6.0 장치 최초 등록과 인증  *(앱: enroll 미구현 — §12)*

1. 웹 사용자가 `POST /api/v1/devices/{deviceId}/enrollment` → `qrPayload` 발급.
2. 앱이 QR을 읽고 `POST /api/v1/device-auth/enroll`(enrollmentId·registrationToken) → Device Secret 1회 발급.
3. 앱이 Device Secret 을 Android Keystore 로 보호하고 `POST /api/v1/device-auth/token` → Device Access Token 발급.
4. Episode API 에 Device Access Token 을 Bearer 로 사용.

> 현재 앱은 3·4(토큰 발급·사용)만 구현(163). 1·2(등록·시크릿 발급)와 Keystore 보관은 미구현.

### 6.1 Episode 업로드 세션 발급  *(앱: 구현 완료)*

```http
POST /api/v1/training-jobs/{trainingJobId}/episode-upload-sessions
Authorization: Bearer <token>
Idempotency-Key: <UUID v4>
```

```json
{
  "sessionId": "…", "contentType": "application/zip",
  "sizeBytes": 10485760, "sha256": "…",
  "metadata": {
    "contractVersion": "episode-archive-v1", "schemaVersion": "1.0",
    "skillKey": "pick_place", "source": "REAL", "success": null, "labelStatus": "UNLABELED",
    "startedAt": "…", "endedAt": "…", "startMonotonicNs": 2810045000000, "timestampTimebase": "MONOTONIC_NS",
    "device": { "deviceIdentifier": "…" },
    "camera": { "width": 1920, "height": 1080, "fx": 1452.21, "fy": 1451.87, "cx": 960.34, "cy": 539.92 }
  },
  "summary": {
    "schemaVersion": "1.0",
    "firstTimestampNs": 2810045000000, "lastTimestampNs": 2822769000000,
    "frameCount": 382, "poseSampleCount": 382, "imuSampleCount": 2541, "gripperSampleCount": 382,
    "missingFrameCount": 0, "trackingGapCount": 1, "trackingLostDurationNs": 83000000
  }
}
```

`metadata` 는 `meta.json` 의, `summary` 는 `summary.json` 의 필드를 lower camel 로 변환한 선언값이다. 성공 `201` 응답:

```json
{ "data": {
  "uploadSessionId": "…", "sessionId": "…", "uploadMethod": "PUT",
  "uploadUrl": "https://…?X-Amz-Signature=…",
  "requiredHeaders": { "Content-Type": "application/zip", "x-amz-checksum-sha256": "base64", "If-None-Match": "*" },
  "expiresAt": "…", "maxUploadBytes": 16777216
} }
```

### 6.2 S3 ZIP 업로드  *(앱: 구현 완료)*

```http
PUT <uploadUrl>
<requiredHeaders 를 이름·값 그대로>
<ZIP binary>
```

- 성공 기준 S3 `200 OK`. 앱은 파일 스트림으로 전송(메모리 미적재), `Content-Length` = 선언 `sizeBytes`.
- 응답 `ETag` 는 SHA-256 으로 신뢰하지 않음. redirect 시 Authorization·서명 URL 을 다른 호스트로 전달하지 않음.

### 6.3 Episode 업로드 완료 통지  *(앱: 구현 완료)*

```http
POST /api/v1/training-jobs/{trainingJobId}/episode-upload-sessions/{uploadSessionId}/complete
```

```json
{ "sessionId": "…", "sizeBytes": 10485760, "sha256": "…" }
```

`endedAt` 은 다시 보내지 않는다(세션의 `metadata.endedAt` 과 `meta.json.ended_at` 을 서버가 비교). 성공 `202`(status=VALIDATING). 이미 완료면 `200`(duplicate=true).

### 6.4 Episode 상태 조회  *(앱: 미구현 — §12)*

`GET /api/v1/training-jobs/{trainingJobId}/episodes/by-session/{sessionId}` → `status ∈ {UPLOAD_PENDING, UPLOADED, VALIDATING, READY, INVALID, FAILED}`. 앱은 `READY` 확인 후에만 로컬 원본을 정리한다.

### 6.5 업로드 URL 재발급  *(앱: 미구현 — §12)*

`POST …/episode-upload-sessions/{uploadSessionId}/renew` — `UPLOAD_PENDING` 세션의 bucket·key·크기·SHA-256 유지하며 새 `uploadUrl`·`requiredHeaders`·`expiresAt` 반환.

### 6.6 업로드 세션 취소  *(앱: 미구현 — §12)*

`DELETE …/episode-upload-sessions/{uploadSessionId}` — `UPLOAD_PENDING` 세션의 S3 객체 삭제·예약 취소. `204`, 멱등.

## 7. 서버 검증 규칙 (요지)

요청·`meta.json`·`summary.json` 의 session ID 일치, UUID v4 형식, `metadata.endedAt == meta.json.ended_at`, S3 object key 규칙, 크기·checksum 일치, 모든 entry `STORE`·경로 안전, 필수 entry·필드 존재, `frame_index` 연속·JPEG 존재, `t_ns` 단조 증가, `NaN`/`Infinity` 없음, 내부 checksum·최종 ZIP SHA-256 일치, `skill_key`·`source`·label 조합이 TrainingJob 계약과 일치.

## 8. 오류 계약

| HTTP | 코드 | UMI 앱 처리 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 요청 수정, 자동 반복 금지 |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | 인증 수행 / 토큰 갱신 후 재시도 |
| 403 | `FORBIDDEN` | 업로드 중단 |
| 404 | `RESOURCE_NOT_FOUND` | 작업 정보 재동기화 |
| 409 | `STATE_CONFLICT` / `INPUT_CLOSED` | 상태 재조회 / 업로드 중단 |
| 409 | `EPISODE_ID_REUSED` | 자동 재시도 금지, 새 촬영 시 새 ID |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 요청 로직 점검 |
| 410 | `UPLOAD_SESSION_EXPIRED` | 같은 session ID 로 세션 재발급 |
| 413 | `PAYLOAD_TOO_LARGE` | 크기 축소 또는 재수집 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | archive 생성 로직 수정 |
| 422 | `CHECKSUM_MISMATCH` / `ARCHIVE_VALIDATION_FAILED` | 재확인 후 재업로드 / 상세 따라 수정 |
| 429 | `RATE_LIMITED` | `Retry-After` 후 재시도 |
| 503 | `STORAGE_UNAVAILABLE` | 지수 백오프 재시도 |

## 9. 재시도와 로컬 상태 관리

로컬 저장: `trainingJobId, sessionId, endedAt, localArchivePath, sizeBytes, sha256, uploadSessionId, uploadExpiresAt, uploadState, uploadIdempotencyKey, completeIdempotencyKey, lastErrorCode, retryCount`.

상태 전이: `RECORDING → PACKAGING → READY_TO_UPLOAD → SESSION_ISSUED → UPLOADING → UPLOADED → COMPLETE_REQUESTED → VALIDATING → READY` (실패 시 `RETRY_WAIT`/`INVALID`).

- timeout·429·5xx 는 지수 백오프+지터. 4xx 는 오류 코드가 재시도를 명시할 때만.
- S3 결과 불명확 시 상태 조회/완료 API 먼저(새 sessionId 생성 금지).
- URL 만료 시 같은 `sessionId`·`endedAt`·`sizeBytes`·`sha256` 으로 세션 재발급.
- 서버 `READY` 확인 전 로컬 ZIP 삭제 금지.

## 10. S3 및 보안 요구사항

- bucket public access 전면 차단, 앱에 AWS 키 미포함.
- 서명 URL 은 한 key·PUT 에만 짧은 만료로 권한 부여.
- object key 는 §5.5 형식으로 서버 생성(클라이언트 파일명·경로 미사용).
- `ETag` 를 SHA-256 으로 신뢰하지 않음. checksum 검증 시 서버가 hex→Base64 변환해 `x-amz-checksum-sha256` 서명·`requiredHeaders` 반환.
- SSE-S3/SSE-KMS 사용 시 해당 헤더를 `requiredHeaders` 에 포함.

## 11. 구현 완료 전 확정 사항

- [ ] 운영/스테이징 API base URL
- [x] QR 장치 등록·Device Secret 1회 발급·Access Token 발급 API (BE)
- [x] Presigned PUT TTL 15분, Access Token TTL 서버 설정
- [ ] **Episode ZIP 최대 크기 — 16 MiB 는 1080p 에피소드에 부족. 상향 값 확정 필요.**
- [x] MVP 단일 PUT, Multipart 제외
- [ ] `meta.json`/`summary.json` JSON Schema·CSV nullable 규칙
- [ ] **CSV 계약화 후속**: gripper `openness(0~1)`, pose `qw 우선 + tracking_state`, imu `wide(t_ns,ax..gz)` 로 전환할지 여부
- [ ] 좌표축·quaternion 정규화 오차·tracking state 허용값
- [ ] `VALID`/`WARNING`/`INVALID` 품질 임계값
- [ ] S3 원본 보관 기간·정리 정책

## 12. UMI 앱 구현 현황

**구현 완료**
- [x] 수집 시작 시 UUID v4 `sessionId` 1회 생성·영속
- [x] `{sessionId}.zip`(STORE) + meta.json/summary.json(episode-archive-v1, 파일별 sha256)
- [x] ZIP byte 크기·전체 SHA-256 계산(스트리밍)
- [x] 세션 요청에 `metadata`+`summary` 포함
- [x] S3 PUT 에 `requiredHeaders` 이름·값 그대로 적용
- [x] complete 에 `endedAt` 미포함, `endedAt` 은 `meta.json.ended_at` 로 일관
- [x] 같은 논리 요청 재시도 시 동일 Idempotency-Key
- [x] 서버 `READY` 전 로컬 원본 미삭제, 토큰·서명 URL 미로깅

**미구현 (BE 확정 후 붙임)**
- [ ] status(by-session) 조회 → `READY` 확인 후 로컬 정리
- [ ] renew(URL 재발급) 호출
- [ ] cancel(세션 취소) 호출
- [ ] 장치 enroll 흐름(QR 등록→Device Secret) + Keystore 보관
- [ ] CSV 계약화(§11) — 확정 시 pose/imu/gripper 컬럼·단위 전환
