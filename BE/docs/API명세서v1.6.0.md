# UMI 로봇팔 스킬 학습·배포 플랫폼 API 명세서

> Episode ZIP·고정 TrainingProfile·학습 큐·게이트·Jetson 계약을 정의하며, EC2 Spring Boot와 GPU Spring Boot 및 FastAPI 사이의 역할과 실행 계약을 포함한다.

- 문서 버전: 1.6.0
- 작성일: 2026-09-15
- 기준 경로: `/api/v1`; 내부 GPU 경로는 `/internal`
- 범위: 웹 클라이언트, EC2 백엔드, UMI, GPU 워커, S3, Jetson 간 인터페이스
- ERD 기준: [DB 스키마 1.5.0](./DB스키마v1.5.0.md) 및 Flyway V1~V28
- 주요 리소스: `TrainingProfile`, `EpisodeArchive`, `SkillVersion`, `RobotSkillDeployment`, `RobotExecution`
- 이 문서는 제안 명세다. 실제 GPU 주소, 로컬 보관 기간과 용량 정책, S3 버킷, 토큰 만료 시간, CKPT 내부 포맷은 구현 전에 확정해야 한다.

## 1.6.0 변경 요약

| 구분 | 1.5.0 → 1.6.0 |
|---|---|
| 병렬 학습 | 등록 GPU Worker의 `gpuIndex=0~29`를 사용해 최대 30개 학습을 병렬 할당한다. |
| 할당 게이트 | Worker·StorageNode 관계, 노드 상태·heartbeat, 가용 저장 공간·GPU 메모리, 지원 알고리즘, GPU 슬롯 점유를 Claim에서 검사한다. |
| 원자성 | 후보 작업은 PostgreSQL `FOR UPDATE SKIP LOCKED`로 점유하고 `(assigned_worker_id,gpu_index)` 활성 부분 UNIQUE 인덱스로 슬롯 중복을 차단한다. |
| Claim 응답 | 후속 요청에 사용할 현재 `attempt`를 응답에 추가한다. |
| 노드 API | GPU-06 StorageNode 등록과 GPU-07 heartbeat를 구현 계약으로 확정한다. |
| Lease 복구 | 만료된 할당은 주기적으로 회수하여 attempt 증가 후 재대기하거나 한도 초과 시 실패 처리한다. |
| Redis | 학습 lease는 Redis에 보조 캐시하되 PostgreSQL을 최종 판정 기준으로 유지한다. |

## 1.5.0 변경 요약

| 구분 | 1.4.0 → 1.5.0 |
|---|---|
| 수집 시작 조건 | Episode 수집을 TrainingJob 생성 여부와 분리한다. UMI는 학습 요청 전에도 등록된 동작 데이터를 업로드할 수 있다. |
| 업로드 API | 세션 발급 URL을 `POST /episode/data-upload-request`로 변경하고 URL에서 `trainingJobId`를 제거한다. |
| 동작 검증 | `metadata.skillKey`로 시스템 공통 스킬 또는 인증 사용자 소유 커스텀 스킬을 조회한다. 조회되지 않으면 업로드를 거부한다. |
| 후속 API | 완료·취소·URL 재발급·처리 상태 조회를 `/episode/data-upload-request/**` 아래로 통일한다. |
| DB 연결 | 신규 `episode_upload_sessions.training_job_id`는 `NULL`이며 기존 행 호환을 위한 레거시 참조로만 유지한다. |
| 학습 연결 | ZIP 검증 성공 시 Episode와 EpisodeArchive만 확정한다. 사용자가 학습을 요청할 때 선택된 Episode를 `training_job_episodes`에 snapshot한다. |
## 1.4.0 변경 요약

| 구분 | 1.3.0 → 1.4.0 |
|---|---|
| 장치 등록 API | QR 등록 시작 `POST /devices/{deviceId}/enrollment`, 완료 `POST /device-auth/enroll` 추가 |
| Episode API | 처리 상태 조회, 업로드 취소, Presigned PUT URL 재발급 추가 |
| 요청 계약 | 업로드 세션 요청의 `metadata`, `summary` 구조를 구현 기준으로 확정 |
| 완료 계약 | 완료 요청에서 `endedAt`을 제거하고 최초 `metadata.endedAt`을 기준으로 검증 |
| 상태 계약 | `UPLOAD_PENDING`, `UPLOADED`, `VALIDATING`, `READY`, `INVALID`, `FAILED`로 통일 |
| 상태 응답 | `qualityStatus` 및 `{code,message,recoveryAction}` 오류 객체 추가 |
| ZIP 검증 | 커밋 후 비동기로 ZIP 안전성·필수 entry·JSON·CSV·내부 checksum·집계를 검사 |
| DB 확정 | 검증 성공 시 Episode, EpisodeArchive, TrainingJobEpisode를 하나의 트랜잭션으로 생성 |

## 1.3.0 변경 요약

UMI가 GPU ingest 서버로 ZIP을 직접 전송하던 계약을 폐기하고, EC2에서 단일 PUT Presigned URL을 발급받아 S3 임시 Episode 영역으로 직접 업로드하는 흐름을 추가한다. 이 절의 Episode 업로드 계약은 [인터페이스 계약 1.1.0](./인터페이스계약v1.1.0.md)을 기준으로 한다.

| 구분 | 1.2.0 → 1.3.0 |
|---|---|
| 추가 API | `EPISODE-UPLOAD-01` 업로드 세션 발급, `EPISODE-UPLOAD-02` 업로드 완료 통지 |
| 전송 경로 | UMI → GPU ingest → GPU 로컬 저장소에서 UMI → S3 Presigned PUT으로 변경 |
| 메타데이터 | 업로드 요청에 ZIP 전체 크기·SHA-256과 `meta.json`·`summary.json`의 API용 필드를 함께 전달 |
| 권위 기준 | owner·UMI는 Device Access Token, Skill은 URL의 TrainingJob에서 EC2가 확정하고 Android 값은 일치 검증에만 사용 |
| 저장 경로 | `episodes/{owner UUID}/{endedAt UTC}/{session UUID}.zip`; bucket과 object key는 EC2만 결정 |
| 완료 처리 | EC2가 S3 객체 존재·크기·checksum을 확인한 후 Episode를 `VALIDATING`으로 접수하고 archive 내용 검증은 비동기 수행 |
| 업로드 방식 | 예상 파일 크기 10 MiB 수준의 MVP는 단일 PUT만 사용하며 Multipart API는 포함하지 않음 |
| 유지 | 조회·학습·CKPT·배포 계약과 VLM 제외 결정은 1.2.0을 유지 |

## 1.2.0 변경 요약

DB 스키마 1.2.0에서 VLM 도메인(`skill_destinations`, `vlm_model_versions`, `vlm_policies`, `vlm_inference_results`, `skills.needs_vlm`, 배포 테이블의 `vlm_model_version_id`)이 제외됨에 따라 API에서 VLM 계약을 제거한다. VLM 모델·정책·목적지 계약이 확정되면 별도 문서와 버전으로 다시 도입한다.

| 구분 | 1.1.0 → 1.2.0 |
|---|---|
| 삭제 API | `SKILL-08` 스킬별 VLM 목적지 목록, `VLM-01`~`VLM-04`(12장 전체) |
| 삭제 모델 | `SkillDestination`, `VlmModelVersion`, `VlmPolicy`, `VlmInferenceResult` |
| 삭제 필드 | `Skill.needsVlm`, `DeploymentItem.vlmModelVersionId`, `RobotSkillDeployment.vlmModelVersionId`, `SKILL-01` 쿼리 `needsVlm`, `DEPLOY-01` 요청 `vlmModelVersions`, `JETSON-01` manifest `models[].vlmModelVersion` |
| 삭제 오류 코드 | `409 VLM_MODEL_REQUIRED`, `422 INVALID_DESTINATION`, `422 VLM_OUTPUT_INVALID` |
| 삭제 SSE 이벤트 | `vlm.inference-completed` |
| 변경 | `DEPLOY-01`은 TaskVersion만 받고 `READY`·`gate.passed=true`·RobotProfile 호환성만 검증한다. `SKILL-03`·스킬 카탈로그의 호출 주체와 `skillKey` 계약 설명에서 VLM을 제외했다. 18장 ERD 호환성 표·다이어그램·UNIQUE 목록, 19.4 모델 로딩 시나리오, 20장 체크리스트, 21장 확정 사항에서 VLM 항목을 정리했다. |
| 유지 | 그 외 모든 API·모델·상태 전이는 1.1.0과 동일하다. 장 번호는 참조 안정성을 위해 유지하며 12장은 "제외" 표시로 남긴다. |

## 1.1.0 변경 요약

- Episode에 학습 가능 여부·차단 사유·복구 행동을 추가하고, 만료형 미리보기 세션 API를 정의했다.
- TrainingJob의 수집 진행 집계를 상세 조회와 SSE에서 동일한 구조로 제공한다.
- 고정 TrainingProfile은 유지하고, FE 안내용 스킬별 학습 데이터 요구사항 조회만 추가했다.
- TaskVersion의 로봇 준비 상태 응답을 구체화하고 RobotExecution 생성·조회·취소·Jetson 보고 계약을 추가했다.
- 목록 응답의 전체 건수와 RobotExecution SSE를 추가해 새로고침·재연결 후 상태를 복구할 수 있게 했다.
- 이 버전은 별도 CaptureSession, 사용자 학습 프리셋, 튜토리얼 API를 추가하지 않는다. Episode는 학습 요청 전에 독립적으로 수집하고 TrainingJob 생성 시 선택한다.

## 1. 설계 가정과 범위

| 항목 | 결정 |
|---|---|
| 학습 원본 | 영상 프레임·Pose·IMU·Gripper 데이터를 무압축 ZIP 하나로 묶어 S3 임시 Episode 영역에 제한 기간 보관한다. EC2와 DB에는 Episode binary를 저장하지 않는다. |
| Episode | UMI 시연 한 번과 `<session_id>.zip` 하나를 Episode 한 건으로 식별하고 보관·검증·삭제 상태를 추적한다. |
| 학습 관제 | DB에는 Episode 품질 집계와 EpisodeArchive의 크기, SHA-256, 논리 storage key, StorageNode, 보관 기한 및 학습 사용 이력을 저장한다. ZIP 내부 행과 프레임은 DB에 저장하지 않는다. |
| 학습 설정 | 사용자는 하이퍼파라미터를 입력하지 않는다. 백엔드가 현재 ACTIVE TrainingProfile을 선택하고 전체 설정 snapshot과 SHA-256을 TrainingJob에 고정한다. |
| 데이터 재사용 | `READY`이며 보관 기한이 남은 Episode를 새 TrainingJob 입력으로 재사용할 수 있다. 학습 입력 목록은 생성 후 불변으로 고정한다. |
| Episode 위치 | EC2가 생성한 S3 object key를 DB에 기록하고 GPU worker에는 제한 시간 Presigned GET을 제공한다. |
| 원본 삭제 | 보관 기한이 지났고 활성 TrainingJob이 참조하지 않을 때만 삭제한다. 파일 삭제 후에도 Episode와 학습 사용 이력은 DB에 tombstone으로 유지한다. |
| 모델 파일 | 학습 결과인 CKPT만 S3에 영구 저장한다. |
| CKPT 메타데이터 | DB에는 S3 bucket/key, 크기, SHA-256, 모델·입출력 계약, 호환 로봇 프로파일을 저장한다. |
| 스킬 | 시스템 공통 스킬과 사용자 소유 커스텀 스킬을 함께 관리하며 FE·BE·UMI·AI가 동일한 `skillKey`를 사용한다. |
| 스킬 버전 | 한 번의 학습으로 생성한 불변 CKPT 한 개다. 기존 버전과 S3 객체를 덮어쓰지 않는다. |
| 배포 게이트 | SkillVersion은 CKPT 검증 후 READY가 될 수 있지만, rollout 게이트를 통과한 버전만 DeploymentItem으로 생성할 수 있다. |
| Task | 여러 스킬을 순서대로 조합한 논리적 로봇 작업이다. 학습 데이터의 episode와 구분한다. |
| TaskVersion | 실행 순서와 정확한 `skillVersionId`를 고정한 불변 버전이다. |
| 배포 | 사용자가 Robot에 TaskVersion 적용을 요청하고, Jetson이 모든 CKPT를 검증·로드한 뒤 성공을 보고한다. |
| 다운로드 | EC2는 DB의 S3 경로를 그대로 노출하지 않고, 권한 확인 후 제한 시간 다운로드 URL을 발급한다. |
| 인증 | 사용자, UMI, GPU, Jetson을 별도 주체로 인증한다. 핸들 번호는 식별자이지 인증 수단이 아니다. |
| 상태 기준 | 학습 완료, CKPT 검증 완료, Jetson 적용 완료를 서로 다른 상태로 관리한다. |
| 평가 | 별도 EvaluationRun/Trial 도메인은 두지 않고 SkillVersion에 배포 게이트 집계 결과만 저장한다. 사용자가 요청한 실제 로봇 작업의 상태와 결과는 RobotExecution으로 추적한다. |
| 재학습 | 보관 중인 READY Episode로 새 TrainingJob을 만들 수 있다. 기존 TrainingJob 자체를 되돌리거나 덮어쓰지는 않는다. |

### 1.1 주요 흐름

```text
학습
사용자 → EC2 TrainingJob 생성
      → EC2가 현재 ACTIVE TrainingProfile을 작업에 불변 고정
      → UMI가 등록된 동작의 데이터를 EC2에서 Presigned PUT을 발급받아 S3에 Episode ZIP으로 선행 업로드
      → EC2가 객체를 확인하고 Episode와 EpisodeArchive를 접수한 뒤 비동기 검증
      → 기존 데이터 사용이면 READY Episode 목록을 TrainingJob에 고정
      → GPU 학습 및 EC2에 진행 보고
      → GPU가 CKPT를 S3에 업로드
      → EC2가 CKPT 검증 후 SkillVersion READY
      → 시뮬레이션·검증 워커가 rollout 게이트 결과 보고
      → 게이트 통과 SkillVersion만 배포 후보가 됨
      → 보관 기한 만료 및 활성 학습 없음 확인
      → Lifecycle worker가 로컬 Episode ZIP 삭제 결과 보고

모델 로딩
사용자 → Robot에 TaskVersion 배포 요청
      → EC2가 CKPT 게이트와 RobotProfile 호환성 검증
      → EC2가 Deployment와 DeploymentItem 생성
Jetson → 자신의 pending Deployment manifest 요청
       → S3 CKPT 다운로드
       → 크기·SHA-256·입출력 계약 검증
       → 전체 TaskVersion 원자적 적용
       → EC2에 배포 결과 보고
       → EC2가 로봇별 활성 RobotSkillDeployment 갱신
```

### 1.2 범위 밖

- 브라우저에서 Episode ZIP 또는 내부 원본 다운로드. 사용자 확인용 만료형 미리보기는 별도 preview session으로만 제공한다.
- 로컬 보관 기한을 넘는 Episode ZIP 장기 보관과 백업 보장
- DatasetVersion 및 데이터 라벨링·가공 파이프라인
- 서로 다른 StorageNode에 저장된 Episode의 자동 복제·전송
- CKPT 내부 가중치 분석 UI
- 복잡한 조건 분기·반복을 포함한 범용 workflow 엔진
- 원격 웹 요청만으로 수행되는 실시간 모터 제어
- EvaluationRun/EvaluationTrial 상세 분석과 trial별 원본 로그 관리
- 사용자별·작업별 하이퍼파라미터 선택 및 수정 UI

## 2. 공통 규칙

### 2.0 서비스 경계

| 서비스 | 제공·호출 계약 |
|---|---|
| EC2 Spring Boot | 기존 /api/v1 제공, 대기 큐·lease·최종 업무 상태·게이트 관리 |
| GPU Spring Boot | GPU-01~07·PURGE-01~03 호출, S3 Episode 다운로드, FastAPI 제어 및 CKPT S3 업로드 |
| FastAPI + 별도 학습 프로세스 | GPU 내부 실행·조회·취소·임대 API 제공 (22절), 직접 EC2 claim·DB 접근 없음 |
| 시뮬레이션·검증 worker | GATE-01 직접 호출, EC2가 결과를 검증해 gatePassed·배포 자격 관리 |

이 문서에서 기존 'GPU' 호출 주체는 GPU Spring Boot를 의미한다. GATE-01의 평가 보고 주체는 시뮬레이션·검증 worker다. 웹 API는 기존 경로를 유지한다.


### 2.1 호출 주체와 인증

| 표기 | 호출 주체 | 인증 방식 | 허용 범위 |
|---|---|---|---|
| 공개 | 인증 전 사용자·장치 | 없음 또는 사전 발급 secret | 로그인·장치 토큰 발급·UMI 스킬 목록 조회 |
| 사용자 | 웹 클라이언트 | `Authorization: Bearer <userAccessToken>` | 자신 소유 리소스 |
| UMI | 데이터 수집 장치 | `Authorization: Bearer <deviceAccessToken>` | 자신에게 허용된 학습 입력 전송과 Episode 등록 |
| GPU | 학습 워커 | 별도 Service Token을 사용하지 않음 | `workerId`, 현재 `attempt`, `leaseToken`으로 할당 작업 검증 |
| Jetson | 로봇 실행 장치 | `Authorization: Bearer <deviceAccessToken>` | 자신과 연결된 Robot의 배포 |
| 내부 | EC2 내부 작업 | 서비스 인증 + 네트워크 제한 | S3 검증·상태 전이 |

- 다른 사용자 리소스는 존재 여부가 드러나지 않도록 `404 RESOURCE_NOT_FOUND`로 응답한다.
- UMI와 Jetson 토큰은 `deviceType`과 `deviceId` 범위를 포함한다.
- GPU → EC2 내부 API에는 `Authorization` 헤더와 Spring Security authority를 요구하지 않는다. EC2는 관련 경로에 GPU Service Token 인증 설정을 추가하지 않는다.
- Claim 이후의 변경 요청은 서버가 발급한 현재 `attempt`와 `leaseToken` 및 할당된 `workerId`를 대조한다. 전송 구간은 HTTPS와 인프라 네트워크 접근 제어를 적용한다.
- 핸들 번호를 안다는 이유만으로 데이터 업로드 또는 CKPT 다운로드를 허용하지 않는다.

### 2.2 요청·응답 형식

- JSON 요청은 `Content-Type: application/json`을 사용한다.
- GPU Episode archive 전송은 `multipart/form-data`를 사용한다.
- 시간은 RFC 3339 UTC 문자열을 사용한다.
- ID는 서버가 발급한 불투명 문자열이다.
- 성공 응답은 `{ "data": ... }` 형식이다.
- 목록 응답은 다음 형식이다.

```json
{
  "data": [],
  "page": {
    "nextCursor": null,
    "hasNext": false,
    "totalCount": 0
  }
}
```

- 목록 공통 쿼리: `cursor?: string`, `limit?: integer`이며 기본 20, 범위 1~100이다.
- `totalCount`는 현재 필터 조건에 해당하는 전체 건수이며 현재 페이지의 배열 길이로 대체하지 않는다.
- 기본 정렬은 `createdAt DESC, id DESC`다.
- 모든 EC2 응답에 `X-Request-Id`를 반환한다.
- `204` 응답에는 본문이 없다.
- 서명 URL과 토큰은 로그에서 마스킹한다.

### 2.3 멱등성 및 동시성

- 다음 요청은 `Idempotency-Key`가 필수다.
  - TrainingJob 생성
  - Episode 수동 삭제 요청
  - 입력 완료 요청
  - CKPT 업로드 세션 생성 및 완료
  - TaskVersion 생성
  - Deployment 생성
  - RobotExecution 생성
- 동일 키·동일 본문은 최초 응답을 재사용한다.
- 동일 키·다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`다.
- 학습 이벤트는 `(trainingJobId, attempt, eventId)`로 중복 제거한다.
- Episode 업로드는 `(ownerId, umiDeviceId, sessionId)`로 중복 제거한다.
- 로컬 삭제 작업은 Episode당 활성 `DataPurgeJob` 하나만 허용한다.
- GPU 및 Jetson 상태 보고는 `reportId`와 `sequence`로 중복·역순을 차단한다.
- 스킬 버전 번호는 EC2가 트랜잭션 안에서 할당한다. GPU가 직접 버전을 정하지 않는다.
- SkillVersion의 CKPT·계약 메타데이터, `TaskVersion`, S3 CKPT 객체는 생성 후 변경하지 않는다. SkillVersion 상태와 게이트 필드는 정의된 전이에 따라서만 갱신한다.
- Deployment 생성 시 `TaskVersion`의 모든 `skillVersionId`를 `DeploymentItem`으로 고정한다.
- `latest`는 목록 편의를 위한 조회일 뿐 Jetson 배포 입력으로 사용하지 않는다.
- Existing Episode로 TrainingJob을 생성하는 트랜잭션과 DataPurgeJob 생성 트랜잭션은 대상 Episode 행을 잠가 선택·삭제 경합을 차단한다.
- TrainingJob에 고정된 Episode 목록과 `inputSnapshotSha256`은 입력 확정 후 변경하지 않는다.
- TrainingJob에 고정된 TrainingProfile key·version·설정 snapshot·SHA-256은 생성 후 변경하지 않는다. 동일 Idempotency-Key 재요청도 최초 프로필을 유지한다.
- Retention 연장과 삭제 시작이 경합하면 이미 `DELETING`으로 전환된 삭제가 우선하며 연장 요청은 `409 STATE_CONFLICT`다.

### 2.4 공통 오류

```json
{
  "error": {
    "code": "SKILL_VERSION_NOT_READY",
    "message": "사용할 수 없는 스킬 버전이 포함되어 있습니다.",
    "details": [
      {
        "field": "steps[2].skillVersionId",
        "reason": "skill_ver_33 is VALIDATING"
      }
    ],
    "requestId": "req_001",
    "recoveryAction": "WAIT"
  }
}
```

- `recoveryAction`은 사용자가 취할 수 있는 행동이 명확한 경우에만 반환하는 선택 필드다.
- 허용 값은 `WAIT`, `RETRY_UPLOAD`, `RECORD_AGAIN`, `SET_LABEL`, `EXTEND_RETENTION`, `CREATE_DEPLOYMENT`, `RETRY`, `CONTACT_SUPPORT`, `NONE`이다.

| HTTP | 대표 오류 코드 | 의미 |
|---|---|---|
| 400 | `INVALID_REQUEST`, `FIELD_NOT_ALLOWED` | JSON·multipart·쿼리 형식 오류 또는 서버 관리 필드 입력 |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS` | 인증 실패 |
| 403 | `FORBIDDEN` | 해당 주체에게 허용되지 않은 작업 |
| 404 | `RESOURCE_NOT_FOUND` | 리소스 없음 또는 소유 범위 밖 |
| 409 | `STATE_CONFLICT`, `ROBOT_BUSY`, `LEASE_EXPIRED`, `SKILL_VERSION_NOT_READY`, `SKILL_VERSION_GATE_FAILED`, `EPISODE_IN_USE`, `EPISODE_NOT_AVAILABLE` | 상태·동시성 충돌 |
| 413 | `PAYLOAD_TOO_LARGE` | GPU ingest 정책상 크기 초과 |
| 422 | `VALIDATION_ERROR`, `CHECKPOINT_INVALID`, `MODEL_INCOMPATIBLE`, `EPISODE_INVALID`, `INVALID_SKILL_KEY`, `UNSUPPORTED_TRAINING_PROFILE`, `TRAINING_CONFIG_MISMATCH` | 요청·파일·설정·호환성 검증 실패 |
| 429 | `RATE_LIMITED` | 요청 제한. `Retry-After` 반환 |
| 500 | `INTERNAL_ERROR` | 예기치 않은 오류 |
| 503 | `GPU_UNAVAILABLE`, `STORAGE_UNAVAILABLE`, `STORAGE_CAPACITY_EXCEEDED`, `TRAINING_PROFILE_UNAVAILABLE` | GPU, 로컬 저장소, S3 또는 활성 학습 프로필 사용 불가 |

## 3. 공통 응답 모델

```typescript
type User = {
  id: string;
  email: string;
  name: string;
  createdAt: string;
};

type Device = {
  id: string;
  ownerId: string;
  deviceType: 'UMI_CAMERA' | 'JETSON';
  handleNumber: string;
  name: string;
  location: string | null;
  connectionStatus: 'ONLINE' | 'OFFLINE';
  lastSeenAt: string | null;
  createdAt: string;
};

type StorageNode = {
  id: string;
  workerId: string;
  name: string;
  storageType: 'LOCAL';
  status: 'ONLINE' | 'OFFLINE' | 'DRAINING';
  capacityBytes: number;
  availableBytes: number;
  lastSeenAt: string | null;
};

type EpisodeArchive = {
  id: string;
  contentType: 'application/zip';
  archiveFormatVersion: string;
  compressionMethod: 'STORE';
  sizeBytes: number;
  uncompressedSizeBytes: number;
  entryCount: number;
  sha256: string;
  status: 'UPLOADING' | 'AVAILABLE' | 'DELETING' | 'DELETED' | 'DELETE_FAILED';
  storedAt: string | null;
  validatedAt: string | null;
  deletedAt: string | null;
};

type Episode = {
  id: string;
  ownerId: string;
  umiDeviceId: string;
  skillId: string;
  skillKey: SkillKey;
  sessionId: string;
  source: 'SIM' | 'REAL';
  success: boolean | null;
  labelStatus: 'UNLABELED' | 'LABELED';
  labelSource: 'UMI' | 'USER' | 'SERVER' | null;
  labeledAt: string | null;
  contractVersion: string;
  metaSchemaVersion: string;
  summarySchemaVersion: string;
  status:
    | 'UPLOADING'
    | 'VALIDATING'
    | 'READY'
    | 'INVALID'
    | 'DELETING'
    | 'DELETED'
    | 'DELETE_FAILED';
  startedAt: string;
  endedAt: string;
  startMonotonicNs: number;
  timestampTimebase: 'MONOTONIC_NS';
  firstTimestampNs: number;
  lastTimestampNs: number;
  durationNs: number;
  frameCount: number;
  poseSampleCount: number;
  imuSampleCount: number;
  gripperSampleCount: number;
  missingFrameCount: number;
  trackingGapCount: number;
  trackingLostDurationNs: number;
  qualityStatus: 'PENDING' | 'VALID' | 'WARNING' | 'INVALID';
  validatedAt: string | null;
  lastUsedAt: string | null;
  retentionExpiresAt: string;
  deletedAt: string | null;
  captureMetadata: Record<string, unknown>;
  qualitySummary: Record<string, unknown>;
  archive: EpisodeArchive;
  trainable: boolean;
  blockingReasons: BlockingReason[];
  recoveryAction: RecoveryAction;
  error: ApiResourceError | null;
  createdAt: string;
};

type RecoveryAction =
  | 'WAIT'
  | 'RETRY_UPLOAD'
  | 'RECORD_AGAIN'
  | 'SET_LABEL'
  | 'EXTEND_RETENTION'
  | 'CREATE_DEPLOYMENT'
  | 'RETRY'
  | 'CONTACT_SUPPORT'
  | 'NONE';

type BlockingReason = {
  code: string;
  message: string;
  recoveryAction: RecoveryAction;
};

type ApiResourceError = {
  code: string;
  message: string;
  recoveryAction: RecoveryAction;
};

type RetentionPolicy = {
  name: string;
  defaultRetentionDays: number;
  postTrainingGraceDays: number;
  maxRetentionDays: number;
  allowManualExtension: boolean;
  allowManualPurge: boolean;
};

type DataPurgeJob = {
  id: string;
  episodeId: string;
  storageNodeId: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  attempt: number;
  error: { code: string; message: string } | null;
  scheduledAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

type RobotProfile = {
  id: string;
  name: string;
  contractVersion: string;
  architecture: string;
  runtime: string;
  runtimeVersion: string;
};

type Robot = {
  id: string;
  ownerId: string;
  jetsonDeviceId: string;
  robotProfileId: string;
  name: string;
  connectionStatus: 'ONLINE' | 'OFFLINE';
  busy: boolean;
  currentDeploymentId: string | null;
  currentTaskVersionId: string | null;
  lastSeenAt: string | null;
  createdAt: string;
};

type SkillKey =
  | 'pick_place'
  | 'sort_two'
  | 'align_fixture'
  | 'present_inspect'
  | 'line_up';

type Skill = {
  id: string;
  skillKey: SkillKey;
  tier: 'TUTORIAL' | 'DEMO';
  status: 'PLANNED' | 'COLLECTING' | 'TRAINING' | 'GATE_FAILED' | 'DEPLOYED';
  displayName: string;
  description: string | null;
  persona: string | null;
  createdAt: string;
  updatedAt: string;
};

type TrainingRequirements = {
  skillId: string;
  skillKey: SkillKey;
  minimumEpisodeCount: number;
  recommendedEpisodeCount: number;
  maximumEpisodeCount: number;
  allowedSources: Array<'SIM' | 'REAL'>;
  requiresSameUmiDevice: boolean;
  requiredContractVersion: string;
  supported: boolean;
  requirementsVersion: number;
};

type TrainingProfileSnapshot = {
  profileKey: string;
  profileVersion: number;
  algorithm: 'ACT';
  hyperparameters: {
    epochs: number;
    batchSize: number;
    seed: number;
    learningRate: number;
    optimizer: string;
  };
  configSha256: string;
};

// configSha256 입력: { algorithm, hyperparameters }의 canonical JSON UTF-8 bytes

type TrainingJob = {
  id: string;
  ownerId: string;
  skillId: string;
  skillKey: SkillKey;
  umiDeviceId: string;
  assignedWorkerId: string | null;
  assignedStorageNodeId: string | null;
  gpuIndex: number | null;
  gitRev: string | null;
  inputMode: 'EXISTING_EPISODES';
  status:
    | 'CREATED'
    | 'COLLECTING'
    | 'INPUT_VALIDATING'
    | 'QUEUED'
    | 'PREPARING'
    | 'RUNNING'
    | 'MODEL_UPLOADING'
    | 'MODEL_VALIDATING'
    | 'CANCEL_REQUESTED'
    | 'CANCELED'
    | 'SUCCEEDED'
    | 'FAILED';
  trainingProfile: TrainingProfileSnapshot;
  datasetQuery: Record<string, unknown>;
  expectedEpisodeCount: number;
  receivedEpisodeCount: number;
  receivedBytes: number;
  episodeCount: number;
  collection: {
    expectedCount: number;
    uploadedCount: number;
    validatingCount: number;
    readyCount: number;
    warningCount: number;
    invalidCount: number;
    receivedBytes: number;
  };
  inputSnapshotSha256: string | null;
  progress: {
    stage: string;
    percent: number;
    epoch: number | null;
    totalEpochs: number;
  };
  attempt: number;
  resultSkillVersionId: string | null;
  error: ApiResourceError | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
};

type CheckpointMetadata = {
  architecture: string;
  framework: string;
  frameworkVersion: string;
  contractVersion: string;
  robotProfileId: string;
  trainingProfileKey: string;
  trainingProfileVersion: number;
  trainingConfigSha256: string;
  actionSpace: 'JOINT_ABSOLUTE' | 'JOINT_DELTA';
  nParams: number;
  controlRateHz: number;
  cameraNames: string[];
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
};

type SkillVersion = {
  id: string;
  skillId: string;
  trainingJobId: string;
  version: number;
  status: 'UPLOADING' | 'VALIDATING' | 'READY' | 'FAILED' | 'ARCHIVED';
  checkpoint: {
    sizeBytes: number;
    sha256: string;
  };
  metadata: CheckpointMetadata;
  gate: {
    metric: 'rollout_success' | null;
    n: number | null;
    runs: number | null;
    value: number | null;
    ciLow: number | null;
    ciHigh: number | null;
    measuredAt: string | null;
    passed: boolean;
  };
  createdAt: string;
};

type Task = {
  id: string;
  ownerId: string;
  taskKey: string;
  displayName: string;
  description: string | null;
  latestVersion: number | null;
  latestVersionDetails: TaskVersion | null;
  createdAt: string;
  updatedAt: string;
};

type TaskStep = {
  order: number;
  skillVersionId: string;
  parameters: Record<string, unknown>;
  timeoutSeconds: number | null;
};

type TaskVersion = {
  id: string;
  taskId: string;
  version: number;
  steps: TaskStep[];
  createdAt: string;
};

type DeploymentItem = {
  id: string;
  skillVersionId: string;
  gatePassed: true;
  status: 'PENDING' | 'DOWNLOADING' | 'VERIFIED' | 'FAILED';
  downloadedAt: string | null;
  verifiedAt: string | null;
  error: { code: string; message: string } | null;
};

type Deployment = {
  id: string;
  robotId: string;
  taskVersionId: string;
  status:
    | 'PENDING'
    | 'DOWNLOADING'
    | 'VERIFYING'
    | 'APPLYING'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'CANCELED';
  progressPercent: number;
  items: DeploymentItem[];
  error: { code: string; message: string } | null;
  createdAt: string;
  updatedAt: string;
  appliedAt: string | null;
};

type RobotSkillDeployment = {
  id: string;
  robotId: string;
  skillId: string;
  skillKey: SkillKey;
  skillVersionId: string;
  deploymentId: string;
  status: 'ACTIVE' | 'RETIRED';
  deployedAt: string;
  retiredAt: string | null;
};

type RobotExecution = {
  id: string;
  ownerId: string;
  robotId: string;
  taskVersionId: string;
  deploymentId: string;
  status:
    | 'REQUESTED'
    | 'STARTING'
    | 'RUNNING'
    | 'CANCEL_REQUESTED'
    | 'CANCELED'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'STATUS_UNKNOWN';
  currentStep: number | null;
  totalSteps: number;
  progressPercent: number;
  error: ApiResourceError | null;
  requestedAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  updatedAt: string;
};
```

- Public SkillVersion 응답에는 `checkpointBucket`과 `checkpointKey`를 노출하지 않는다.
- CKPT 파일 정보는 크기·SHA-256·호환성만 반환하고, 실제 접근은 Deployment manifest의 서명 URL을 사용한다.
- Episode 사용자 응답에는 StorageNode의 실제 mount 경로와 내부 `storageKey`를 노출하지 않는다.
- 원본 Episode ZIP은 S3에 저장하고 EC2 DB에는 bucket·object key·크기·SHA-256 등 메타데이터만 저장한다.
- GPU의 로컬 Episode는 학습 실행용 임시 복사본이며 시스템 원본으로 취급하지 않는다.
- ZIP 삭제 후에도 Episode, EpisodeArchive의 크기·해시·형식과 TrainingJob 사용 이력은 DB에 유지한다.

## 4. 인증 및 사용자 API

### AUTH-01 로그인

- `POST /auth/login` · 공개
- 요청: `email: string`, `password: string` 필수.
- 성공 `200`:

```json
{
  "data": {
    "accessToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "user_01",
      "email": "user@example.com",
      "name": "사용자",
      "createdAt": "2026-09-01T00:00:00Z"
    }
  }
}
```

- refresh 토큰은 `HttpOnly; Secure; SameSite=Lax` 쿠키로 전달한다.
- 실패: `401 INVALID_CREDENTIALS`, `429 RATE_LIMITED`.

### AUTH-02 로그아웃

- `POST /auth/logout` · refresh 쿠키
- 요청 본문 없음.
- 현재 refresh 세션 폐기 및 쿠키 삭제.
- 성공 `204`. 반복 요청도 `204`.

### AUTH-03 토큰 갱신

- `POST /auth/refresh` · refresh 쿠키
- 요청 본문 없음.
- 성공 `200`: `{ "data": { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 900 } }`.
- refresh 토큰 회전 및 재사용 탐지.
- 실패: `401 UNAUTHENTICATED`.

### AUTH-04 회원가입 (선택)

- `POST /auth/signup` · 공개
- 요청: `email`, `password`, `name` 필수.
- 성공 `201`: `User`.
- 실패: `409 EMAIL_ALREADY_EXISTS`, `422 VALIDATION_ERROR`.

### USER-01 내 정보 조회

- `GET /users/me` · 사용자
- 성공 `200`: `User`.

### USER-02 내 정보 수정

- `PATCH /users/me` · 사용자
- 요청: `name: string` 필수.
- 성공 `200`: 수정된 `User`.

## 5. 장치 및 로봇 API

### DEVICE-01 장치 등록

- `POST /devices` · 사용자
- 요청:

```json
{
  "deviceType": "UMI_CAMERA",
  "handleNumber": "UMI-0001",
  "name": "1번 UMI",
  "location": "801호"
}
```

- 성공 `201`: `Device`.
- `deviceType`은 사용자가 웹 화면에서 `UMI_CAMERA` 또는 `JETSON` 중 선택한다.
- 등록 직후 연결 상태는 `OFFLINE`이며 heartbeat 수신 후 갱신한다.
- 실패: `409 DEVICE_ALREADY_REGISTERED`, `422 VALIDATION_ERROR`.

### DEVICE-02 내 장치 목록

- `GET /devices` · 사용자
- 쿼리: `deviceType?: UMI_CAMERA | JETSON`, 공통 페이지네이션.
- 성공 `200`: `Device[]`.

### DEVICE-03 장치 상세

- `GET /devices/{deviceId}` · 사용자
- 성공 `200`: `Device`.

### DEVICE-04 장치 이름 수정

- `PATCH /devices/{deviceId}` · 사용자
- 요청: `name: string`, `location?: string | null`.
- 성공 `200`: 수정된 `Device`.
- 핸들 번호·장치 유형·소유자는 변경하지 않는다.

### DEVICE-05 장치 토큰 발급

- `POST /device-auth/token` · 공개 경로, 장치 secret 필수
- 요청:

```json
{
  "deviceId": "device_01",
  "deviceSecret": "..."
}
```

- 성공 `200`:

```json
{
  "data": {
    "accessToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "deviceType": "UMI_CAMERA"
  }
}
```

- 실패: `401 INVALID_CREDENTIALS`.

### DEVICE-05A UMI 사용 가능 스킬 목록

- `POST /device-auth/skills` · 공개 경로, UMI 장치 ID와 Device Secret 필수
- 사용자 로그인이나 Bearer Access Token 없이 QR 등록으로 발급받은 장치 자격증명을 직접 검증한다.
- 운영 환경에서는 Device Secret 노출 방지를 위해 HTTPS만 허용하고 요청 Body와 인증 실패 로그를 마스킹한다.
- 요청:

```json
{
  "deviceId": "51000000-0000-4000-8000-000000000001",
  "deviceSecret": "발급받은 Device Secret 원문"
}
```

- `deviceId`는 `devices.id` UUID다. `UMI-001` 같은 `handleNumber`나 표시 이름을 사용하지 않는다.
- EC2는 다음 조건을 모두 검증한다.
  1. 장치와 `device_credentials`가 존재한다.
  2. Credential 상태가 `ACTIVE`다.
  3. 제출한 Secret이 저장된 BCrypt hash와 일치한다.
  4. 장치 종류가 `UMI_CAMERA`다.
- 성공하면 `device_credentials.last_authenticated_at`을 서버 시각으로 갱신한다.
- 조회 범위는 `skills.owner_id IS NULL`인 시스템 공통 스킬과 `skills.owner_id = devices.owner_id`인 커스텀 스킬이다. 다른 사용자 소유 스킬은 반환하지 않는다.
- 성공 `200`:

```json
{
  "data": [
    {
      "id": "62000000-0000-4000-8000-000000000001",
      "skillKey": "pick_place",
      "tier": "DEMO",
      "status": "PLANNED",
      "displayName": "집어 옮기기",
      "description": "물체를 집어 지정된 위치로 옮기는 동작",
      "persona": null,
      "createdAt": "2026-09-14T01:00:00Z",
      "updatedAt": "2026-09-14T01:00:00Z"
    }
  ]
}
```

- 실패: `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`.
- 존재하지 않는 장치, 잘못된 Secret, 비활성 Credential 및 UMI가 아닌 장치는 자격증명·장치 정보 노출을 막기 위해 동일한 인증 실패 응답을 사용한다.

### DEVICE-06 QR 장치 등록 시작

- `POST /devices/{deviceId}/enrollment` · 웹 사용자 Access Token
- 사용자 소유 `UMI_CAMERA`만 등록할 수 있다.
- 기존 `ACTIVE` 등록 요청은 폐기하고 새 요청을 생성한다.
- 성공 `201 Created`:

```json
{
  "data": {
    "enrollmentId": "57f76b00-fde0-40e9-9280-cd288cad71cc",
    "deviceId": "e42371d3-673f-4c19-8c98-9e47e29439f7",
    "qrPayload": "umi://enroll?enrollmentId=...&registrationToken=...",
    "expiresAt": "2026-09-14T05:40:00Z"
  }
}
```

QR 원문 토큰은 이 응답에서만 제공하며 DB에는 소문자 SHA-256만 저장한다.

### DEVICE-07 QR 장치 등록 완료

- `POST /device-auth/enroll` · 공개 경로, QR 원문 토큰 필수

```json
{
  "enrollmentId": "57f76b00-fde0-40e9-9280-cd288cad71cc",
  "registrationToken": "QR에서 읽은 원문 토큰"
}
```

- 성공 `200`은 `deviceId`, `deviceSecret`, `enrolledAt`을 반환한다.
- Device Secret 원문은 성공 응답에서 한 번만 제공하고 DB에는 BCrypt hash만 저장한다.
- 만료는 `410 ENROLLMENT_EXPIRED`, 이미 사용·폐기는 `409 ENROLLMENT_NOT_ACTIVE`, 토큰 불일치는 `401 INVALID_ENROLLMENT_TOKEN`이다.

### DEVICE-08 장치 heartbeat

- `POST /devices/{deviceId}/heartbeat` · 해당 UMI 또는 Jetson
- 요청:

```json
{
  "firmwareVersion": "1.0.0",
  "runtimeVersion": "1.0.0"
}
```

- 성공 `200`: `{ "data": { "serverTime": "...", "nextHeartbeatSeconds": 15 } }`.
- 서버 수신 시각으로 `lastSeenAt`을 갱신한다.
- UMI는 60초, Jetson은 20초 동안 미수신 시 OFFLINE으로 판정하는 것을 초안으로 한다.

### ROBOT-01 내 로봇 목록

- `GET /robots` · 사용자
- 쿼리: 공통 페이지네이션.
- 성공 `200`: `Robot[]`.

### ROBOT-02 로봇 상세

- `GET /robots/{robotId}` · 사용자
- 성공 `200`: `Robot`.
- `currentDeploymentId`는 Jetson이 실제 적용 성공을 보고한 배포만 가리킨다.

## 6. Episode 및 S3 임시 보관 API

Episode는 UMI 시연 한 번에서 생성한 ZIP archive 한 건이다. 사용자 API는 Episode의 상태·품질 집계와 공개 archive 메타데이터만 제공하며 로컬 절대 경로, 내부 storage key, ZIP 내부 원본, StorageNode 접속 정보는 노출하지 않는다.

### EPISODE-UPLOAD-01 업로드 세션 발급

- `POST /episode/data-upload-request` · UMI Device Access Token · `Idempotency-Key` 필수
- `Content-Type: application/json`
- 요청의 `metadata`와 `summary`는 ZIP 내부 `meta.json`·`summary.json`의 API 계약 필드를 lower camel case로 복사한 선언값이다. ZIP 내부 원본 파일은 그대로 유지한다.

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "contentType": "application/zip",
  "sizeBytes": 10485760,
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "metadata": {
    "contractVersion": "episode-archive-v1",
    "schemaVersion": "1.0",
    "skillKey": "pick_place",
    "source": "REAL",
    "success": true,
    "labelStatus": "LABELED",
    "startedAt": "2026-09-11T05:30:00.123Z",
    "endedAt": "2026-09-11T05:30:12.842Z",
    "startMonotonicNs": 1000000000,
    "timestampTimebase": "MONOTONIC_NS",
    "device": { "deviceIdentifier": "UMI-001", "androidVersion": "15", "arCoreVersion": "1.48.0" },
    "camera": { "width": 1920, "height": 1080, "fx": 1200.1, "fy": 1201.3, "cx": 960.0, "cy": 540.0 }
  },
  "summary": {
    "schemaVersion": "1.0",
    "firstTimestampNs": 1000000000,
    "lastTimestampNs": 13800000000,
    "frameCount": 360,
    "poseSampleCount": 360,
    "imuSampleCount": 2400,
    "gripperSampleCount": 360,
    "missingFrameCount": 0,
    "trackingGapCount": 1,
    "trackingLostDurationNs": 83000000
  }
}
```

- `sessionId`는 소문자 UUID v4이며 ZIP 파일명은 `{sessionId}.zip`으로 파생한다.
- `contentType`은 `application/zip`, `sizeBytes`는 1 이상 서버 제한 이하, `sha256`은 소문자 hexadecimal 64자리다.
- EC2는 Device Access Token의 owner·device를 확인하고 해당 장치가 `UMI_CAMERA`인지 검증한다.
- `metadata.device.deviceIdentifier`는 선택 필드이며 누락·빈 값·`handle_number`와 다른 값을 허용한다. 장치 귀속은 Device Access Token의 서명된 owner·device로만 확정한다.
- `episodes.skill_id`는 `metadata.skillKey`로 조회한 가시 스킬로 확정한다. 시스템 공통 스킬 또는 인증 사용자 소유 커스텀 스킬만 허용하며, 없으면 `404 RESOURCE_NOT_FOUND`다.
- EC2가 `episodes/{owner UUID 소문자}/{yyyyMMdd'T'HHmmss'Z'}/{sessionId}.zip` key를 생성한다.
- 같은 owner·UMI·session과 같은 hash의 재요청은 기존 예약을 반환하고, hash나 `metadata.endedAt`이 다르면 `409 EPISODE_ID_REUSED`다.
- 성공 `201 Created`:

```json
{
  "data": {
    "uploadSessionId": "7fcf42a4-872c-4a25-918d-8f0c34c9e90a",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "uploadMethod": "PUT",
    "uploadUrl": "https://s3...signed-put-url",
    "requiredHeaders": {
      "Content-Type": "application/zip",
      "x-amz-checksum-sha256": "base64-encoded-sha256",
      "If-None-Match": "*"
    },
    "expiresAt": "2026-09-11T05:45:00Z",
    "maxUploadBytes": 16777216
  }
}
```

서명 URL과 내부 bucket·object key는 로그, Outbox, 사용자 API에 기록하거나 노출하지 않는다.

### EPISODE-UPLOAD-02 업로드 완료 통지

- `POST /episode/data-upload-request/{uploadSessionId}/complete` · UMI Device Access Token · `Idempotency-Key` 필수
- S3 PUT 성공 후 호출한다. S3 업로드 성공만으로 Episode 등록이 완료된 것으로 보지 않는다.

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "sizeBytes": 10485760,
  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

- 세 필드는 최초 업로드 세션 예약값과 일치해야 한다. 메타데이터는 완료 요청에서 다시 받지 않는다.
- EC2는 S3 HEAD/checksum으로 객체 존재·크기·checksum을 확인한다.
- 검증 컴포넌트가 이후 S3 객체를 읽어 ZIP 구조와 내부 `meta.json`·`summary.json` 및 실제 CSV·frame 집계를 비동기로 검증한다.
- 최초 접수 성공은 `202 Accepted`, 이미 동일 Episode가 접수된 재요청은 `200 OK`다.

```json
{
  "data": {
    "episodeId": "a7a95443-b21b-4bba-9624-c199343f5105",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "VALIDATING",
    "duplicate": false
  }
}
```

- 실패: `404 RESOURCE_NOT_FOUND`, `409 STATE_CONFLICT`, `409 EPISODE_ID_REUSED`, `409 IDEMPOTENCY_KEY_REUSED`, `410 UPLOAD_SESSION_EXPIRED`, `413 PAYLOAD_TOO_LARGE`, `422 CHECKSUM_MISMATCH`.

### EPISODE-UPLOAD-03 처리 상태 조회

- `GET /episode/data-upload-request/by-session/{sessionId}` · UMI Device Access Token
- S3 PUT 직후 완료 통지 전에는 S3 객체 존재 여부를 확인해 `UPLOADED`를 반환할 수 있다.

```json
{
  "data": {
    "episodeId": "a7a95443-b21b-4bba-9624-c199343f5105",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "READY",
    "qualityStatus": "VALID",
    "error": null
  }
}
```

공개 상태는 `UPLOAD_PENDING`, `UPLOADED`, `VALIDATING`, `READY`, `INVALID`, `FAILED`다. `INVALID`와 `FAILED`의 `error`는 `code`, `message`, `recoveryAction`을 포함한다.

### EPISODE-UPLOAD-04 업로드 세션 취소

- `DELETE /episode/data-upload-request/{uploadSessionId}` · UMI Device Access Token
- 업로드 대기 상태만 취소하며 S3 객체가 있으면 함께 삭제한다.
- 성공 `204 No Content`; 이미 취소된 세션의 반복 요청도 `204`다.
- 검증 접수 이후에는 `409 STATE_CONFLICT`다.

### EPISODE-UPLOAD-05 업로드 URL 재발급

- `POST /episode/data-upload-request/{uploadSessionId}/renew` · UMI Device Access Token
- 업로드 대기 상태에서 기존 bucket·object key·크기·SHA-256을 유지하고 새 Presigned PUT을 발급한다.
- 성공 `200`은 `EPISODE-UPLOAD-01`과 같은 `EpisodeUploadSessionResponse`다.
- 완료·취소된 세션은 `409 STATE_CONFLICT`다.

### EPISODE-UPLOAD-06 비동기 ZIP 검증 및 확정

완료 통지 트랜잭션이 커밋된 뒤 전용 executor에서 다음을 검증한다.

1. 모든 entry의 STORE 방식, 중복·절대경로·`..`·역슬래시·중첩 ZIP 금지
2. `meta.json`, `summary.json`, `frames.csv`, `pose.csv`, `imu.csv`, `gripper.csv`, `frames/*.jpg` 존재
3. 요청·meta·summary의 session ID, skill key, 종료 시각 일치
4. CSV header, 연속 frame index, JPEG 참조, timestamp 단조 증가와 범위
5. summary sample count와 실제 CSV·JPEG 개수 일치
6. 주요 내부 파일 SHA-256 일치
7. entry 개수 및 압축 해제 총 크기 제한

성공 시 `episodes`, `episode_archives`와 업로드 세션 `READY` 상태를 한 트랜잭션으로 저장한다. `training_job_episodes`는 이후 사용자가 해당 Episode를 선택해 TrainingJob을 생성할 때 저장한다. 계약 위반은 `INVALID`, 저장소·처리 장애는 `FAILED`로 업로드 세션에 오류 코드와 메시지를 기록한다.

### EPISODE-01 Episode 목록

- `GET /episodes` · 사용자
- 쿼리:
  - `umiDeviceId?: string`
  - `skillId?: string`
  - `skillKey?: SkillKey`
  - `source?: SIM | REAL`
  - `labelStatus?: UNLABELED | LABELED`
  - `success?: boolean`
  - `contractVersion?: string`
  - `qualityStatus?: PENDING | VALID | WARNING | INVALID`
  - `status?: Episode.status`
  - `availableForTraining?: boolean`
  - `startedFrom?: string`
  - `startedTo?: string`
  - 공통 페이지네이션
- `availableForTraining=true`는 `status=READY`, `qualityStatus IN (VALID, WARNING)`, `labelStatus=LABELED`, `success=true`, archive가 `AVAILABLE`이고 삭제 작업이 시작되지 않은 Episode만 반환한다.
- 성공 `200`: `Episode[]`.

### EPISODE-02 Episode 상세

- `GET /episodes/{episodeId}` · 사용자
- 성공 `200`: `Episode`.
- 응답의 `archive`에는 content type, archive format, 압축 방식, 크기, entry 수, SHA-256과 상태만 포함한다.
- ZIP 또는 내부 이미지·CSV의 다운로드 URL과 내부 로컬 경로는 사용자에게 제공하지 않는다.

### EPISODE-03 Episode 보관 기한 연장

- `PATCH /episodes/{episodeId}/retention` · 사용자
- 요청:

```json
{
  "retentionExpiresAt": "2026-09-15T00:00:00Z"
}
```

- 서버가 정한 최대 보관 기간과 StorageNode 용량 정책 안에서만 연장할 수 있다.
- 현재 `retentionExpiresAt`보다 이후 시각으로만 연장할 수 있으며 단축은 허용하지 않는다.
- `UPLOADING`, `VALIDATING`, `READY`, `DELETE_FAILED` 상태에서만 가능하다.
- `DELETING` 또는 `DELETED`이면 `409 STATE_CONFLICT`.
- 성공 `200`: 갱신된 `Episode`.

### EPISODE-04 Episode 조기 삭제 요청

- `POST /episodes/{episodeId}/purge` · 사용자 · `Idempotency-Key` 필수
- 요청 본문 없음.
- 해당 Episode를 사용하는 활성 TrainingJob이 있으면 `409 EPISODE_IN_USE`.
- 삭제 작업을 생성하고 Episode를 `DELETING`으로 전환한다.
- 성공 접수 `202`:

```json
{
  "data": {
    "episodeId": "episode_100",
    "purgeJobId": "purge_01",
    "status": "DELETING"
  }
}
```

- 실제 파일 삭제가 완료된 후 `DELETED`, 실패하면 `DELETE_FAILED`가 된다.
- 물리 파일 삭제 후에도 Episode와 학습 사용 이력은 DB에 유지한다.

### EPISODE-05 Episode 사용 이력

- `GET /episodes/{episodeId}/training-jobs` · 사용자
- 쿼리: 공통 페이지네이션.
- 성공 `200`: 해당 Episode를 입력으로 고정한 `TrainingJob[]`.
- 파일이 이미 삭제된 Episode도 과거 사용 이력을 조회할 수 있다.

### EPISODE-06 적용 보관 정책 조회

- `GET /episodes/retention-policy` · 사용자
- 성공 `200`:

```json
{
  "data": {
    "name": "default-local-retention",
    "defaultRetentionDays": 7,
    "postTrainingGraceDays": 2,
    "maxRetentionDays": 30,
    "allowManualExtension": true,
    "allowManualPurge": true
  }
}
```

- 사용자에게 적용되는 현재 정책을 반환한다. 실제 값은 운영 정책으로 확정한다.
- 정책 변경은 관리자 또는 배포 설정으로 수행하며 현재 MVP 사용자 API 범위에는 포함하지 않는다.

### EPISODE-07 Episode 성공 라벨 확정

- `PATCH /episodes/{episodeId}/label` · 사용자 · `Idempotency-Key` 필수
- 요청:

```json
{
  "success": true
}
```

- 사용자 소유이고 `status=READY`, `labelStatus=UNLABELED`이며 활성 TrainingJob이 참조하지 않는 Episode만 변경할 수 있다.
- 성공 시 `labelStatus=LABELED`, `labelSource=USER`, `labeledAt=서버 시각`을 함께 기록한다.
- 이미 같은 라벨이면 멱등 성공, 다른 확정 라벨을 덮어쓰려면 별도 관리자 정정 절차를 사용한다.
- 성공 `200`: 갱신된 `Episode`.
- 실패: `409 EPISODE_IN_USE`, `409 LABEL_ALREADY_FINALIZED`, `409 STATE_CONFLICT`.

### EPISODE-08 Episode 미리보기 세션 발급

- `POST /episodes/{episodeId}/preview-session` · 사용자
- 요청 본문 없음.
- 사용자 소유 Episode이며 archive가 `AVAILABLE`이고 실제 로컬 ZIP이 존재할 때만 발급한다.
- EC2는 소유권과 상태를 검증하고, GPU StorageNode가 제공하는 미리보기 전용 URL에 짧은 TTL의 토큰을 부여한다. 로컬 절대 경로와 내부 `storageKey`는 노출하지 않는다.
- 성공 `201`:

```json
{
  "data": {
    "episodeId": "episode_100",
    "status": "READY",
    "previewType": "FRAME_SEQUENCE",
    "manifestUrl": "/api/v1/episode-previews/preview_01/manifest",
    "thumbnailUrl": "/api/v1/episode-previews/preview_01/thumbnail",
    "expiresAt": "2026-09-08T06:15:00Z"
  }
}
```

- 미리보기 세션은 짧은 TTL로 만료되며 만료 후 재발급할 수 있다.
- MVP는 ZIP의 JPEG 목록을 제공하는 `FRAME_SEQUENCE`를 사용한다. 임시 MP4 생성이 확정되면 `VIDEO`를 추가한다.



## 7. 스킬 API

스킬은 시스템 공통 카탈로그와 사용자 소유 커스텀 스킬로 구성한다. `skillKey`는 FE·BE·AI 간 계약값이고 내부 UUID `skillId`는 DB 관계와 사용자 API 경로에 사용한다.

### SKILL-01 스킬 목록

- `GET /skills` · 사용자
- 쿼리: `q?: string`, `tier?: TUTORIAL | DEMO`, `status?: Skill.status`, 공통 페이지네이션.
- 성공 `200`: `Skill[]`.

### SKILL-02 스킬 상세

- `GET /skills/{skillId}` · 사용자
- 성공 `200`: `Skill`.

### SKILL-03 SkillKey로 스킬 조회

- `GET /skills/by-key/{skillKey}` · 사용자·GPU
- 성공 `200`: `Skill`.
- 정의되지 않은 값은 `404 RESOURCE_NOT_FOUND` 또는 입력 검증 단계에서 `422 INVALID_SKILL_KEY`.

### SKILL-04 스킬 버전 목록

- `GET /skills/{skillId}/versions` · 사용자
- 쿼리: `status?: SkillVersion.status`, `gatePassed?: boolean`, 공통 페이지네이션.
- 성공 `200`: `SkillVersion[]`, version 내림차순.
- 일반 사용자는 자신이 생성한 TrainingJob 결과 또는 정책상 공유된 버전만 조회한다.
- S3 bucket/key 및 서명 URL은 반환하지 않는다.

### SKILL-05 스킬 버전 상세

- `GET /skills/{skillId}/versions/{skillVersionId}` · 사용자
- 성공 `200`: `SkillVersion`.
- 다른 스킬의 version ID를 조합하면 `404`.

### SKILL-06 최신 배포 가능 버전

- `GET /skills/{skillId}/versions/latest` · 사용자
- 쿼리: `robotProfileId?: string`, `deployableOnly?: boolean`이며 기본 true.
- `deployableOnly=true`이면 `READY`, `gate.passed=true`이며 지정 로봇 프로파일과 호환되는 가장 큰 version을 반환한다.
- false이면 TaskVersion 편집을 위해 READY 최신 버전을 반환할 수 있지만 실제 배포 시 게이트를 다시 검사한다.
- 없음: `404 RESOURCE_NOT_FOUND`.
- 이 API는 TaskVersion 편집 UI의 추천값에만 사용한다. 배포 시에는 정확한 `skillVersionId`를 고정한다.

### SKILL-07 스킬 버전 보관 처리

- `POST /skills/{skillId}/versions/{skillVersionId}/archive` · 사용자
- 요청 본문 없음.
- 성공 `200`: `SkillVersion` (`ARCHIVED`).
- 호출 사용자가 해당 버전을 만든 TrainingJob 소유자이거나 관리자여야 한다.
- 활성 RobotSkillDeployment가 참조하면 `409 SKILL_VERSION_IN_USE`.
- 보관 처리는 S3 파일 즉시 삭제를 의미하지 않는다.

### SKILL-10 사용자 커스텀 스킬 생성

- `POST /skills` · 사용자 · `Idempotency-Key` 필수
- 요청: `{ "displayName": "빨간 블록 쌓기", "description": "빨간 블록을 순서대로 쌓는 동작" }`.
- 서버는 전역 고유 `custom_{UUID}` 형식의 `skillKey`를 생성한다.
- `ownerId`는 인증 사용자, `tier=CUSTOM`, `status=PLANNED`로 저장한다.
- 동일 사용자의 표시 이름은 대소문자를 무시하고 중복될 수 없다.
- 성공 `201`: 생성된 `Skill`.
- 실패 `409 SKILL_NAME_DUPLICATED`.

## 8. 학습 관리 API

학습 하이퍼파라미터는 백엔드 운영 설정이다. 사용자용 생성·수정·선택 API를 제공하지 않으며, 적용된 TrainingProfile은 TrainingJob 응답에서 읽기 전용으로 확인한다. 프로필 변경은 Flyway seed 또는 인증된 관리자 운영 절차로 새 version을 생성해 수행한다.

### TRAIN-01 학습 작업 생성

- `POST /training-jobs` · 사용자 · `Idempotency-Key` 필수
- 요청:

```json
{
  "skillKey": "pick_place",
  "umiDeviceId": "device_umi_01",
  "episodeIds": ["episode_100", "episode_101", "episode_102"]
}
```

- `skillKey`는 `SkillKey` enum 중 하나여야 한다. 일반 사용자는 새 스킬 key를 만들 수 없다.
- `umiDeviceId`는 사용자 소유 UMI여야 한다.
- 학습은 UMI가 S3에 업로드하고 서버 검증을 통과한 Episode만 사용한다.
- `episodeIds`는 1개 이상이며 중복될 수 없다. `inputMode`는 요청받지 않고 `EXISTING_EPISODES`로 고정한다.
- 학습 하이퍼파라미터는 요청받지 않는다. 요청에 `config`, `algorithm`, `epochs`, `batchSize`, `seed`, `learningRate`, `optimizer` 또는 `options`가 있으면 `400 FIELD_NOT_ALLOWED`로 거부한다.
- 서버는 작업 생성 트랜잭션에서 현재 ACTIVE TrainingProfile을 선택하고 profile key·version·전체 설정 snapshot·SHA-256을 고정한다.
- 같은 `Idempotency-Key` 재요청은 그 사이 ACTIVE 프로필이 바뀌었더라도 최초 TrainingJob과 프로필을 그대로 반환한다.
- 서버는 `skillKey`, `source`, `success`, `contractVersion`, 선택 방식이 포함된 `datasetQuery` snapshot을 생성한다. 클라이언트가 임의 JSON을 그대로 저장하게 하지 않는다.
- 성공 `201`: `TrainingJob` (`QUEUED`).
- GPU가 아직 할당되지 않은 경우에도 요청은 성공하며 UI는 할당 대기 상태를 표시한다.
- 실패: `409 SKILL_BUSY`는 사용하지 않는다. 같은 Skill에 여러 학습 작업을 허용하고 결과 version을 서로 다르게 할당한다.

새로운 커스텀 동작과 첫 학습을 함께 만들 때는 `skillKey` 대신 `newSkill`을 전달할 수 있다.

```json
{
  "newSkill": {
    "displayName": "빨간 블록 쌓기",
    "description": "빨간 블록을 순서대로 쌓는 동작"
  },
  "umiDeviceId": "device_umi_01",
  "episodeIds": ["episode_100", "episode_101"]
}
```

- `skillKey`와 `newSkill`은 정확히 하나만 전달한다.
- `newSkill`과 TrainingJob은 하나의 DB 트랜잭션에서 생성한다.
- 중간에 실패하면 두 레코드를 모두 롤백한다.
- 동일 사용자 내 동작 이름이 중복되면 `409 SKILL_NAME_DUPLICATED`.

보관 중인 Episode로 새 학습을 생성하는 요청은 다음과 같다.

```json
{
  "skillKey": "pick_place",
  "umiDeviceId": "device_umi_01",
  "episodeIds": ["episode_100", "episode_101", "episode_102"]
}
```

- `episodeIds`는 1개 이상이고 중복될 수 없다.
- 모든 Episode는 요청 사용자 소유이고 `READY`, `qualityStatus IN (VALID, WARNING)`이며 EpisodeArchive가 `AVAILABLE`이고 실제 ZIP이 존재해야 한다.
- 모든 Episode의 `skillId`는 요청 `skillKey`에 해당하는 스킬과 같아야 한다.
- MVP에서는 모든 Episode가 동일한 StorageNode에 있어야 한다. 다르면 `422 EPISODE_STORAGE_NODE_MISMATCH`.
- 서버는 요청과 선택 결과를 바탕으로 `datasetQuery` snapshot을 생성한다. 실제 입력은 Episode별 archive SHA-256·contract·source·label·품질 snapshot을 포함한 `TrainingJobEpisode`와 `inputSnapshotSha256`으로 고정한다. `datasetQuery`가 실제 입력 목록을 대체하지 않는다.
- 성공 `201`: `TrainingJob` (`QUEUED`).
- 실패: `409 EPISODE_NOT_AVAILABLE`, `422 EPISODE_INVALID`, `422 EPISODE_STORAGE_NODE_MISMATCH`.

### TRAIN-02 학습 작업 목록

- `GET /training-jobs` · 사용자
- 쿼리:
  - `skillId?: string`
  - `skillKey?: SkillKey`
  - `umiDeviceId?: string`
  - `status?: TrainingJob.status`
  - 공통 페이지네이션
- 성공 `200`: `TrainingJob[]`.

### TRAIN-03 학습 작업 상세

- `GET /training-jobs/{trainingJobId}` · 사용자
- 성공 `200`: `TrainingJob`.
- `episodeCount`와 입력 snapshot hash를 반환하되 로컬 경로와 storage key는 반환하지 않는다.

### TRAIN-04 입력 세션 발급

- **1.5.0에서 폐기**한다. TrainingJob 종속 입력 세션 대신 `EPISODE-UPLOAD-01`을 사용한다.
- 수집된 Episode는 `READY`가 된 뒤 `TRAIN-01`의 `episodeIds`로 선택하여 학습 입력에 포함한다.

### TRAIN-05·06 폐기

- `PATCH /training-jobs/{trainingJobId}/input-plan`과 `POST /training-jobs/{trainingJobId}/input-complete`는 폐기한다.
- 학습 입력은 TRAIN-01 생성 시 지정한 검증 완료 Episode 목록으로 즉시 고정한다.

### TRAIN-07 학습 취소 요청

- `POST /training-jobs/{trainingJobId}/cancel` · 사용자
- 요청 본문 없음.
- `QUEUED`, `PREPARING`은 즉시 취소 가능.
- 실행 중이면 `202`와 `CANCEL_REQUESTED`를 반환하며 GPU 확인 후 `CANCELED`.
- 종료 상태는 `409 STATE_CONFLICT`.
- 취소해도 Episode를 즉시 삭제하지 않는다. Episode는 자신의 보관 기한과 조기 삭제 정책을 따른다.

### TRAIN-08 학습 진행 요약

- `GET /skills/{skillId}/training-summary` · 사용자
- 성공 `200`:

```json
{
  "data": {
    "skillId": "skill_12",
    "skillKey": "pick_place",
    "jobCounts": {
      "collecting": 1,
      "active": 1,
      "succeeded": 3,
      "failed": 1,
      "canceled": 0
    },
    "latestJobId": "job_31",
    "latestReadySkillVersionId": "skill_ver_30",
    "latestDeployableSkillVersionId": "skill_ver_28",
    "availableEpisodeCount": 18,
    "expiringEpisodeCount": 4,
    "updatedAt": "2026-09-01T05:00:00Z"
  }
}
```

- `availableEpisodeCount`는 `READY`이고 로컬 파일이 존재해 새 학습에 사용할 수 있는 Episode 수다.
- `latestReadySkillVersionId`는 CKPT 검증 완료 버전이고 `latestDeployableSkillVersionId`는 추가로 게이트를 통과한 버전이다.
- `expiringEpisodeCount`의 기준 기간은 운영 정책으로 확정하고 응답 문서에 명시한다.

### TRAIN-09 학습 입력 Episode 목록

- `GET /training-jobs/{trainingJobId}/episodes` · 사용자
- 쿼리: 공통 페이지네이션.
- 성공 `200`: 입력 순서가 포함된 Episode 요약 목록.
- 학습 입력이 고정되기 전에는 현재 등록된 목록을, 고정된 후에는 불변 snapshot 목록을 반환한다.
- 로컬 경로, storage key, StorageNode 접속 정보는 반환하지 않는다.

### TRAIN-10 학습 작업 삭제

- `DELETE /training-jobs/{trainingJobId}` · 사용자
- `CANCELED`, `SUCCEEDED`, `FAILED` 상태만 soft delete할 수 있다.
- 이미 soft delete된 동일 소유 리소스의 재요청은 멱등하게 `204`로 처리한다.
- 성공 `204`, 진행 중이면 `409 TRAINING_DELETE_NOT_ALLOWED`.

## 9. GPU 데이터 수집 API (1.3.0에서 제외)

1.2.0의 `INGEST-01`~`INGEST-03`과 `/ingest/v1` UMI 직접 전송 계약은 폐기한다. UMI는 GPU 서버에 접속하거나 multipart ZIP을 전송하지 않는다.

- Episode 업로드는 6장의 `EPISODE-UPLOAD-01`, `EPISODE-UPLOAD-02`를 사용한다.
- ZIP 구조와 내부 파일 계약은 [인터페이스 계약 1.1.0](./인터페이스계약v1.1.0.md)의 5장을 따른다.
- GPU worker는 EC2에서 학습 작업을 claim한 뒤 EC2가 발급한 Presigned GET으로 S3 Episode를 다운로드한다.
- archive 검증 완료 보고 API는 검증 worker 계약을 확정할 때 별도 버전으로 추가한다.

## 10. GPU 내부 학습 API

내부 작업 수정 요청에는 현재 `attempt`와 `leaseToken`이 필요하다. 임대가 만료되거나 이전 attempt이면 `409 LEASE_EXPIRED`다.

### GPU-01 작업 할당

- `POST /internal/training-jobs/claim` · GPU
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "gpuIndex": 0,
  "gitRev": "a1b2c3d4",
  "storageNodeId": "storage_node_01",
  "ingestBaseUrl": "https://gpu-ingest.example",
  "supportedAlgorithms": ["BC", "ACT"],
  "availableMemoryMiB": 81920,
  "storageCapacityBytes": 2199023255552,
  "storageAvailableBytes": 1099511627776
}
```

- 서버는 S3 업로드 및 검증이 끝난 Episode가 입력으로 고정된 `EXISTING_EPISODES`, `QUEUED` 작업 하나를 원자적으로 할당한다.
- 성공 `200`:

```json
{
  "data": {
    "job": {
      "id": "job_31",
      "skillKey": "pick_place",
      "inputMode": "EXISTING_EPISODES",
      "trainingProfile": {
        "profileKey": "umi-act-default",
        "profileVersion": 1,
        "algorithm": "ACT",
        "hyperparameters": {
          "epochs": 100,
          "batchSize": 16,
          "seed": 42,
          "learningRate": 0.0001,
          "optimizer": "AdamW"
        },
        "configSha256": "9999999999999999999999999999999999999999999999999999999999999999"
      },
      "datasetQuery": {
        "skillKey": "pick_place",
        "source": "REAL",
        "success": true,
        "labelStatus": "LABELED",
        "qualityStatus": ["VALID", "WARNING"],
        "contractVersion": "episode-archive-v1"
      },
      "inputSnapshotSha256": "...",
      "episodes": [
        {
          "episodeId": "episode_100",
          "sessionId": "01J7ABCDEF123456789XYZW000",
          "qualityStatus": "VALID",
          "archive": {
            "downloadUrl": "https://s3.example.com/presigned-get-url",
            "expiresAt": "2026-09-15T01:15:00Z",
            "contentType": "application/zip",
            "sizeBytes": 10485760,
            "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          }
        }
      ]
    },
    "attempt": 1,
    "leaseToken": "...",
    "leaseExpiresAt": "2026-09-01T04:01:00Z",
    "heartbeatIntervalSeconds": 15
  }
}
```

- GPU Node Agent가 사용 가능한 GPU를 선택해 `gpuIndex`를 전달한다. 서버는 `assignedWorkerId`, `gpuIndex`, `gitRev`를 현재 attempt에 기록한다.
- GPU는 `trainingProfile`의 값을 그대로 사용하고 로컬 기본값·환경변수·CLI 옵션으로 덮어쓰지 않는다.
- GPU는 지원하지 않는 profile key·version이면 학습 시작 전에 `UNSUPPORTED_TRAINING_PROFILE`로 실패 보고한다.
- GPU는 canonical JSON으로 `configSha256`을 다시 계산하고 응답값과 다르면 `TRAINING_CONFIG_MISMATCH`로 실패 보고한다.
- 해시 입력은 profile 식별자와 상태를 제외한 `{"algorithm": ..., "hyperparameters": ...}` 객체이며 합의된 canonical JSON UTF-8 bytes를 사용한다.
- `EXISTING_EPISODES` 할당 시 `assignedWorkerId`를 설정하고 `PREPARING`으로 변경한다.
- Claim 응답은 내부 S3 key 대신 Episode별 제한 시간 Presigned GET `downloadUrl`과 `expiresAt`을 제공한다. GPU는 URL을 변경하지 않고 만료 전에 다운로드하며 URL을 로그나 영속 journal에 저장하지 않는다.
- 작업 없음 `204`.
- 같은 worker에 유효한 기존 할당이 있으면 같은 작업을 반환한다.
- 등록된 GPU 학습 자원은 `gpuIndex=0~29`이며 Worker당 최대 30개 작업을 병렬 실행할 수 있다.
- 동일 `(workerId, gpuIndex)`에는 활성 작업을 하나만 할당한다.
- Claim 전에 `storageNodeId`가 요청 Worker 소유의 `LOCAL`, `ONLINE` 노드인지와 heartbeat 유효성, 가용 저장 공간, GPU 메모리를 확인한다.
- 지원 알고리즘 조건은 후보 조회 쿼리에 적용하고, 원자적 선택에는 `FOR UPDATE SKIP LOCKED`를 사용한다.
- 만료 Lease는 복구 작업이 attempt를 증가시켜 재대기하며 최대 attempt 초과 시 `FAILED`로 종료한다.

### GPU-02 작업 heartbeat

- `POST /internal/training-jobs/{trainingJobId}/heartbeat` · GPU
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "storageNodeId": "storage_node_01",
  "attempt": 1,
  "leaseToken": "...",
  "storageAvailableBytes": 1099511627776
}
```

- 성공 `200`:

```json
{
  "data": {
    "leaseExpiresAt": "2026-09-01T04:02:00Z",
    "cancelRequested": false,
    "serverTime": "2026-09-01T04:01:00Z"
  }
}
```

- Episode 입력은 TrainingJob 생성 시 이미 고정되므로 입력 종료 신호를 제공하지 않는다.

### GPU-03 상태·진행 이벤트

- `POST /internal/training-jobs/{trainingJobId}/events` · GPU
- 요청 공통 필드:
  - `attempt: integer`
  - `leaseToken: string`
  - `eventId: string`
  - `sequence: integer >= 1`
  - `type: string`
  - `occurredAt: string`
  - `payload: object`

| type | payload | 처리 |
|---|---|---|
| `STARTED` | `{}` | QUEUED/PREPARING → RUNNING |
| `PROGRESS` | `{ stage, percent, epoch, totalEpochs, metrics? }` | 학습 진행도 갱신 |
| `TRAINING_FINISHED` | `{ checkpointSizeBytes, checkpointSha256 }` | MODEL_UPLOADING |
| `FAILED` | `{ code, message }` | FAILED |
| `CANCELED` | `{}` | CANCELED |

- 성공 `200`: `{ "data": { "accepted": true, "jobStatus": "RUNNING", "lastSequence": 12 } }`.
- 같은 `eventId`와 동일한 본문은 중복 성공으로 처리한다.
- 이전 sequence의 새 이벤트는 `409 STALE_EVENT`.
- `TRAINING_FINISHED`만으로 작업을 SUCCEEDED 처리하지 않는다. CKPT 검증 완료가 필요하다.

### GPU-04 CKPT 업로드 세션

- `POST /internal/training-jobs/{trainingJobId}/checkpoint-upload-session` · GPU · `Idempotency-Key` 필수
- 요청:

```json
{
  "attempt": 1,
  "leaseToken": "...",
  "filename": "model.ckpt",
  "modelCompletedAt": "2026-09-07T06:30:00Z",
  "contentType": "application/octet-stream",
  "sizeBytes": 7340032,
  "sha256": "...",
  "metadata": {
    "architecture": "ACT",
    "framework": "PyTorch",
    "frameworkVersion": "2.x",
    "contractVersion": "umi-policy-v1",
    "robotProfileId": "so101-v1",
    "trainingProfileKey": "umi-act-default",
    "trainingProfileVersion": 1,
    "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
    "actionSpace": "JOINT_DELTA",
    "nParams": 52341234,
    "controlRateHz": 30,
    "cameraNames": [
      "cam_front",
      "cam_wrist"
    ],
    "inputSchema": {
      "stateShape": [
        6
      ],
      "imageShape": [
        3,
        224,
        224
      ]
    },
    "outputSchema": {
      "actionShape": [
        6
      ],
      "actionType": "normalized_joint_target"
    }
  }
}
```

- EC2는 같은 Skill의 version 번호를 트랜잭션으로 할당한다.
- `SkillVersion(UPLOADING)`을 먼저 생성하고 불변 S3 key를 발급한다.
- 성공 `201`:

```json
{
  "data": {
    "skillVersionId": "skill_ver_33",
    "version": 3,
    "method": "PUT",
    "uploadUrl": "https://s3...signed-put-url",
    "expiresAt": "2026-09-07T06:45:00Z",
    "headers": {
      "Content-Type": "application/octet-stream"
    }
  }
}
```

- S3 key: `ckpt/{owner_id}/{skill_key}/v{version}/{skill_key}_{YYYYMMDD}T{HHMMSS}Z.ckpt`.
- `filename`은 로컬 결과 파일명 참고값이며 최종 S3 key는 EC2가 생성한다.
- `modelCompletedAt`은 필수 RFC 3339 UTC 값으로 FastAPI가 학습·CKPT 생성 완료 후 GPU Spring Boot에 최초 완료 통지를 발신하는 UTC 시각이다. GPU Spring Boot가 RUN-05 완료 통지의 값을 전달한다. EC2는 시작 시각·현재 시각과의 허용 오차 및 현재 attempt를 검증한다.
- 최초 유효한 요청에서 `training_jobs.model_completed_at`, version, key를 고정한다. 같은 결과의 업로드 재시도는 기존 값을 재사용하며 다른 완료 시각·hash는 충돌 처리한다.
- 새 Idempotency-Key로 URL을 재발급해도 동일 SkillVersion·key를 사용한다. 허용 시간 오차는 배포 전 확정한다.
- 기존 key를 덮어쓰지 않으며 조건부 쓰기 또는 버킷 정책으로 재사용을 차단한다.

- metadata는 CheckpointMetadata 전체 필드가 필수다. EC2는 형식·TrainingProfile·RobotProfile의 계약 버전, action space, camera 목록을 검증하고 sizeBytes·sha256·전체 메타데이터·version·key를 같은 트랜잭션에서 저장한 뒤 UPLOADING 행을 생성한다. 이 상태는 S3 객체 검증 완료를 뜻하지 않는다.
- GPU-05의 metadata·크기·hash는 예약값과 일치해야 한다. 다른 값이면 409 CHECKPOINT_RESERVATION_CONFLICT이며 예약 행을 덮어쓰지 않는다.

### GPU-05 CKPT 업로드 완료

- `POST /internal/training-jobs/{trainingJobId}/checkpoint-complete` · GPU · `Idempotency-Key` 필수
- 요청:

```json
{
  "attempt": 1,
  "leaseToken": "...",
  "skillVersionId": "skill_ver_33",
  "checkpoint": {
    "sizeBytes": 7340032,
    "sha256": "..."
  },
  "metadata": {
    "architecture": "ACT",
    "framework": "PyTorch",
    "frameworkVersion": "2.x",
    "contractVersion": "umi-policy-v1",
    "robotProfileId": "so101-v1",
    "trainingProfileKey": "umi-act-default",
    "trainingProfileVersion": 1,
    "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
    "actionSpace": "JOINT_DELTA",
    "nParams": 52341234,
    "controlRateHz": 30,
    "cameraNames": ["cam_front", "cam_wrist"],
    "inputSchema": {
      "stateShape": [6],
      "imageShape": [3, 224, 224]
    },
    "outputSchema": {
      "actionShape": [6],
      "actionType": "normalized_joint_target"
    }
  }
}
```

- GPU-04에서 예약한 metadata·크기·hash와 대조하고 다르면 409 CHECKPOINT_RESERVATION_CONFLICT. 유효한 현재 attempt만 완료 보고할 수 있다.
- 성공 접수 `202`: `{ "data": { "skillVersionId": "skill_ver_33", "status": "VALIDATING" } }`.
- EC2 또는 검증 워커가 다음을 확인한다.
  1. S3 객체 존재
  2. 크기와 SHA-256
  3. CKPT가 PyTorch ZIP 또는 legacy pickle signature를 사용하는지 확인
  4. GPU-04와 GPU-05의 전체 metadata envelope 일치
  5. RobotProfile 입출력 호환성
  6. `actionSpace` 허용 enum과 `nParams > 0`
  7. TrainingJob, TrainingProfile, 완료 요청의 profile key·version·설정 SHA-256 일치

- EC2는 안전하지 않은 pickle 역직렬화를 수행하지 않는다. CKPT 내부 메타데이터가 필요한 경우 GPU 검증 결과를 서명 가능한 별도 manifest 계약으로 추가하며, 현재 버전에서는 GPU-04/05의 metadata envelope와 S3 checksum을 기준으로 판정한다.
- 검증 성공:
  - `SkillVersion.status = READY`
  - `TrainingJob.status = SUCCEEDED`
  - `TrainingJob.resultSkillVersionId` 설정
- 검증 실패:
  - `SkillVersion.status = FAILED`
  - `TrainingJob.status = FAILED`
- 설정이 일치하지 않으면 `TRAINING_CONFIG_MISMATCH`, worker가 profile을 지원하지 않으면 `UNSUPPORTED_TRAINING_PROFILE`을 사용한다.
- 학습 종료 시 연결된 Episode의 `lastUsedAt`과 `retentionExpiresAt`을 RetentionPolicy에 따라 갱신한다.
- GPU는 학습 종료 직후 Episode ZIP을 삭제하지 않는다. 실제 삭제는 별도 DataPurgeJob 계약을 따른다.

### GATE-01 SkillVersion 게이트 결과 보고

- `POST /internal/skill-versions/{skillVersionId}/gate-result` · GPU 또는 시뮬레이션 검증 워커 · `Idempotency-Key` 필수
- 요청:

```json
{
  "metric": "rollout_success",
  "n": 120,
  "runs": 3,
  "value": 0.31,
  "ciLow": 0.23,
  "ciHigh": 0.40,
  "measuredAt": "2026-09-02T06:00:00Z"
}
```

- 모든 게이트 필드는 한 요청에서 함께 전달한다. 일부만 전달하면 `422 VALIDATION_ERROR`.
- 백엔드는 다음 조건으로 `gate.passed`를 계산하며 요청에서 passed 값을 받지 않는다.

```text
metric = rollout_success
AND n >= 100
AND runs >= 3
AND value > 0.20
AND ciLow > 0.20
```

- 성공 `200`: 갱신된 `SkillVersion`.
- 이미 DeploymentItem이 참조하는 버전의 게이트 값 변경은 `409 STATE_CONFLICT`.
- 게이트 통과는 CKPT READY와 별개다. 실제 배포 가능 조건은 `status=READY AND gate.passed=true`다.

### GPU-06 StorageNode 등록

- `POST /internal/storage-nodes/register` · GPU, 별도 Service Token 없음
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "nodeKey": "gpu-01-local-data",
  "name": "GPU 1번 로컬 데이터 볼륨",
  "storageType": "LOCAL",
  "capacityBytes": 2199023255552,
  "availableBytes": 1099511627776
}
```

- `workerId`는 요청 식별자로 사용한다. 이 API를 포함한 GPU → EC2 내부 경로에는 Service Token 또는 관련 Spring Security authority를 적용하지 않는다.
- 실제 mount root는 worker 설정에만 두고 요청 또는 DB에 전송하지 않는다.
- 동일한 `(workerId, nodeKey)` 재등록은 기존 StorageNode를 `200`으로 반환한다.
- 성공 `201`: `StorageNode`.

### GPU-07 StorageNode heartbeat

- `POST /internal/storage-nodes/{storageNodeId}/heartbeat` · 해당 GPU 또는 storage worker
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "status": "ONLINE",
  "capacityBytes": 2199023255552,
  "availableBytes": 1099511627776,
  "activeUploadCount": 1,
  "activeTrainingCount": 1
}
```

- 성공 `200`: `{ "data": { "serverTime": "...", "nextHeartbeatSeconds": 15, "acceptNewUploads": true } }`.
- 백엔드는 용량 하한을 넘으면 `acceptNewUploads=false`로 응답하고 해당 노드에 신규 수집 작업을 할당하지 않는다.
- `activeTrainingCount`는 최대 `30`이며, EC2는 GPU 보고값과 `training_jobs`의 활성 할당 수를 함께 확인한다.

### GPU 학습 할당 게이트 오류

| 오류 코드 | 조건 |
|---|---|
| `STORAGE_NODE_WORKER_MISMATCH` | StorageNode와 요청 Worker가 다름 |
| `STORAGE_NODE_TYPE_MISMATCH` | 학습 노드가 `LOCAL`이 아님 |
| `STORAGE_NODE_UNAVAILABLE` | 노드가 `ONLINE`이 아님 |
| `STORAGE_NODE_STALE` | 마지막 heartbeat가 허용 시간을 초과함 |
| `INSUFFICIENT_STORAGE` | 가용 로컬 저장 공간이 하한 미만 |
| `INSUFFICIENT_GPU_MEMORY` | 가용 GPU 메모리가 하한 미만 |
| `WORKER_BUSY` | Worker 활성 학습 수가 30개에 도달함 |
| `GPU_BUSY` | 동일 `workerId + gpuIndex`에 활성 작업이 있음 |
| `GPU_INDEX_OUT_OF_RANGE` | `gpuIndex`가 0~29 범위를 벗어남 |

## 11. 로컬 데이터 보관 및 삭제 내부 API

Lifecycle scheduler는 보관 기한이 지난 Episode를 직접 삭제하지 않고 DataPurgeJob을 생성한다. 삭제 worker가 EpisodeArchive 한 건의 실제 삭제 결과를 보고한 뒤에만 Episode를 `DELETED`로 확정한다.

### PURGE-01 삭제 작업 할당

- `POST /internal/data-purge-jobs/claim` · GPU storage worker
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "storageNodeId": "storage_node_01"
}
```

- 서버는 해당 노드의 `PENDING` 삭제 작업 하나를 원자적으로 할당한다.
- 할당 직전에 Episode가 활성 TrainingJob에 사용 중이지 않은지 다시 검사한다.
- 성공 `200`:

```json
{
  "data": {
    "purgeJobId": "purge_01",
    "episodeId": "episode_100",
    "attempt": 1,
    "leaseToken": "...",
    "archive": {
      "episodeArchiveId": "episode_archive_01",
      "storageKey": "episodes/7b5fba32-8852-4cff-a8af-761e91c6c031/20260902T071042Z/550e8400-e29b-41d4-a716-446655440000.zip",
      "sizeBytes": 524288000,
      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }
}
```

- 작업 없음 `204`.
- worker는 storage key를 정규화한 최종 경로가 허용된 root 아래인지 검사한다. symlink를 따라 root 밖의 파일을 삭제하면 안 된다.

### PURGE-02 삭제 작업 heartbeat

- `POST /internal/data-purge-jobs/{purgeJobId}/heartbeat` · 할당된 GPU storage worker
- 요청: `workerId`, `attempt`, `leaseToken`.
- 성공 `200`: 갱신된 `leaseExpiresAt`, `cancelRequested`, `serverTime`.

### PURGE-03 삭제 결과 보고

- `POST /internal/data-purge-jobs/{purgeJobId}/complete` · 할당된 GPU storage worker
- 요청:

```json
{
  "workerId": "gpu_worker_01",
  "attempt": 1,
  "leaseToken": "...",
  "reportId": "purge_report_01",
  "status": "SUCCEEDED",
  "archive": {
    "episodeArchiveId": "episode_archive_01",
    "status": "DELETED",
    "deletedAt": "2026-09-10T00:01:00Z",
    "error": null
  },
  "error": null
}
```

- 같은 `reportId`와 동일 본문은 중복 성공으로 처리한다.
- archive가 `DELETED`이면 DataPurgeJob을 `SUCCEEDED`, EpisodeArchive와 Episode를 `DELETED`로 변경한다.
- 삭제 실패 시 DataPurgeJob을 `FAILED`, Episode와 EpisodeArchive를 `DELETE_FAILED`로 변경하고 오류를 저장한다.
- archive가 이미 존재하지 않는 경우에도 허용된 경로를 확인한 뒤 멱등한 삭제 성공으로 처리할 수 있다.
- 삭제 후에도 DB의 Episode, EpisodeArchive, TrainingJobEpisode 이력은 유지한다.
## 12. VLM 계약 API (1.2.0에서 제외)

1.1.0의 `VLM-01`~`VLM-04`와 `SkillDestination`·`VlmModelVersion`·`VlmPolicy`·`VlmInferenceResult` 모델은 DB 스키마 1.2.0에서 VLM 도메인이 제외되어 삭제했다. 장 번호는 이후 장의 참조 안정성을 위해 유지한다.

## 13. Task API

### TASK-01 Task 생성

- `POST /tasks` · 사용자
- 요청:

```json
{
  "taskKey": "move_red_block",
  "displayName": "빨간 블록 옮기기",
  "description": "빨간 블록을 잡아 지정 위치에 놓는다"
}
```

- 성공 `201`: `Task`.
- `taskKey`는 소유자 범위에서 유일하고 생성 후 변경하지 않는다.
- 실패: `409 TASK_KEY_ALREADY_EXISTS`.

### TASK-02 Task 목록

- `GET /tasks` · 사용자
- 쿼리: `q?: string`, `includeLatestVersion?: boolean`(기본 `false`), 공통 페이지네이션.
- 성공 `200`: `Task[]`.
- 작업 탭의 최신 구성 미리보기가 필요한 경우 `includeLatestVersion=true`로 요청한다. 이때 각 Task의
  `latestVersionDetails`에 최신 `TaskVersion`과 정렬된 `steps`가 포함되며, 버전이 없으면 `null`이다.
  기본값에서는 `latestVersionDetails`가 `null`이므로 단순 목록 조회 비용을 늘리지 않는다.

### TASK-03 Task 상세

- `GET /tasks/{taskId}` · 사용자
- 쿼리: `includeLatestVersion?: boolean`(기본 `false`). `true`이면 최신 버전 및 단계 구성을
  `latestVersionDetails`로 함께 반환한다.
- 성공 `200`: `Task`.

### TASK-04 Task 정보 수정

- `PATCH /tasks/{taskId}` · 사용자
- 요청: `displayName?: string`, `description?: string | null`.
- 성공 `200`: 수정된 `Task`.
- 기존 TaskVersion에는 영향을 주지 않는다.

### TASK-05 TaskVersion 생성

- `POST /tasks/{taskId}/versions` · 사용자 · `Idempotency-Key` 필수
- 요청:

```json
{
  "steps": [
    {
      "order": 1,
      "skillVersionId": "skill_ver_21",
      "parameters": {},
      "timeoutSeconds": 10
    },
    {
      "order": 2,
      "skillVersionId": "skill_ver_33",
      "parameters": {
        "targetColor": "red"
      },
      "timeoutSeconds": 15
    }
  ]
}
```

- `steps`는 비어 있지 않아야 한다.
- `order`는 1부터 중복 없이 연속이어야 한다.
- 모든 SkillVersion은 사용자에게 조회 권한이 있고 `READY`여야 한다. 스킬 카탈로그 자체에는 사용자 소유권이 없다.
- 하나의 Robot에 적용 가능한 공통 RobotProfile 및 contract를 만족해야 한다.
- 성공 `201`: `TaskVersion`.
- TaskVersion과 steps는 생성 후 수정하지 않는다.
- 변경하려면 새 version을 생성한다.
- 실패: `409 SKILL_VERSION_NOT_READY`, `422 MODEL_INCOMPATIBLE`.

### TASK-06 TaskVersion 목록

- `GET /tasks/{taskId}/versions` · 사용자
- 쿼리: 공통 페이지네이션.
- 성공 `200`: `TaskVersion[]`, version 내림차순.

### TASK-07 TaskVersion 상세

- `GET /tasks/{taskId}/versions/{taskVersionId}` · 사용자
- 성공 `200`: steps가 포함된 `TaskVersion`.

### TASK-08 TaskVersion 검증

- `POST /tasks/{taskId}/versions/{taskVersionId}/validate` · 사용자
- 요청:

```json
{
  "robotId": "robot_01"
}
```

- 성공 `200`:

```json
{
  "data": {
    "valid": true,
    "errors": [],
    "warnings": []
  }
}
```

- 각 SkillVersion의 상태, RobotProfile, contractVersion, 런타임과 중복 파일을 검사한다.
- 의미상 검증 실패는 `200 valid=false`, 잘못된 요청은 `422`.

### TASK-09 Task 삭제

- `DELETE /tasks/{taskId}` · 사용자
- 활성 실행 또는 현재 적용된 배포가 없는 경우 soft delete한다.
- 이미 soft delete된 동일 소유 리소스의 재요청은 멱등하게 `204`로 처리한다.
- 성공 `204`.
- 실패 `409 TASK_RUNNING`, `409 TASK_DEPLOYED`.

## 14. 배포 및 Jetson 모델 로딩 API

### DEPLOY-01 배포 생성

- `POST /robots/{robotId}/deployments` · 사용자 · `Idempotency-Key` 필수
- 요청:

```json
{
  "taskVersionId": "task_ver_15"
}
```

- EC2는 TaskVersion의 SkillVersion을 중복 제거하여 DeploymentItem을 생성한다.
- 모든 SkillVersion이 `READY`, `gate.passed=true`이며 Robot과 호환되어야 한다.
- DeploymentItem에 `gatePassed=true`를 고정한다.
- 진행 중 Deployment가 있으면 `409 ROBOT_BUSY`.
- Robot이 OFFLINE이어도 `PENDING`으로 생성할 수 있다.
- 성공 `201`: `Deployment`.
- 실패: `409 SKILL_VERSION_GATE_FAILED`, `422 MODEL_INCOMPATIBLE`.

### DEPLOY-02 사용자용 배포 목록

- `GET /robots/{robotId}/deployments` · 사용자
- 쿼리: `status?: Deployment.status`, 공통 페이지네이션.
- 성공 `200`: `Deployment[]`.

### DEPLOY-03 배포 상세

- `GET /robots/{robotId}/deployments/{deploymentId}` · 사용자 또는 해당 Jetson
- 성공 `200`: `Deployment`.
- 사용자 응답에는 S3 key와 다운로드 URL이 포함되지 않는다.

### DEPLOY-04 배포 취소

- `POST /robots/{robotId}/deployments/{deploymentId}/cancel` · 사용자
- `PENDING`에서는 즉시 `CANCELED`.
- 다운로드 이후에는 Jetson에 취소 요청을 전달하고 실제 적용 여부를 확인한다.
- 이미 종료된 배포는 `409 STATE_CONFLICT`.

### JETSON-01 pending 배포 조회

- `GET /robots/me/deployments/pending` · Jetson
- Jetson 토큰과 연결된 Robot을 서버가 결정한다. 핸들 번호를 요청 본문에 받지 않는다.
- 성공 `200`: 다운로드 URL을 포함한 배포 manifest.
- pending 배포가 없으면 `204`.

```json
{
  "data": {
    "deploymentId": "dep_100",
    "robot": {
      "robotId": "robot_01",
      "handleNumber": "JETSON-0001",
      "robotProfileId": "so101-v1"
    },
    "task": {
      "taskId": "task_10",
      "taskVersionId": "task_ver_15",
      "version": 4,
      "displayName": "빨간 블록 옮기기"
    },
    "models": [
      {
        "deploymentItemId": "dep_item_01",
        "skillVersionId": "skill_ver_21",
        "skillKey": "pick_place",
        "version": 2,
        "downloadUrl": "https://s3...signed-url-1",
        "expiresAt": "2026-09-01T05:15:00Z",
        "sizeBytes": 5242880,
        "sha256": "1111...",
        "metadata": {
          "architecture": "ACT",
          "contractVersion": "umi-policy-v1",
          "trainingProfileKey": "umi-act-default",
          "trainingProfileVersion": 1,
          "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
          "actionSpace": "JOINT_DELTA",
          "nParams": 52341234,
          "controlRateHz": 30,
          "cameraNames": ["cam_front", "cam_wrist"]
        },
        "gatePassed": true
      },
      {
        "deploymentItemId": "dep_item_02",
        "skillVersionId": "skill_ver_33",
        "skillKey": "sort_two",
        "version": 3,
        "downloadUrl": "https://s3...signed-url-2",
        "expiresAt": "2026-09-01T05:15:00Z",
        "sizeBytes": 7340032,
        "sha256": "2222...",
        "metadata": {
          "architecture": "ACT",
          "contractVersion": "umi-policy-v1",
          "trainingProfileKey": "umi-act-default",
          "trainingProfileVersion": 1,
          "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
          "actionSpace": "JOINT_DELTA",
          "nParams": 52341234,
          "controlRateHz": 30,
          "cameraNames": ["cam_front", "cam_wrist"]
        },
        "gatePassed": true
      }
    ],
    "steps": [
      {
        "order": 1,
        "skillVersionId": "skill_ver_21",
        "parameters": {},
        "timeoutSeconds": 10
      },
      {
        "order": 2,
        "skillVersionId": "skill_ver_33",
        "parameters": {
          "targetColor": "red"
        },
        "timeoutSeconds": 15
      }
    ]
  }
}
```

- 동일 SkillVersion이 여러 step에 등장해도 `models`에는 한 번만 포함된다.
- Jetson은 `gatePassed=true`를 확인하되 이 값만 신뢰하지 않고 manifest 서명과 모델 계약을 함께 검증한다.
- Deployment 생성 당시의 TaskVersion 및 SkillVersion만 반환한다.
- 새 학습이 완료되어도 기존 Deployment manifest는 바뀌지 않는다.

### JETSON-02 배포 manifest 재발급

- `POST /robots/me/deployments/{deploymentId}/manifest` · Jetson
- 요청 본문 없음.
- 만료된 S3 URL을 재발급한다.
- 배포가 해당 Jetson에 속하고 진행 가능한 상태인지 검사한다.
- 성공 `200`: JETSON-01과 같은 manifest.
- 종료·취소된 배포는 `409 STATE_CONFLICT`.

### JETSON-03 배포 상태 보고

- `PATCH /robots/me/deployments/{deploymentId}` · Jetson
- 요청:

```json
{
  "reportId": "report_101",
  "sequence": 4,
  "status": "VERIFYING",
  "progressPercent": 70,
  "items": [
    {
      "deploymentItemId": "dep_item_01",
      "status": "VERIFIED",
      "error": null
    },
    {
      "deploymentItemId": "dep_item_02",
      "status": "DOWNLOADING",
      "error": null
    }
  ],
  "error": null
}
```

- 허용 전이:

```text
PENDING → DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED
각 비종료 상태 → FAILED
PENDING 또는 진행 중 → CANCELED
```

- 종료 상태를 이전 상태로 되돌릴 수 없다.
- `FAILED`이면 `error`가 필수다.
- `SUCCEEDED`는 모든 DeploymentItem이 `VERIFIED`이고 Jetson이 모든 모델 로드 및 TaskVersion 활성화를 완료한 뒤에만 허용한다.
- 성공 `200`: 갱신된 `Deployment`.
- `SUCCEEDED`가 처음 확정될 때만 `Robot.currentDeploymentId`를 원자적으로 갱신한다.
- `SUCCEEDED`가 처음 확정될 때 로봇·스킬별 기존 ACTIVE `RobotSkillDeployment`를 RETIRED로 바꾸고 새 버전을 ACTIVE로 기록한다.
- 실패하면 기존 currentDeployment를 유지한다.

### JETSON-04 현재 적용 Task 조회

- `GET /robots/me/deployment/current` · Jetson
- 성공 `200`: 현재 `Deployment`의 task·steps·모델 메타데이터.
- 다운로드 URL은 기본적으로 포함하지 않는다.
- 적용된 배포가 없으면 `404 RESOURCE_NOT_FOUND`.

### JETSON-05 현재 활성 스킬 목록

- `GET /robots/me/skills/active` · Jetson
- 성공 `200`: 해당 Robot의 `RobotSkillDeployment[]`.
- 모델 재시작·로컬 캐시 복구 시 서버가 인식하는 활성 버전과 로컬 상태를 비교하는 데 사용한다.

### DEPLOY-05 사용자용 현재 활성 스킬 목록

- `GET /robots/{robotId}/skills/active` · 사용자
- 성공 `200`: 해당 Robot의 `RobotSkillDeployment[]`.

## 15. 실시간 상태 API

### EVENT-01 SSE 구독

- `GET /events` · 사용자
- 헤더: `Accept: text/event-stream`, Bearer 사용자 토큰.
- 쿼리:
  - `skillId?: string`
  - `trainingJobId?: string`
  - `robotId?: string`
  - 한 요청에서 필터는 최대 하나
- 재연결 시 `Last-Event-ID` 사용.

```text
id: evt_001
event: training.updated
data: {"trainingJobId":"job_31","skillId":"skill_12","status":"RUNNING","percent":42,"occurredAt":"2026-09-01T05:00:00Z"}

```

| event | data |
|---|---|
| `training.input-progress` | `{ trainingJobId, receivedEpisodeCount, expectedEpisodeCount, receivedBytes, occurredAt }` |
| `training.progress` | `{ trainingJobId, status, stage, percent, epoch, occurredAt }` |
| `training.updated` | `{ trainingJobId, skillId, skillKey, skillDisplayName, status, resultSkillVersionId, resultSkillVersion, error, completedAt, occurredAt }` |
| `episode.updated` | `{ episodeId, skillId, skillKey, umiDeviceId, source, success, status, retentionExpiresAt, error, occurredAt }` |
| `episode.purge-updated` | `{ episodeId, purgeJobId, status, error, occurredAt }` |
| `skill-version.updated` | `{ skillId, skillKey, skillVersionId, version, status, gatePassed, occurredAt }` |
| `deployment.updated` | `{ robotId, deploymentId, status, progressPercent, occurredAt }` |
| `execution.updated` | `{ executionId, robotId, taskVersionId, deploymentId, status, currentStep, totalSteps, progressPercent, error, occurredAt }` |
| `robot-skill.updated` | `{ robotId, skillId, skillKey, skillVersionId, status, occurredAt }` |
| `device.connection` | `{ deviceId, connectionStatus, occurredAt }` |
| `robot.connection` | `{ robotId, connectionStatus, occurredAt }` |

- 15초마다 `: ping` 주석 heartbeat를 보낸다.
- 인증된 사용자가 소유한 TrainingJob, Episode, Robot 관련 이벤트만 전달한다. 스킬 카탈로그가 전역이어도 다른 사용자의 학습·배포 이벤트는 전달하지 않는다.
- 학습 완료 알림은 GPU의 `TRAINING_FINISHED`가 아니라 CKPT 검증 후 `TrainingJob.status=SUCCEEDED`가 최초 확정될 때 생성한다.
- 이벤트는 재전달될 수 있으므로 `id`로 중복 제거한다.
- 기본 재생 보관 기간은 24시간으로 제안한다.
- 보관 범위를 지난 `Last-Event-ID`는 `409 EVENT_CURSOR_EXPIRED`; REST API로 상태를 다시 조회한 뒤 재구독한다.

## 16. 비동기 명령 및 큐 계약

### 16.1 학습 입력 종료 명령 폐기

- 학습 입력은 작업 생성 시 S3의 검증 완료 Episode 목록으로 고정한다.
- `CLOSE_TRAINING_INPUT` 명령과 heartbeat의 `closeInputRequested` 필드는 사용하지 않는다.

### 16.2 로컬 데이터 삭제 명령

EC2가 보관 기한 만료 또는 사용자 요청으로 로컬 파일 삭제를 요청할 때 사용한다.

```json
{
  "commandId": "cmd_purge_01",
  "type": "PURGE_EPISODE",
  "purgeJobId": "purge_01",
  "episodeId": "episode_100",
  "storageNodeId": "storage_node_01",
  "occurredAt": "2026-09-10T00:00:00Z"
}
```

- 명령에는 로컬 절대 경로를 포함하지 않는다. StorageNode는 DB에 등록된 논리 storage key만 사용한다.
- 같은 `commandId`를 재수신해도 실제 삭제는 한 번만 수행한다.
- worker는 삭제 대상이 허용된 storage root 아래인지 다시 검증한다.
- 삭제 성공·실패는 `PURGE-03` API로 보고한다.

### 16.3 Jetson 배포 명령

Jetson heartbeat 방식이 필요하면 다음 최소 메시지를 사용한다.

```json
{
  "commandId": "cmd_100",
  "type": "APPLY_DEPLOYMENT",
  "robotId": "robot_01",
  "deploymentId": "dep_100",
  "occurredAt": "2026-09-01T05:00:00Z"
}
```

- 명령에는 CKPT 경로나 임의의 “latest” 버전을 넣지 않는다.
- Jetson은 `deploymentId`로 JETSON-01/02 manifest를 조회한다.
- ACK까지 동일 commandId를 재전달할 수 있으며 Jetson은 중복 적용하지 않는다.

### 16.4 Outbox

- Episode, DataPurgeJob, TrainingJob, SkillVersion, Deployment 상태 변경과 이벤트 발행의 일관성을 위해 outbox 패턴을 권장한다.
- 소비자는 `eventId` 또는 `commandId`로 중복 제거한다.
- 일시 오류는 재시도하고 한도 초과 시 DLQ에 보낸다.
- Episode ZIP payload와 S3 서명 URL은 큐에 넣지 않는다.

## 17. 상태 전이

### 17.1 Episode

```text
UPLOADING → VALIDATING → READY → DELETING → DELETED
                   └→ INVALID
DELETING → DELETE_FAILED → DELETING
```

- `READY`만 새 TrainingJob의 입력으로 선택할 수 있다.
- `DELETING`으로 전환하기 전에 활성 TrainingJob 참조 여부를 다시 검사한다.
- ZIP 삭제 후에도 Episode와 EpisodeArchive 메타데이터 행을 유지한다.

### 17.2 TrainingJob

```text
QUEUED
  → PREPARING
  → RUNNING
  → MODEL_UPLOADING
  → MODEL_VALIDATING
  → SUCCEEDED

비종료 상태 → CANCEL_REQUESTED → CANCELED
비종료 상태 → FAILED
```

- 모든 작업은 검증된 Episode와 snapshot을 고정한 뒤 `QUEUED`에서 시작한다.
- `SUCCEEDED`는 SkillVersion이 `READY`로 검증된 뒤에만 가능하다.
- 학습 계산 종료만으로 성공 처리하지 않는다.
- 학습 종료 후 Episode를 즉시 삭제하지 않고 `lastUsedAt`과 보관 기한을 갱신한다.

### 17.3 DataPurgeJob

```text
PENDING → RUNNING → SUCCEEDED
                 └→ FAILED → PENDING
```

- Episode당 `PENDING` 또는 `RUNNING` 삭제 작업은 하나만 허용한다.
- 삭제 성공은 worker가 EpisodeArchive 결과를 보고한 뒤에만 확정한다.
- 재시도 횟수 초과 시 `FAILED`를 유지하고 운영 경고를 발생시킨다.

### 17.4 SkillVersion

```text
UPLOADING → VALIDATING → READY
                     └→ FAILED
READY → ARCHIVED
```

- READY/ARCHIVED/FAILED 파일을 같은 S3 key에 덮어쓰지 않는다.
- FAILED 업로드 객체의 삭제 시점은 저장소 정리 정책으로 결정한다.
- READY는 CKPT 유효성을 의미하며 배포 가능과 동일하지 않다.
- 배포 가능 조건은 `status=READY AND gate.passed=true`다.
- 게이트 값이 모두 NULL이면 미평가이며 `gate.passed=false`다.

### 17.5 TaskVersion

```text
생성 시 확정된 불변 상태
```

- TaskVersion 수정·step 순서 변경 API는 제공하지 않는다.
- 변경하려면 새 version을 만든다.
- READY가 아닌 SkillVersion을 참조할 수 없다.

### 17.6 Deployment

```text
PENDING → DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED
   └────────────── 비종료 상태 ──────────────→ FAILED
PENDING/진행 중 → CANCELED
```

- 새 Deployment 성공 전까지 Robot은 기존 `currentDeploymentId`를 유지한다.
- SUCCEEDED 보고, currentDeployment 변경, 기존 ACTIVE RobotSkillDeployment 은퇴와 새 ACTIVE 행 생성은 하나의 DB 트랜잭션에서 처리한다.

### 17.7 RobotSkillDeployment

```text
ACTIVE → RETIRED
```

- `(robotId, skillId)`당 ACTIVE 행은 하나만 허용한다.
- 과거 RETIRED 행은 배포 이력으로 유지한다.

## 18. ERD 호환성

| API 리소스 | 권장 테이블 | 핵심 관계·제약 |
|---|---|---|
| User | `users` | email UNIQUE |
| Device | `devices` | owner_id FK, handle_number UNIQUE, device_type |
| StorageNode | `storage_nodes` | worker_id UNIQUE, 용량·연결 상태 |
| RetentionPolicy | `retention_policies` | 기본 보관 기간·학습 후 유예 기간 |
| Episode | `episodes` | owner·UMI·StorageNode·Skill FK, session·label·시간·품질 집계·보관 상태 |
| EpisodeArchive | `episode_archives` | Episode와 1:1, ZIP storage_key·크기·SHA-256·entry·삭제 상태 |
| TrainingJobEpisode | `training_job_episodes` | UNIQUE(training_job_id, episode_id), 입력 snapshot |
| DataPurgeJob | `data_purge_jobs` | Episode당 활성 삭제 작업 하나 |
| RobotProfile | `robot_profiles` | contract_version·runtime 규격 |
| Robot | `robots` | jetson_device_id UNIQUE, current_deployment_id nullable |
| Skill | `skills` | 전역 `skill_key` UNIQUE, tier·status·persona |
| TrainingProfile | `training_profiles` | profile_key·version UNIQUE, 시스템 ACTIVE 하나, 불변 config·SHA-256 |
| TrainingJob | `training_jobs` | profile FK, 설정 snapshot·SHA-256, dataset_query·gpu_index·git_rev·attempt |
| SkillVersion | `skill_versions` | profile·설정 SHA-256, CKPT 계약·action_space·n_params·gate 집계 |
| Task | `tasks` | UNIQUE(owner_id, task_key) |
| TaskVersion | `task_versions` | UNIQUE(task_id, version), 생성 후 불변 |
| TaskStep | `task_steps` | UNIQUE(task_version_id, step_order), skill_version_id FK |
| Deployment | `deployments` | robot_id·task_version_id FK |
| DeploymentItem | `deployment_items` | gate_passed=true 복합 FK, READY 검증 트리거 |
| RobotSkillDeployment | `robot_skill_deployments` | Robot·Skill별 ACTIVE 하나, 적용 이력 유지 |
| Idempotency | `idempotency_keys` | actor_id·method·path·key 범위 |
| 이벤트 | `outbox_events` | event_id UNIQUE, 상태 변경과 같은 트랜잭션 |

```mermaid
erDiagram
    USER ||--o{ DEVICE : owns
    DEVICE ||--o{ EPISODE : captures
    SKILL ||--o{ EPISODE : classifies
    STORAGE_NODE ||--o{ EPISODE : located_on
    RETENTION_POLICY ||--o{ EPISODE : applies
    EPISODE ||--|| EPISODE_ARCHIVE : has
    STORAGE_NODE ||--o{ EPISODE_ARCHIVE : stores
    SKILL ||--o{ TRAINING_JOB : trains
    TRAINING_PROFILE ||--o{ TRAINING_JOB : configures
    DEVICE ||--o{ TRAINING_JOB : collects_for
    STORAGE_NODE ||--o{ TRAINING_JOB : assigned_to
    TRAINING_JOB ||--|{ TRAINING_JOB_EPISODE : uses
    EPISODE ||--o{ TRAINING_JOB_EPISODE : selected_by
    EPISODE ||--o{ DATA_PURGE_JOB : purge_target
    STORAGE_NODE ||--o{ DATA_PURGE_JOB : executes
    TRAINING_JOB ||--o| SKILL_VERSION : produces
    TRAINING_PROFILE ||--o{ SKILL_VERSION : recorded_in
    SKILL ||--o{ SKILL_VERSION : versions
    TASK ||--o{ TASK_VERSION : versions
    TASK_VERSION ||--|{ TASK_STEP : contains
    SKILL_VERSION ||--o{ TASK_STEP : referenced_by
    ROBOT ||--o{ DEPLOYMENT : receives
    TASK_VERSION ||--o{ DEPLOYMENT : deployed_as
    DEPLOYMENT ||--|{ DEPLOYMENT_ITEM : contains
    SKILL_VERSION ||--o{ DEPLOYMENT_ITEM : downloads
    ROBOT ||--o{ ROBOT_SKILL_DEPLOYMENT : runs
    SKILL ||--o{ ROBOT_SKILL_DEPLOYMENT : assigns
    SKILL_VERSION ||--o{ ROBOT_SKILL_DEPLOYMENT : activates
    DEPLOYMENT ||--o{ ROBOT_SKILL_DEPLOYMENT : establishes
```

### 18.1 Episode 및 로컬 archive 컬럼

DB에는 로컬 절대 경로 대신 다음 내부 정보를 저장한다.

```text
episodes.storage_node_id
episodes.skill_id
episodes.session_id
episodes.source
episodes.success
episodes.label_status
episodes.contract_version
episodes.timestamp_timebase
episodes.frame_count
episodes.quality_status
episodes.retention_expires_at
episodes.last_used_at
episode_archives.storage_key
episode_archives.content_type
episode_archives.compression_method
episode_archives.sha256
episode_archives.size_bytes
episode_archives.uncompressed_size_bytes
episode_archives.entry_count
episode_archives.status
```

- `storage_key` 형식: `episodes/{owner UUID 소문자}/{yyyyMMdd'T'HHmmss'Z'}/{session UUID v4 소문자}.zip`.
- StorageNode의 실제 mount root는 worker 설정에서 관리하고 API로 배포하지 않는다.
- 사용자 API에는 `storage_node_id`, `storage_key`, 절대 경로를 노출하지 않는다.
- ZIP이 삭제되어도 size, SHA-256, 형식, 품질 집계와 학습 사용 관계는 유지한다.

### 18.2 TrainingProfile 및 TrainingJob 설정 컬럼

```text
training_profiles.profile_key
training_profiles.version
training_profiles.status
training_profiles.algorithm
training_profiles.hyperparameters
training_profiles.config_sha256
training_jobs.training_profile_id
training_jobs.training_config_snapshot
training_jobs.training_config_sha256
```

- TrainingJob 생성 시점의 ACTIVE 프로필을 서버가 선택한다.
- 프로필 FK, snapshot과 SHA-256은 작업 생성 후 변경하지 않는다.
- 프로필 값 변경은 기존 행 수정이 아니라 새 version 생성으로 처리한다.
- 사용자 API는 `trainingProfile`을 읽기 전용으로만 반환하며 storage 내부 ID는 노출하지 않는다.

### 18.3 SkillVersion의 CKPT·게이트 컬럼

DB 내부에서 다음 필드를 저장한다.

```text
checkpoint_bucket
checkpoint_key
checkpoint_sha256
checkpoint_bytes
training_profile_id
training_config_sha256
action_space
n_params
gate_metric
gate_n
gate_runs
gate_value
gate_ci_low
gate_ci_high
gate_measured_at
gate_passed
```

- `checkpoint_bucket`과 `checkpoint_key`는 내부 필드다.
- 사용자 및 Jetson 일반 조회 응답에 노출하지 않는다.
- S3 다운로드 URL은 배포 manifest 생성 시점에만 발급한다.
- DB 경로와 S3 객체가 불일치하면 SkillVersion을 READY로 만들지 않는다.
- TrainingJob·TrainingProfile·완료 요청·CKPT manifest의 학습 설정 SHA-256이 불일치하면 READY로 만들지 않는다.
- 게이트 집계 필드는 전부 NULL이거나 전부 채워져야 한다. `gate_passed`는 DB 계산값이며 API 입력으로 받지 않는다.

### 18.4 충돌 방지 DB 제약

```sql
UNIQUE (worker_id)                                  -- storage_nodes
UNIQUE (owner_id, umi_device_id, session_id)        -- episodes
UNIQUE (episode_id)                                 -- episode_archives
UNIQUE (storage_node_id, storage_key)               -- episode_archives
UNIQUE (training_job_id, episode_id)                 -- training_job_episodes
UNIQUE (training_job_id, input_order)                -- training_job_episodes
UNIQUE (profile_key, version)                        -- training_profiles
UNIQUE ACTIVE profile                                -- training_profiles partial index
UNIQUE (id, training_profile_id, training_config_sha256) -- training_jobs, 복합 FK 대상
UNIQUE (skill_key)
UNIQUE (skill_id, version)
UNIQUE (training_job_id)
UNIQUE (owner_id, task_key)
UNIQUE (task_id, version)
UNIQUE (task_version_id, step_order)
UNIQUE (deployment_id, skill_version_id)
UNIQUE (id, gate_passed)                             -- skill_versions, gate FK 대상
UNIQUE (training_job_id, attempt, event_id)
UNIQUE (deployment_id, report_id)

FOREIGN KEY (skill_versions.training_job_id,
             skill_versions.training_profile_id,
             skill_versions.training_config_sha256)
  REFERENCES training_jobs (id, training_profile_id, training_config_sha256)
```

PostgreSQL을 사용한다면 진행 중인 Deployment는 Robot당 하나만 허용하는 부분 인덱스를 권장한다.

```sql
CREATE UNIQUE INDEX uq_robot_active_deployment
ON deployments (robot_id)
WHERE status IN ('PENDING', 'DOWNLOADING', 'VERIFYING', 'APPLYING');

CREATE UNIQUE INDEX uq_episode_active_purge_job
ON data_purge_jobs (episode_id)
WHERE status IN ('PENDING', 'RUNNING');

CREATE UNIQUE INDEX uq_robot_active_skill
ON robot_skill_deployments (robot_id, skill_id)
WHERE status = 'ACTIVE';
```

Episode 삭제 대상은 다음 조건을 모두 만족해야 한다.

```text
episode.status IN (READY, DELETE_FAILED)
AND episode.retention_expires_at <= serverNow
AND 해당 Episode를 참조하는 활성 TrainingJob이 없음
```

권장 보관 기한 계산은 다음과 같다.

```text
retentionExpiresAt = MAX(
  validatedAt + defaultRetentionDays,
  lastTrainingFinishedAt + postTrainingGraceDays
)
```

## 19. 전체 정상 시나리오

### 19.1 신규 Episode 수집 및 학습

1. UMI가 Episode ZIP을 S3에 직접 업로드하고 EC2 검증을 완료한다.
2. 사용자가 Skill과 `READY` Episode 목록을 선택한다.
3. 사용자가 하이퍼파라미터 없이 `POST /training-jobs`로 학습 작업을 생성한다.
4. EC2가 Episode snapshot과 ACTIVE TrainingProfile의 key·version·설정 SHA-256을 고정한다.
5. GPU worker가 작업을 claim하고 Presigned GET URL로 Episode를 다운로드한다.
6. GPU가 크기·SHA-256·archive 계약을 재검증한 뒤 FastAPI 학습을 실행한다.
7. GPU가 진행 이벤트를 EC2에 보고한다.
8. GPU가 CKPT 업로드 세션을 발급받고 S3에 업로드한다.
9. GPU가 전체 metadata envelope와 CKPT 완료 정보를 EC2에 전달한다.
10. EC2가 S3 객체·SHA-256·CKPT 구조·RobotProfile·TrainingProfile 일치를 검증한다.
11. SkillVersion이 READY가 되고 TrainingJob이 SUCCEEDED가 된다.
12. Episode의 `lastUsedAt`과 `retentionExpiresAt`을 정책에 따라 갱신한다.
13. 검증 워커가 rollout 결과를 보고하고 백엔드가 `gate.passed`를 계산한다.

### 19.2 보관 Episode 재학습

1. 사용자가 `READY` Episode 목록을 조회한다.
2. 정확한 `episodeIds`로 새 TrainingJob을 생성한다. `inputMode`는 서버가 `EXISTING_EPISODES`로 고정한다.
3. EC2가 모든 Episode의 소유자, 상태, 실제 파일, StorageNode를 검증한다.
4. Episode 목록과 입력 snapshot hash를 TrainingJob에 불변으로 고정한다.
5. 데이터가 위치한 StorageNode의 GPU worker가 작업을 claim한다.
6. 이후 CKPT 업로드·검증 및 SkillVersion 생성은 신규 수집 학습과 동일하다.
7. 학습 종료 시 Episode의 `lastUsedAt`과 `retentionExpiresAt`을 정책에 따라 연장한다.

### 19.3 보관 만료 및 삭제

1. Lifecycle scheduler가 `retentionExpiresAt`이 지난 Episode를 조회한다.
2. 활성 TrainingJob 참조와 Episode 상태를 검사한다.
3. EC2가 DataPurgeJob을 생성하고 Episode를 `DELETING`으로 전환한다.
4. 해당 StorageNode worker가 허용된 root 아래의 Episode ZIP만 삭제한다.
5. worker가 EpisodeArchive 삭제 결과를 EC2에 보고한다.
6. archive 삭제 성공 시 Episode를 `DELETED`로 전환한다.
7. 실패 시 `DELETE_FAILED`로 기록하고 재시도한다.
8. Episode, EpisodeArchive, TrainingJobEpisode 메타데이터는 감사·추적을 위해 유지한다.

### 19.4 모델 로딩

1. 사용자가 READY SkillVersion들로 TaskVersion을 생성한다.
2. 사용자가 Robot에 TaskVersion을 지정해 배포를 요청한다.
3. EC2가 모든 SkillVersion의 `gate.passed=true`와 RobotProfile 호환성을 검증한다.
4. EC2가 Deployment와 중복 제거된 DeploymentItem을 생성한다.
5. Jetson이 pending Deployment를 조회한다.
6. EC2가 고정된 TaskStep과 SkillVersion에 대한 제한 시간 S3 URL을 반환한다.
7. Jetson이 CKPT를 임시 경로로 다운로드한다.
8. Jetson이 크기·SHA-256·contract·actionSpace·카메라·입출력 규격을 검증한다.
9. Jetson이 모든 모델의 임시 로드 테스트를 수행한다.
10. 전체 성공 시 TaskVersion을 원자적으로 활성화한다.
11. Jetson이 Deployment SUCCEEDED를 보고한다.
12. EC2가 Robot.currentDeploymentId와 RobotSkillDeployment ACTIVE 상태를 갱신한다.
13. 실패 시 기존 currentDeployment와 기존 활성 스킬을 유지한다.

## 20. 구현·검증 체크리스트

- [ ] Episode ZIP binary가 DB 또는 영구 S3 영역에 저장되지 않는지 확인
- [ ] 사용자 API와 로그에 로컬 절대 경로·storage key가 노출되지 않는지 확인
- [ ] 업로드 중 `.part` 파일과 검증 실패 파일의 정리 확인
- [ ] `(ownerId, umiDeviceId, sessionId)` 중복 업로드 방지
- [ ] Episode마다 archive 한 건과 크기·SHA-256·STORE 방식 검증
- [ ] 필수 ZIP entry, Zip Slip·symlink·nested ZIP·크기/entry 제한 검증
- [ ] JSON/CSV schema, frame mapping, timestamp·단위·좌표계 계약 검증
- [ ] Episode의 skillId·source·labelStatus·success·contractVersion·qualityStatus 저장 및 검색 검증
- [ ] 정의되지 않은 skillKey로 학습·배포 요청 차단
- [ ] `READY` Episode만 새 학습 입력으로 선택 가능
- [ ] 한 TrainingJob의 Episode가 동일 StorageNode에 존재하는지 확인
- [ ] TrainingJob 입력 snapshot 생성 후 Episode 목록 변경 차단
- [ ] 활성 TrainingJob이 참조하는 Episode 삭제 차단
- [ ] 보관 기한 만료만으로 `DELETED` 처리하지 않고 실제 worker 보고 확인
- [ ] 삭제 worker의 경로 정규화 및 허용된 storage root 이탈 차단
- [ ] DataPurgeJob 중복·역순·lease 만료 보고 차단과 실패 재시도 확인
- [ ] 물리 ZIP 삭제 후 Episode·EpisodeArchive·TrainingJobEpisode 메타데이터 유지
- [ ] 로컬 디스크 용량 하한 도달 시 신규 업로드 차단
- [ ] UMI/GPU/Jetson 토큰의 역할 교차 호출 차단
- [ ] 핸들 번호만으로 입력·다운로드 권한을 얻을 수 없음
- [ ] 같은 학습 요청·Episode archive·이벤트·완료 요청 중복 처리 방지
- [ ] 학습 생성 요청에 `config` 또는 개별 하이퍼파라미터가 있으면 FIELD_NOT_ALLOWED 반환
- [ ] TrainingJob 생성 시 ACTIVE TrainingProfile을 원자적으로 선택하고 snapshot·SHA-256 고정
- [ ] 프로필 활성 버전 변경이 기존 QUEUED/RUNNING TrainingJob에 영향을 주지 않음
- [ ] GPU가 지원하지 않는 프로필 또는 설정 hash 불일치를 학습 시작 전에 차단
- [ ] TrainingJob·TrainingProfile·완료 요청·CKPT manifest 설정 SHA-256 일치 검증
- [ ] 동시에 끝난 같은 Skill 학습이 서로 다른 version 번호를 받음
- [ ] 기존 SkillVersion CKPT S3 key 덮어쓰기 차단
- [ ] CKPT 크기·SHA-256·내부 계약 검증 전 READY 처리 차단
- [ ] CKPT actionSpace·nParams 필수 검증
- [ ] 게이트 필드 부분 저장 차단과 gatePassed 서버 계산 확인
- [ ] gatePassed=false 또는 ARCHIVED SkillVersion의 DeploymentItem 생성 차단
- [ ] GPU의 TRAINING_FINISHED만으로 TrainingJob 성공 처리 차단
- [ ] TaskVersion이 READY 상태의 정확한 skillVersionId만 참조
- [ ] TaskVersion 생성 후 step 변경 차단
- [ ] 새 SkillVersion 생성이 기존 TaskVersion과 Deployment에 영향 없음
- [ ] Jetson 응답에 내부 bucket/key 미노출
- [ ] 만료된 다운로드 URL 재발급 및 다른 Robot의 manifest 접근 차단
- [ ] Jetson이 모든 CKPT를 검증하기 전 APPLYING/SUCCEEDED 처리 차단
- [ ] 배포 실패 시 기존 currentDeployment 유지
- [ ] 같은 Robot에 진행 중 Deployment 중복 생성 차단
- [ ] Robot·Skill별 ACTIVE RobotSkillDeployment 하나만 유지
- [ ] SSE 단절 후 REST 조회로 상태 복구
- [ ] 토큰·서명 URL·장치 secret 로그 마스킹
- [ ] 원격 통신 단절과 무관한 Jetson 로컬 안전 정지 검증

## 21. 구현 전 확정할 사항

1. GPU ingest endpoint의 네트워크 접근 방식과 TLS 인증서.
2. GPU 로컬 storage root, 디스크 용량, 경고·업로드 차단 임계값.
3. 기본 보관 기간, 학습 후 유예 기간, 최대 연장 기간과 조기 삭제 권한.
4. 삭제 scheduler 주기, 최대 재시도 횟수, worker 장기 OFFLINE 처리 정책.
5. 로컬 디스크 장애 시 데이터 손실 허용 여부와 RAID·NAS·복제 필요성.
6. 다중 GPU 환경에서 데이터 지역성, 노드 간 복제 또는 공유 스토리지 정책.
7. Episode ZIP 최대 크기·entry 수, JSON/CSV schema, 좌표계·단위·시간 동기화 및 품질 임계값.
8. 학습 수집 중 GPU worker lease가 만료됐을 때 입력과 부분 파일 처리 정책.
9. 고정 epochs·batch size·seed·learning rate·optimizer 최종값.
10. TrainingProfile canonical JSON 직렬화, SHA-256 및 ACTIVE 전환 절차.
11. GPU worker가 지원할 TrainingProfile key·version과 설정 override 차단 방식.
12. CKPT 내부 필수 메타데이터 및 안전한 검증 방식.
13. 게이트 재측정 허용 여부, 측정 주체, `rollout_success` 집계 방식.
14. S3 버킷 이름, 버전 관리, 객체 잠금·삭제 정책.
15. RobotProfile의 contractVersion, 카메라, 입력·출력 규격.
16. TaskStep의 지원 parameters와 timeout 의미.
17. TaskVersion에 서로 다른 architecture의 SkillVersion을 혼합할 수 있는지.
18. Jetson 모델 캐시·용량 제한·기존 버전 보관 개수.
19. 배포 URL 만료 시간과 대용량 CKPT 재개 다운로드 지원 여부.
20. 실제 로봇의 시작·중지·통신 두절·복구 안전 정책.

---

이 문서는 UMI 시연 원본을 무압축 Episode ZIP으로 S3에 정책 기간 동안 보관하고 CKPT도 S3에 저장한다는 범위를 기준으로 작성했다. 학습 하이퍼파라미터는 사용자 요청에서 받지 않고 백엔드가 버전 있는 TrainingProfile로 고정하며, TrainingJob과 CKPT의 설정 SHA-256 일치를 검증한다. 보관 기간 안에는 READY Episode를 새 TrainingJob에 재사용할 수 있지만, 물리 ZIP 삭제 이후에는 메타데이터와 사용 이력만 조회할 수 있다.
## 22. GPU Spring Boot ↔ FastAPI 학습 실행 계약

### 22.1 배치 및 책임

EC2 Spring Boot가 학습 대기 큐·claim·lease·최종 상태와 배포 게이트를 관리한다. GPU Spring Boot가 GPU-01~07·PURGE-01~03의 EC2 호출 주체이며 S3에서 Episode를 다운로드한다. Python은 EC2 PostgreSQL에 직접 접근하거나 EC2 작업을 별도로 claim하지 않는다.

| 구성 요소 | 책임 |
|---|---|
| EC2 Spring Boot | TrainingJob·입력 snapshot·TrainingProfile·lease·SkillVersion·게이트·Outbox·SSE |
| GPU Spring Boot | S3 Episode 다운로드·재검증, EC2 claim·heartbeat, Python 실행 제어, 진행 보고, CKPT S3 업로드·완료 보고, 로컬 복사본 정리 |
| FastAPI | 실행 접수·조회·취소·실행 임대 관리, 로컬 실행 기록 복구 |
| 별도 Python 프로세스 | 전처리·고정 설정 학습·진행 기록·CKPT 생성 |
| 시뮬레이션·검증 worker | 평가 계산 및 GATE-01 직접 보고, EC2가 검증·저장·배포 판정 |

FastAPI는 GPU 서버의 별도 내부 주소에서 서비스한다. 아래 경로에는 EC2의 /api/v1 prefix를 붙이지 않는다. 전용 Bearer 서비스 자격증명과 네트워크 접근 제한을 사용하고 호출자의 workerId·storageNodeId를 인증 정보와 대조한다. EC2 leaseToken과 사용자 토큰은 FastAPI에 전달하지 않는다.

대기 큐는 EC2에 하나만 둔다. FastAPI는 즉시 실행 자원을 예약할 수 있을 때만 접수하고 GPU가 점유 중이면 409 GPU_BUSY를 반환한다. HTTP 요청 처리와 실제 학습은 별도 프로세스로 분리한다.

### 22.2 RUN-01 학습 실행 접수

- POST /internal/training-runs · GPU Spring Boot → FastAPI
- 요청 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trainingJobId | string | 예 | EC2 작업 ID |
| attempt | integer | 예 | 현재 EC2 실행 시도, 1 이상 |
| workerId | string | 예 | 인증된 GPU Spring Boot worker |
| storageNodeId | string | 예 | 공유 볼륨의 StorageNode |
| gpuIndex | integer | 예 | 예약할 GPU 번호 |
| trainingProfile | TrainingProfileSnapshot | 예 | EC2 claim에서 받은 원본 고정 설정·SHA-256 |
| inputSnapshotSha256 | string | 예 | EC2 고정 입력 목록 hash |
| episodes | array | 예 | inputOrder, episodeId, storageKey, sha256, contractVersion을 포함한 고정 목록 |
| executionLeaseExpiresAt | RFC 3339 UTC | 예 | EC2 lease 만료보다 늦지 않은 실행 허용 기한 |

runId는 trainingJobId + ":" + attempt로 결정한다. path parameter로 전달할 때 URL 인코딩한다. 동일 worker의 동일 tuple·동일 불변 입력 재요청은 기존 실행을 반환한다. 다른 입력은 409 RUN_REQUEST_CONFLICT다. executionLeaseExpiresAt은 입력 hash에서 제외하며 RUN-01 재요청으로 갱신하지 않고 RUN-04로만 갱신한다.

FastAPI는 로컬 영속 실행 기록과 GPU 예약을 확보한 뒤 응답한다. 검증 전 임의 프로세스를 시작하지 않으며, 지원하지 않는 프로필은 422 UNSUPPORTED_TRAINING_PROFILE, hash 불일치는 422 TRAINING_CONFIG_MISMATCH다.

- 최초 성공 202, 동일 실행 재조회 성격의 중복 요청 200:

```json
{
  "data": {
    "runId": "job_31:1",
    "trainingJobId": "job_31",
    "attempt": 1,
    "status": "STARTING"
  }
}
```

응답 유실 시 같은 tuple로 조회 또는 재요청한다. 새 attempt를 GPU 측에서 임의 생성하지 않는다. 다른 tuple을 접수할 때도 해당 gpuIndex의 이전 프로세스가 실제 종료되었는지 확인한다.

### 22.3 RUN-02 학습 상태·결과 조회

- GET /internal/training-runs/{runId} · GPU Spring Boot → FastAPI
- 성공 200:

```json
{
  "data": {
    "runId": "job_31:1",
    "trainingJobId": "job_31",
    "attempt": 1,
    "status": "COMPLETED",
    "sequence": 18,
    "stage": "CHECKPOINT_READY",
    "percent": 100,
    "epoch": 100,
    "totalEpochs": 100,
    "lastProgressAt": "2026-09-07T06:29:58Z",
    "lastProcessHeartbeatAt": "2026-09-07T06:30:12Z",
    "modelCompletedAt": "2026-09-07T06:30:00Z",
    "result": {
      "checkpointStorageKey": "training-results/job_31/1/model.ckpt",
      "sizeBytes": 7340032,
      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "metadata": {
        "architecture": "ACT",
        "framework": "PyTorch",
        "frameworkVersion": "2.x",
        "contractVersion": "umi-policy-v1",
        "robotProfileId": "so101-v1",
        "trainingProfileKey": "umi-act-default",
        "trainingProfileVersion": 1,
        "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
        "actionSpace": "JOINT_DELTA",
        "nParams": 52341234,
        "controlRateHz": 30,
        "cameraNames": ["cam_front", "cam_wrist"],
        "inputSchema": {"stateShape": [6]},
        "outputSchema": {"actionShape": [6]}
      }
    },
    "error": null
  }
}
```

- status: STARTING, RUNNING, CANCEL_REQUESTED, CANCELED, COMPLETED, FAILED, LOST.
- CKPT 생성 완료 전에는 result를 null로 반환한다. modelCompletedAt은 최초 RUN-05 발신 준비 시 영속 기록하며 그 전에는 null이다. 상태 조회만으로 완료 통지를 생략하지 않는다.
- sequence는 실행별 단조 증가하는 로컬 상태 revision이며 재시작 후에도 유지한다.
- COMPLETED는 Python 계산 및 최종 CKPT 파일 생성 완료다. result.metadata는 CheckpointMetadata 전체 계약을 따른다.
- modelCompletedAt은 FastAPI가 학습·CKPT 생성 완료 후 GPU Spring Boot에 최초 완료 통지를 발신하는 UTC 시각이다. 최종 CKPT 생성·닫기·크기·hash 확인 후에만 COMPLETED와 result를 제공한다.
- 체크포인트 경로는 공유 결과 root 기준 상대 key다. DB 또는 S3 key로 사용하지 않는다.
- error는 실패·유실 시 { code, message }, 그 외 null이다.
- 존재하지 않는 실행은 404 RUN_NOT_FOUND다. 존재하던 기록의 실행 상태를 복구할 수 없으면 LOST와 EXECUTION_STATE_LOST를 기록한다.
- 진행률 정체만으로 실패 판정하지 않는다. 학습 프로세스 heartbeat·종료 상태를 함께 확인한다.

### 22.4 RUN-03 취소

- POST /internal/training-runs/{runId}/cancel
- 요청: reason 문자열 필수.
- 접수 202: { data: { runId, status: "CANCEL_REQUESTED" } }.
- 실제 프로세스 종료 확인 후 CANCELED. 이미 terminal 상태면 현재 상태를 200으로 반환하고 상태를 덮어쓰지 않는다.
- 제한 시간 내 정상 종료되지 않으면 프로세스 그룹 종료를 수행한다. 종료 grace period는 배포 설정으로 관리한다.
- Spring Boot는 FastAPI의 CANCELED 확인 후 EC2에 취소 결과를 보고한다. EC2가 취소를 확정한 작업은 늦은 COMPLETED로 성공 전환하지 않는다.

### 22.5 RUN-04 실행 임대 갱신

- PATCH /internal/training-runs/{runId}/lease
- 요청: executionLeaseExpiresAt RFC 3339 UTC 필수.
- 성공 200: { data: { runId, executionLeaseExpiresAt } }.
- GPU Spring Boot는 EC2 heartbeat 성공 후 EC2가 반환한 lease 만료 이하의 기한으로 갱신한다. EC2 갱신 실패 시 로컬 임대를 연장하지 않는다.
- 만료된 실행은 409 EXECUTION_LEASE_EXPIRED, terminal 실행은 409 RUN_STATE_CONFLICT다. 종료된 실행을 되살리지 않는다.
- FastAPI와 실행 supervisor는 만료 시 프로세스를 중단한다. FastAPI 재시작 여부와 무관하게 supervisor가 임대를 확인할 수 있어야 한다.
- 호스트 간 시간 동기화를 적용하고 임대 갱신 주기는 EC2 heartbeat 주기보다 충분히 짧은 요청 timeout으로 운영한다.

### 22.6 상태 매핑·파일·복구

| Python 상태 | GPU Spring Boot 처리 |
|---|---|
| STARTING | EC2 PREPARING에 대응 |
| RUNNING | EC2 RUNNING 진행 이벤트 발행 |
| COMPLETED | RUN-05 통지 확인 → 파일 재검증 → GPU-04 → S3 PUT → GPU-05; EC2 검증 전 성공 처리 금지 |
| CANCEL_REQUESTED / CANCELED | EC2 취소 상태와 대조 후 실제 종료 보고 |
| FAILED / LOST | 현재 lease·attempt가 유효한 경우 EC2 실패 이벤트 보고 |

Spring Boot만 EC2 이벤트 sequence와 eventId를 발급한다. FastAPI sequence와 별도로 관리하며 로컬 영속 전달 기록으로 재전송 시 동일 eventId를 사용한다. EC2가 상태의 최종 기준이다.

로컬 실행 journal에는 runId·요청 hash·attempt·GPU 예약·프로세스 식별 정보·상태·sequence·실행 lease·결과 위치를 저장한다. Spring Boot 재시작 시 EC2의 기존 할당과 runId 상태를 대조한 후 제어를 재개한다. 프로세스 ID 하나만으로 동일 실행이라고 판단하지 않는다.

FastAPI 재시작 시 supervisor의 실제 실행 상태와 journal을 복구한다. 활성 실행 여부를 확인하지 못한 GPU는 재사용하지 않고 이전 프로세스 종료를 확인한다. 기록 없이 응답만 재생하거나 중복 학습을 시작하지 않는다.

Spring Boot와 Python은 동일 데이터를 공유 볼륨으로 접근한다. mount root는 각 서비스 설정에서 관리하고 논리 storage key의 매핑은 동일해야 한다. 원본 Episode를 HTTP로 재전송하지 않는다. 경로 이탈과 symlink 우회를 차단하고 학습 실행 중 원본은 변경하지 않는다.

CKPT 업로드와 EC2 검증 완료까지 로컬 결과를 유지한다. 결과 정리는 상태 확인 후 수행한다. S3 업로드 재시도는 기존 SkillVersion·key를 재사용하고 동일 객체가 이미 존재하면 크기·hash를 확인해 완료 보고를 재시도한다.

내부 오류 응답은 EC2와 동일한 error envelope를 사용한다. 인증 오류 401/403, 입력 오류 400/422, 상태 충돌 409, 자원 장애 503으로 구분한다. 통신 오류만으로 EC2 학습 실패를 즉시 확정하지 않고 조회·동일 실행 재요청 및 lease 정책에 따라 복구한다.

### 22.7 검증 기준 및 DB 연계

- [ ] 동일 trainingJobId·attempt의 동시 실행 요청에서 프로세스 하나만 생성
- [ ] 실행 접수 응답 유실 후 동일 실행 복구
- [ ] Spring Boot·FastAPI 재시작 후 실행 상태·sequence·결과 위치 복구
- [ ] EC2 lease 만료 및 FastAPI 단절 시 이전 실행 중단·늦은 결과 차단
- [ ] 학습 중 취소와 완료 경합에서 EC2 상태 전이 준수
- [ ] Python COMPLETED만으로 웹 학습 성공 알림을 발행하지 않음
- [ ] CKPT 최초 완료 통지 발신 시각과 업로드 재시도 시 파일 key 유지 검증

중앙 DB 신규 테이블은 이번 서비스 분리만으로 요구하지 않는다. runId는 기존 TrainingJob ID와 attempt로 도출한다. 로컬 journal은 업무 대기 큐가 아니라 실행 복구 기록이다.


### 22.8 RUN-05 학습 완료 통지 (FastAPI → GPU Spring Boot)

- POST /internal/training-runs/{runId}/completion · GPU Spring Boot 전용 내부 주소에서 제공한다. FastAPI 실행 API의 호스트와 구분한다.
- 인증: Python 서비스 전용 Bearer 자격증명. 호출자의 worker·StorageNode 범위를 확인한다. 콜백 주소는 서비스 설정으로 고정하며 사용자 입력으로 받지 않는다.
- 요청 필수: eventId(string), trainingJobId(string), attempt(integer), modelCompletedAt(RFC 3339 UTC), result(RUN-02의 checkpointStorageKey·sizeBytes·sha256·전체 CheckpointMetadata).
- FastAPI는 학습 및 최종 CKPT 생성·hash 계산 후 최초 호출 직전에 UTC 현재 시각을 modelCompletedAt으로 영속 기록한다. 이 값이 CKPT 파일명 기준이다. 재전송은 동일 eventId·시각·payload를 사용한다.
- GPU Spring Boot는 runId·현재 attempt·작업 소유권·공유 파일·hash·메타데이터를 대조하고 영속 inbox에 저장한 뒤 202: { data: { runId, receiptStatus: "ACCEPTED" } }를 반환한다. 응답은 학습 최종 성공을 뜻하지 않는다.
- 동일 이벤트·동일 본문 재전송은 200으로 기존 접수 결과를 반환한다. 동일 이벤트 또는 실행의 다른 결과는 409 COMPLETION_CONFLICT다. 이전 attempt·취소 확정·소유권 상실 결과는 409 RUN_STATE_CONFLICT로 차단한다.
- 통신 실패·응답 유실·5xx는 영속 발신 journal에서 재시도한다. 409 영구 충돌은 무한 재시도하지 않고 운영 오류로 기록한다. Spring Boot 재시작 후 inbox에서 미처리 업로드를 재개한다.
- RUN-02는 상태 복구용으로 유지한다. 결과를 조회하더라도 RUN-05 미접수 상태이면 콜백 재전송으로 같은 완료 시각·이벤트를 복구한다.
- 접수 후 GPU Spring Boot는 GPU-04 → S3 업로드 → GPU-05를 실행한다. EC2는 현재 lease·attempt 및 예약 결과를 다시 검증한다.

RUN-05 요청 예시:

```json
{
  "eventId": "completion_job_31_1",
  "trainingJobId": "job_31",
  "attempt": 1,
  "modelCompletedAt": "2026-09-07T06:30:00Z",
  "result": {
    "checkpointStorageKey": "training-results/job_31/1/model.ckpt",
    "sizeBytes": 7340032,
    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "metadata": {
      "architecture": "ACT",
      "framework": "PyTorch",
      "frameworkVersion": "2.x",
      "contractVersion": "umi-policy-v1",
      "robotProfileId": "so101-v1",
      "trainingProfileKey": "umi-act-default",
      "trainingProfileVersion": 1,
      "trainingConfigSha256": "9999999999999999999999999999999999999999999999999999999999999999",
      "actionSpace": "JOINT_DELTA",
      "nParams": 52341234,
      "controlRateHz": 30,
      "cameraNames": [
        "cam_front",
        "cam_wrist"
      ],
      "inputSchema": {
        "stateShape": [
          6
        ]
      },
      "outputSchema": {
        "actionShape": [
          6
        ]
      }
    }
  }
}
```




## 23. 웹 클라이언트 보완 API (1.1.0)

이 절은 1.1.0에서 추가된 계약이며 앞 절과 충돌하면 이 절을 적용한다.

### SKILL-09 학습 입력 조건 조회

GET /api/v1/skills/{skillId}/training-requirements · 사용자. 성공 200은 TrainingRequirements를 반환한다. 프리셋 선택 API가 아니며 고정 TrainingProfile 정책을 유지한다. TRAIN-01과 TRAIN-06에서 같은 조건을 다시 검증한다.

### 수집 진행 계약

Episode 업로드는 TrainingJob과 독립적으로 수행한다. 학습 생성 화면은 READY Episode 목록에서 입력을 선택하며, TrainingJob은 선택된 Episode snapshot이 확정된 뒤 QUEUED 상태로 진행한다. 기존 collection 집계 필드는 레거시 응답 호환용이며 신규 업로드 세션의 권위 데이터가 아니다.

### TASK-08 준비 상태 보완

응답에 valid, readiness, robotId, taskVersionId, deploymentId, observedAt, blockingReasons, warnings를 반환한다. readiness는 PREPARING, READY, BLOCKED, UNKNOWN이다. 실행 생성 시 상태를 다시 검증한다.

### EXEC-01 실행 생성

POST /api/v1/robots/{robotId}/executions · 사용자 · Idempotency-Key 필수. 요청은 taskVersionId와 deploymentId를 포함한다. 서버는 소유권, Robot busy 및 연결 상태, Deployment SUCCEEDED, 현재 TaskVersion, 모델 호환성과 gate를 다시 검증한다. 성공 202는 RobotExecution을 반환한다.

### EXEC-02 실행 목록

GET /api/v1/robots/{robotId}/executions · 사용자. taskVersionId, status, startedFrom, startedTo와 공통 페이지네이션을 지원한다.

### EXEC-03 실행 상세

GET /api/v1/robots/{robotId}/executions/{executionId} · 사용자. 성공 200은 RobotExecution을 반환한다.

### EXEC-04 실행 취소

POST /api/v1/robots/{robotId}/executions/{executionId}/cancel · 사용자 · Idempotency-Key 필수. REQUESTED는 즉시 CANCELED가 될 수 있다. STARTING 또는 RUNNING은 202와 CANCEL_REQUESTED를 반환하며 Jetson 확인 전 CANCELED로 표시하지 않는다. 종료 상태는 409 STATE_CONFLICT다.

### JETSON-06 실행 상태 보고

PATCH /api/v1/robots/me/executions/{executionId} · Jetson. 요청은 reportId, sequence, status, currentStep, totalSteps, progressPercent, error를 포함한다. reportId와 sequence로 중복 및 역순 보고를 차단한다. FAILED에는 error가 필수다.


RobotExecution 상태는 REQUESTED, STARTING, RUNNING, CANCEL_REQUESTED, CANCELED, SUCCEEDED, FAILED, STATUS_UNKNOWN이다. 현재 DB 스키마에는 영속 구조가 없으므로 구현 전에 robot_executions 테이블, 상태 CHECK, FK, 멱등성 및 로봇별 활성 실행 UNIQUE 제약을 Flyway migration으로 추가한다.

### SSE 실행 이벤트

| `execution.updated` | `{ executionId, robotId, taskVersionId, deploymentId, status, currentStep, totalSteps, progressPercent, error, occurredAt }` |

### 범위 제외

CaptureSession, 사용자 학습 preset, 튜토리얼 이수, 알림 센터, 관리자 권한 및 원격 모터 제어 API는 1.1.0 범위에 포함하지 않는다.
