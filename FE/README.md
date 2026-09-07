# S.S.A.F.Y 프론트엔드 기능·UX 명세서

## 1. 문서 목적

이 문서는 S.S.A.F.Y(Show Simply Arm Follows You) 웹 플랫폼의 프론트엔드 사용자 흐름, 화면 구조, 상태 처리, API 연동 규칙을 정의한다.

프론트 개발자는 이 문서를 기준으로 화면을 설계하고, 백엔드의 `API명세서v1.md`를 기준으로 요청·응답 타입을 구현한다. 두 문서가 충돌하면 임의로 구현하지 않고 FE·BE 담당자가 API 계약을 먼저 확정한다.

현재 백엔드 명세는 v1 제안 명세이므로 실제 엔드포인트 구현 여부, 개발 서버 주소, CORS, 토큰 정책, SSE 제공 여부를 연동 전에 확인해야 한다.

## 2. 제품 목표와 핵심 용어

S.S.A.F.Y는 사용자가 UMI로 동작을 보여주고, 해당 시연 데이터로 로봇 스킬을 학습한 뒤 여러 스킬을 Task로 구성하여 Robot에 배포·실행하는 플랫폼이다.

| 용어 | 의미 | 프론트 표시 예시 |
|---|---|---|
| UMI | 사용자의 시연 데이터를 수집하는 장치 | `UMI-01 · 온라인` |
| Jetson | Robot에서 모델을 내려받아 실행하는 장치 | `Jetson-07 · 온라인` |
| Robot | TaskVersion이 배포되고 작업을 실행하는 로봇 | `로봇팔 07` |
| Episode | UMI 시연 1회와 그 결과 데이터 묶음 | `빨간 블록 집기 · 시연 03` |
| Skill | 시스템 공통 동작 카탈로그 | `Pick & Place` |
| SkillVersion | 한 번의 학습으로 생성되고 검증된 불변 모델 버전 | `Pick & Place v3` |
| Task | 여러 스킬을 순서대로 조합한 논리적 작업 | `빨간 블록 정리` |
| TaskVersion | 실행 순서와 SkillVersion을 고정한 불변 작업 버전 | `빨간 블록 정리 v2` |
| Deployment | TaskVersion을 Robot에 적용하는 과정 | `배포 중 65%` |
| Execution | 배포된 Task를 실제로 실행한 기록 | `실행 중`, `완료`, `실패` |

## 3. UX 설계 원칙

1. 사용자는 시스템 용어를 몰라도 다음 행동을 알 수 있어야 한다.
2. 각 화면의 주요 행동 버튼은 하나만 가장 강하게 강조한다.
3. 연결·검증·진행 상태는 색상뿐 아니라 텍스트와 아이콘을 함께 사용한다.
4. 긴 기술 상태값은 사용자 친화적인 상태명으로 변환하고 상세 화면에서 원인을 제공한다.
5. 학습 취소, 배포 취소, 실행 중지는 확인 단계를 거친다.
6. 목록에서 상세 화면으로 이동해도 필터와 스크롤 위치를 가능한 한 유지한다.
7. 신규 시연과 기존 데이터 재사용 흐름을 구분하되 학습 현황은 하나로 합친다.
8. 모든 비동기 화면은 로딩, 빈 데이터, 오류, 재시도 상태를 제공한다.
9. 프론트 보호 라우트는 UX를 위한 1차 차단이며 실제 권한 검증은 백엔드가 수행한다.

## 4. 정보 구조

상단 내비게이션은 모든 내부 화면에서 같은 위치와 크기를 유지한다.

| 메뉴 | 주요 역할 |
|---|---|
| `DASHBOARD` | 연결 장치, 진행 중 학습, 배포, 실행 요약과 다음 행동 안내 |
| `DATA` | Episode 목록, 품질·라벨·보관 상태 확인 |
| `LEARNING` | 학습 생성, 진행 상태, 결과 SkillVersion 확인 |
| `LIBRARY` | SkillVersion과 Task/TaskVersion 조회 |
| `FLEET` | UMI·Jetson·Robot 조회, 장치 등록, 배포 상태 확인 |
| `WORKFLOW` | Task 구성, 검증, 버전 생성 |

`운영 관리`와 `장치 관리`는 별도 최상위 메뉴로 중복 제공하지 않는다. 장치와 Robot 관련 기능은 `FLEET`으로 통합한다.

## 5. 전체 사용자 흐름

### 5.1 대표 흐름

```text
로그인
→ 대시보드
→ 새 학습 만들기
→ 스킬 선택
→ 데이터 준비 방식 선택
   ├─ 새 시연 수집
   └─ 기존 Episode 재사용
→ 학습 입력 검증
→ 학습 시작
→ 학습 진행 확인
→ 학습 결과(SkillVersion) 및 게이트 확인
→ Task 구성
→ TaskVersion 검증·생성
→ Robot 선택 및 배포
→ 배포 진행·결과 확인
→ 작업 실행·중지
```

### 5.2 신규 시연 데이터 수집 흐름

```text
새 학습 만들기
→ 시스템 Skill 선택
→ `새로 시연하기` 선택
→ 온라인 UMI 선택
→ 예상 시연 횟수 확인
→ 학습 작업 생성
→ UMI에서 시연 수행
→ Episode 업로드·검증 상태 확인
→ 필요한 Episode가 READY가 되면 입력 완료
→ 서버 입력 검증
→ 학습 대기·진행
→ SkillVersion 생성 및 검증
```

UX 규칙:

- 온라인 UMI가 없으면 다음 단계로 이동시키지 않고 `장치 연결 방법`과 `FLEET으로 이동`을 제공한다.
- 웹은 실시간 모터 제어 화면을 제공하지 않는다. 시연은 UMI 측에서 수행하고 웹은 안내와 상태 확인을 담당한다.
- 수집 중에는 예상 수량, 수집 완료 수량, 검증 중 수량, 사용 가능 수량을 구분한다.
- 품질 검증에 실패한 Episode는 이유와 재시연 동작을 안내한다.
- 입력 완료 후 Episode 목록은 해당 TrainingJob의 불변 입력으로 취급한다.

### 5.3 기존 Episode 재사용 흐름

```text
새 학습 만들기
→ 시스템 Skill 선택
→ `기존 데이터 사용` 선택
→ 학습에 사용 가능한 Episode 목록 조회
→ Episode 다중 선택
→ 선택 데이터 요약·품질 확인
→ 학습 작업 생성
→ 서버 입력 검증
→ 학습 대기·진행
→ SkillVersion 생성 및 검증
```

선택 가능한 Episode 조건:

- `status=READY`
- `qualityStatus`가 학습 허용 상태
- `labelStatus=LABELED`
- `success=true`
- archive가 사용 가능하고 삭제가 시작되지 않음
- 보관 기한이 만료되지 않음
- 선택한 Skill의 `skillKey`와 일치함

사용할 수 없는 Episode는 숨기기보다 비활성화하고 이유를 표시한다. 예: `검증 중`, `라벨 필요`, `보관 만료`, `스킬 불일치`.

## 6. 화면별 기능 명세

### 6.1 랜딩과 로그인

- 로그인 전 내부 메뉴를 선택하면 내부 화면으로 바로 이동하지 않고 로그인 모달을 표시한다.
- 로그인 성공 후 원래 요청한 화면 또는 대시보드로 이동한다.
- 토큰이 유효하면 새로고침 후 내 정보를 조회하여 인증 상태를 복원한다.
- 로그아웃하면 인증 정보와 사용자 캐시를 제거하고 랜딩으로 이동한다.

### 6.2 대시보드

반드시 보여줄 내용:

- UMI·Jetson·Robot 온라인/오프라인 요약
- 진행 중이거나 확인이 필요한 학습
- 진행 중이거나 실패한 배포
- 최근 작업 실행 결과
- 사용자 상태에 맞는 주요 행동 버튼

주요 행동은 상황에 따라 바꾼다.

- 등록된 UMI 없음: `UMI 등록`
- 온라인 UMI 없음: `연결 상태 확인`
- 데이터 없음: `첫 시연 시작`
- 진행 중 학습 있음: `학습 진행 보기`
- 일반 상태: `새 학습 만들기`

### 6.3 FLEET

- `UMI`, `Jetson`, `Robot`을 탭 또는 명확한 필터로 구분한다.
- Device와 Robot을 같은 리소스로 취급하지 않는다.
- 장치 등록에서는 장치 유형, 이름 등 API가 허용하는 값만 입력한다.
- 연결 상태, 마지막 연결 시각, 현재 배포 상태를 표시한다.
- Robot 상세에서는 현재 활성 Task/Skill, 최근 Deployment, 최근 Execution을 표시한다.

### 6.4 DATA

- Episode 목록을 Skill, UMI, 상태, 품질, 라벨, 보관 기한으로 필터링한다.
- 학습에 사용할 수 있는 항목을 명확히 표시한다.
- Episode ZIP 또는 내부 원본의 브라우저 재생·다운로드는 API v1 범위 밖이다.
- 영상 미리보기 UI가 필요하면 실제 기능이 아닌 발표용 Mock임을 코드와 문서에 명시한다.

### 6.5 새 학습 만들기

단계형 화면을 사용한다.

1. Skill 선택
2. `새로 시연하기` 또는 `기존 데이터 사용` 선택
3. UMI 또는 Episode 선택
4. 입력 데이터 검토
5. 최종 확인 및 생성

사용자는 epochs, batch size, learning rate 같은 하이퍼파라미터를 입력하지 않는다. 백엔드가 활성 TrainingProfile을 선택하고 TrainingJob에 고정한다.

Skill은 사용자가 임의로 생성하지 않는다. 백엔드가 제공하는 시스템 공통 카탈로그에서 선택한다. 초기 명세의 SkillKey는 다음과 같다.

- `pick_place`
- `sort_two`
- `align_fixture`
- `present_inspect`
- `line_up`

### 6.6 학습 현황과 상세

목록에서 사용자에게 표시할 통합 상태:

| 사용자 상태 | API 상태 예시 |
|---|---|
| 데이터 수집 중 | `CREATED`, `COLLECTING` |
| 입력 확인 중 | `INPUT_VALIDATING` |
| 학습 대기 | `INPUT_READY`, `QUEUED`, `PREPARING` |
| 학습 중 | `RUNNING`, `MODEL_UPLOADING`, `MODEL_VALIDATING` |
| 취소 중 | `CANCEL_REQUESTED` |
| 취소됨 | `CANCELED` |
| 완료 | `SUCCEEDED` |
| 실패 | `FAILED` |

상세 화면에는 원본 API 상태, 진행 단계, 진행률, 시작·완료 시각, 오류 원인, 입력 Episode를 제공한다.

- 진행 중 작업만 취소할 수 있다.
- 실패한 작업의 `재시도`는 기존 TrainingJob을 되돌리지 않고 동일 조건으로 새 TrainingJob을 생성하는 흐름으로 처리한다.
- 학습 성공만으로 배포 가능하다고 표시하지 않는다. SkillVersion이 `READY`이고 gate가 통과되어야 한다.

### 6.7 LIBRARY

- `SKILL LIBRARY`: Skill과 SkillVersion 목록, 버전, 검증 상태, 배포 가능 여부를 제공한다.
- `TASK LIBRARY`: Task와 TaskVersion 목록, 포함 SkillVersion, 검증 결과를 제공한다.
- SkillVersion과 TaskVersion은 불변 버전으로 취급하며 기존 버전을 덮어쓰지 않는다.

### 6.8 WORKFLOW

- 사용 가능한 SkillVersion을 순서대로 배치하여 Task를 구성한다.
- MVP에서는 순차적이며 순환하지 않는 작업만 지원한다.
- 반복, 조건 분기, 병합, 임의 코드 실행 블록은 API v1 범위 밖이므로 실제 기능으로 노출하지 않는다.
- 저장 전 클라이언트 기본 검증을 하고, 최종 판정은 TaskVersion 검증 API 결과를 사용한다.
- Task 수정 시 기존 TaskVersion을 변경하지 않고 새 TaskVersion을 만든다.

### 6.9 배포와 실행

```text
TaskVersion 선택
→ 호환 가능한 Robot 선택
→ 배포 요청
→ 다운로드·검증·적용 진행 확인
→ 배포 성공
→ 작업 실행
```

배포 상태는 `PENDING → DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED` 흐름으로 표시하며 실패 상태와 원인을 별도로 제공한다.

- 단일 모델을 직접 Robot에 적용하는 UI 대신 TaskVersion 배포를 기준으로 한다.
- 배포 중 화면을 닫아도 대시보드와 Robot 상세에서 계속 상태를 확인할 수 있어야 한다.
- 실행 시작 전 Robot 온라인 상태와 활성 TaskVersion을 확인한다.
- 실행 상태는 `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `STOP_REQUESTED`, `STOPPED`를 사용자용 문구로 변환한다.
- 실행 중지는 확인 모달을 거치고 요청 접수와 실제 정지 완료를 구분한다.

## 7. API 연동 규칙

### 7.1 공통 규칙

- EC2 API 기준 경로: `/api/v1`
- 사용자 API 인증: `Authorization: Bearer {accessToken}`
- 성공 응답: `{ "data": ... }`
- 목록 응답: `{ "data": [], "page": { "nextCursor": ..., "hasNext": ... } }`
- 목록은 `cursor`, `limit` 기반이며 기본 정렬은 최신순이다.
- 모든 응답의 `X-Request-Id`를 오류 추적에 활용한다.

공통 오류 형태:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "설명",
    "details": [{ "field": "fieldName", "reason": "원인" }],
    "requestId": "request-id"
  }
}
```

프론트 처리:

- `400`, `422`: 필드 오류 또는 요청 오류 표시
- `401`: 토큰 갱신 1회 시도 후 실패하면 로그아웃
- `403`: 권한 부족 화면 또는 안내
- `404`: 삭제되었거나 접근할 수 없는 리소스 안내
- `409`: 중복 요청·상태 충돌·SSE 커서 만료에 맞는 복구 동작
- `413`: 업로드 크기 제한 안내
- `429`: 잠시 후 재시도 안내
- `500`, `503`: 공통 오류와 재시도 제공

### 7.2 멱등성

명세에서 요구하는 생성·완료 요청에는 `Idempotency-Key`를 보낸다. 사용자가 버튼을 연속으로 눌러도 동일 작업이 중복 생성되지 않도록 제출 중 버튼을 비활성화한다.

적용 대상에는 TrainingJob 생성, 입력 완료, Episode 조기 삭제, TaskVersion 생성, Deployment 생성 등이 포함된다.

### 7.3 실시간 상태

학습, Episode, 장치 연결, 배포, 실행 상태는 SSE `/events`를 사용하고 REST 조회를 복구 수단으로 사용한다.

- Authorization 헤더가 필요하므로 브라우저 기본 `EventSource` 대신 `fetch` 스트림을 사용한다.
- 토큰 갱신 후 재연결한다.
- 마지막 이벤트 ID를 저장하고 재연결 시 `Last-Event-ID`를 보낸다.
- 15초 heartbeat를 기준으로 연결 끊김을 감지한다.
- `EVENT_CURSOR_EXPIRED`가 발생하면 관련 REST API를 다시 조회해 화면 상태를 동기화한다.

## 8. 화면과 API 매핑

| 화면 | API 영역 |
|---|---|
| 로그인·사용자 | `AUTH-01~04`, `USER-01~02` |
| FLEET 장치 | `DEVICE-01~06` |
| Robot 목록·상세 | `ROBOT-01~02` |
| DATA | `EPISODE-01~07` |
| Skill Library | `SKILL-01~08` |
| 새 학습·학습 현황 | `TRAIN-01~09` |
| Workflow·Task Library | `TASK-01~08` |
| Robot 배포 | `DEPLOY-01~05` |
| 실시간 상태 | `EVENT-01` |

GPU, UMI, Jetson 내부 API는 웹 클라이언트가 직접 호출하지 않는다.

## 9. 프론트 코드 구조

화면 코드가 Mock 또는 실제 API 구현에 직접 의존하지 않게 분리한다.

```text
FE/
├─ app/                 # 라우트, 화면, 공통 레이아웃
├─ components/          # 공통 UI와 도메인 컴포넌트
├─ hooks/               # 화면 상태 및 서버 상태 접근
├─ services/            # HTTP, 인증, SSE, 도메인 API 함수
├─ mocks/               # Mock 데이터와 Mock 서비스
├─ types/
│  ├─ api/              # 백엔드 DTO와 공통 응답 타입
│  └─ domain/           # 화면에서 사용하는 도메인 모델
└─ utils/               # 상태 변환, 포맷, 검증
```

권장 규칙:

- 컴포넌트에서 `fetch`를 직접 호출하지 않는다.
- API 영문 enum을 화면의 한글 상태명으로 변환하는 mapper를 둔다.
- API DTO와 화면 모델을 분리한다.
- Mock과 실제 API는 동일한 service interface를 구현한다.
- API base URL과 Mock 사용 여부는 환경변수로 전환한다.
- 토큰, secret, signed URL을 콘솔에 기록하지 않는다.

## 10. 공통 화면 상태

모든 목록·상세 화면은 다음 상태를 구현한다.

| 상태 | UI |
|---|---|
| 최초 로딩 | 레이아웃을 유지하는 Skeleton |
| 추가 로딩 | 기존 목록 하단 로딩 표시 |
| 빈 데이터 | 원인 설명과 다음 행동 버튼 |
| 오류 | 사용자용 메시지, 재시도, 필요 시 requestId |
| 권한 없음 | 접근 불가 안내와 돌아가기 |
| 오프라인 | 마지막 데이터임을 표시하고 재연결 안내 |
| 성공 | 중복 이동을 막는 토스트 또는 완료 화면 |

## 11. API v1 기준 제외·보류 기능

- Episode ZIP과 내부 원본의 브라우저 재생·다운로드
- 사용자 입력 하이퍼파라미터
- 반복·조건 분기를 포함하는 범용 Workflow
- 웹을 통한 실시간 모터 제어
- 사용자가 임의 이름으로 만드는 Skill
- 단일 CKPT를 Robot에 바로 적용하는 배포

발표용 Mock으로 유지할 경우 실제 기능과 혼동되지 않도록 코드와 화면에 표시한다.

## 12. 완료 기준

- 로그인하지 않은 사용자는 내부 화면에 접근할 수 없다.
- 신규 수집과 기존 Episode 재사용 방식으로 각각 TrainingJob을 생성할 수 있다.
- 사용할 수 없는 Episode는 선택되지 않으며 이유가 표시된다.
- 학습 상태가 API enum에서 사용자 상태로 정확히 변환된다.
- 완료된 SkillVersion의 `READY` 및 gate 통과 여부를 구분한다.
- TaskVersion 생성 전 검증 결과를 표시한다.
- 배포와 실행의 요청 접수, 진행, 성공, 실패, 취소·정지를 구분한다.
- 주요 목록은 cursor pagination을 처리한다.
- `401`, `403`, `409`, `422`, `500`, `503`을 구분해 처리한다.
- SSE 단절 후 재연결하거나 REST 조회로 최신 상태를 복구한다.
- Mock/실제 API 전환 시 화면 컴포넌트를 수정하지 않는다.

## 13. 연동 전 확인 사항

- 실제 개발·운영 API Base URL과 CORS 설정
- 구현 완료된 사용자용 API 목록과 Swagger/OpenAPI 주소
- access/refresh token 저장·만료·회전 정책
- 신규 수집 시 웹, UMI, APP 각각의 책임과 시작 트리거
- 초기 시스템 SkillKey 확정 목록
- Episode 품질·라벨별 학습 허용 조건
- SSE 구현 여부와 이벤트별 payload
- TaskStep에서 지원하는 parameters와 timeout 의미
- Robot 생성 시점과 UMI·Jetson 등록 관계
- 배포·실행 취소 및 통신 단절 시 안전 정책

## 14. 로컬 실행

요구 환경은 Node.js 22.13 이상이다.

```bash
cd FE
npm install
npm run dev
```

검증 명령:

```bash
npm run lint
npm run build
```

현재 프로젝트는 React 19, Next.js 호환 구조, Vinext/Vite, TypeScript를 사용한다.
