# 프론트엔드 변경 관련 백엔드 API 요청사항

학습 목록과 작업 목록의 선택 삭제 UI에서 필요한 API 계약입니다. 현재 프론트엔드는 아래 개별 DELETE API를 항목별로 호출하며, 요청이 하나라도 실패하면 목록을 임의로 제거하지 않고 오류 안내를 표시합니다.

## 공통 규칙

- 인증: 기존 API와 동일하게 `Authorization: Bearer {accessToken}` 사용
- 성공 응답: 기존 공통 형식인 `{ "data": ... }` 유지
- 오류 응답: 기존 형식인 `{ "code": "ERROR_CODE", "message": "사용자 안내 문구" }` 유지
- 본인 소유 리소스만 삭제 가능
- 존재하지 않거나 접근할 수 없는 ID는 `404 RESOURCE_NOT_FOUND`로 통일
- 중복 DELETE의 멱등성 정책을 확정해야 함
  - 권장: 이미 삭제된 리소스는 `204`로 처리해 재시도에 안전하게 구성

## 1. 학습 작업 삭제

```http
DELETE /api/v1/training-jobs/{trainingJobId}
Authorization: Bearer {accessToken}
```

성공 응답 권장:

```http
204 No Content
```

프론트의 공통 응답 파서와 형식을 맞춰야 한다면 다음 형식도 가능합니다.

```json
{
  "data": {
    "trainingJobId": "uuid",
    "deleted": true
  }
}
```

삭제 가능 상태 제안:

| 상태 | 삭제 | 처리 제안 |
|---|---:|---|
| `CREATED`, `COLLECTING`, `INPUT_VALIDATING`, `INPUT_READY`, `QUEUED`, `PREPARING`, `RUNNING`, `MODEL_UPLOADING`, `MODEL_VALIDATING` | 불가 | 먼저 취소 API로 종료한 뒤 삭제 |
| `CANCEL_REQUESTED` | 불가 | 취소 완료까지 대기 |
| `CANCELED`, `SUCCEEDED`, `FAILED` | 가능 | 관련 입력·결과 파일 정책에 따라 soft delete 권장 |

상태 충돌 응답 예시:

```http
409 Conflict
```

```json
{
  "code": "TRAINING_DELETE_NOT_ALLOWED",
  "message": "진행 중인 학습은 삭제할 수 없습니다. 먼저 학습을 취소해 주세요."
}
```

권장 삭제 방식:

- 사용자 목록에서는 즉시 제외하되 감사·운영 추적을 위해 DB는 soft delete
- 모델 체크포인트와 Episode 같은 대용량 파일은 별도 비동기 정리
- 정리 중에는 내부적으로 삭제 상태와 실패 사유를 기록
- 학습 결과 SkillVersion이 배포 또는 작업에서 사용 중이면 삭제를 거부하거나 참조 결과를 유지

## 2. 작업 삭제

```http
DELETE /api/v1/tasks/{taskId}
Authorization: Bearer {accessToken}
```

성공 응답 권장:

```http
204 No Content
```

또는 공통 응답 형식:

```json
{
  "data": {
    "taskId": "uuid",
    "deleted": true
  }
}
```

삭제 정책 제안:

- 아직 배포·실행되지 않은 작업: 삭제 가능
- 현재 실행 중인 작업: `409 TASK_RUNNING`으로 삭제 금지
- 활성 배포가 있는 작업: `409 TASK_DEPLOYED`로 삭제 금지하고 배포 해제 안내
- 종료된 실행 이력만 있는 작업: 작업은 soft delete하고 실행 이력은 감사 목적으로 유지
- 작업 버전과 실행 이력에서 참조 무결성이 깨지지 않도록 hard delete는 피하는 것을 권장

오류 응답 예시:

```json
{
  "code": "TASK_DEPLOYED",
  "message": "배포 중인 작업은 삭제할 수 없습니다. 배포를 해제한 뒤 다시 시도해 주세요."
}
```

## 3. 여러 항목 삭제

현재 FE 구현은 선택한 ID마다 개별 DELETE를 병렬 호출합니다. `Promise.all`을 사용하므로 일부 요청만 성공해도 하나가 실패하면 전체 동작을 실패로 안내하고 목록을 다시 불러오지 않습니다. 이 경우 서버와 화면 상태가 잠시 달라질 수 있습니다.

권장안은 전용 일괄 삭제 API를 추가하는 것입니다.

```http
POST /api/v1/training-jobs/bulk-delete
POST /api/v1/tasks/bulk-delete
Content-Type: application/json
```

```json
{
  "ids": ["uuid-1", "uuid-2"]
}
```

부분 성공을 허용하는 응답 예시:

```json
{
  "data": {
    "deletedIds": ["uuid-1"],
    "failures": [
      {
        "id": "uuid-2",
        "code": "TASK_DEPLOYED",
        "message": "배포 중인 작업입니다."
      }
    ]
  }
}
```

백엔드 정책이 확정되면 FE는 다음 중 하나로 맞춰야 합니다.

1. 원자적 처리: 하나라도 삭제할 수 없으면 전체를 삭제하지 않고 `409` 반환
2. 부분 성공: 성공·실패 ID를 함께 반환하고 FE에서 목록 재조회 후 실패 항목만 안내

운영 편의성과 여러 상태의 항목을 함께 선택하는 UI를 고려하면 부분 성공 응답을 권장합니다.

## 4. 현재 확인된 서버 상태

2026-09-10 로컬 서버의 `OPTIONS` 응답 기준:

- `/training-jobs/{id}`: `GET, HEAD, OPTIONS`
- `/tasks/{id}`: `GET, HEAD, PATCH, OPTIONS`

따라서 두 DELETE API는 아직 구현되지 않았습니다. 구현 전까지 프론트는 삭제 성공으로 처리하지 않습니다.
