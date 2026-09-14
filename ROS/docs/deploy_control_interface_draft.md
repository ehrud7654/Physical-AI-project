# 배포 제어 인터페이스 — BE ↔ 젯슨 오케스트레이터

> 상태: **BE 계약 v1.3.0 + 실제 Jetson 컨트롤러 구현 기준**으로 확정. (이전 SSE 초안의 제안 엔드포인트는 폐기)
> 범위: **제어 평면(BE↔젯슨)** 만. 젯슨 내부 30Hz 실시간은 ros2_control(별개).
> 관련 이슈: 116([ROS] 오케스트레이터 BE 연동), 126([BE] Jetson 배포 구현)

---

## 1. 두 평면

```
━━ 제어 평면 (이 문서, BE↔젯슨, HTTP/SSE, 저빈도) ━━━━━━━━━━━
   BE ──명령(APPLY_DEPLOYMENT/EXECUTE_TASK/CANCEL)── SSE ──> 젯슨
   젯슨 ──heartbeat·배포/실행 리포트── HTTP ──> BE

━━ 데이터 평면 (젯슨 내부, ros2_control, 30Hz) ━━━━━━━━━━━━━
   정책노드(ckpt→IK) → forward_position_controller → SO101System → 모터
```

**BE는 관절 명령을 보내지 않는다.** 고수준(배포/실행/취소)만. 관절각은 젯슨 내부 정책이 30Hz 생성.

---

## 2. 연결 방식

- 젯슨은 캠퍼스 NAT 뒤 → **BE(EC2 공인)로 젯슨이 아웃바운드.** BE→젯슨 직접 호출 불가.
- **명령(BE→젯슨): SSE** `command-stream` (BE가 SseEmitter로 push, 자동 재연결)
- **리포트(젯슨→BE): HTTP POST(heartbeat) · PATCH(배포·실행 상태)**
- 인증: **JWT Bearer** (DEVICE-05 device access token). 모든 `/robots/me/**`.
- 응답은 `ApiResponse{data}` 래퍼.

---

## 3. 명령 채널 (SSE, BE → 젯슨)

`GET /api/v1/robots/me/command-stream` · `text/event-stream` · JWT

- 이벤트: `event: robot.command`, `id: <commandId>`, `data: <JetsonCommand json>`
- keepalive 주석: `: connected`(연결 시), `: ping`(주기). 무시.
- `id`(commandId)로 중복 제거. 재연결 시 `Last-Event-ID`.

**JetsonCommand:**
```json
{ "commandId": "...", "sequence": 0, "type": "EXECUTE_TASK",
  "robotId": "...", "executionId": "...", "taskVersionId": "...",
  "deploymentId": "...", "steps": [ ... ], "issuedAt": "...",
  "contractVersion": "robot-command-v1" }
```

| type | 의미 | 주요 필드 |
|---|---|---|
| `APPLY_DEPLOYMENT` | 배포 적용 | `deploymentId` |
| `EXECUTE_TASK` | 작업 실행 | `executionId`, `taskVersionId`, `steps[]` |
| `CANCEL_EXECUTION` | 실행 취소 | `executionId` |

`steps[]`: `{stepId, parentStepId, order, type, skillVersionId, timeoutSeconds, repeatCount, parameters}` (오케스트레이션).

---

## 4. 리포트 채널 (젯슨 → BE)

**heartbeat** `POST /api/v1/devices/{deviceId}/heartbeat`
- 요청 `{firmwareVersion, runtimeVersion, contractVersion}` → 응답 `{serverTime, nextHeartbeatSeconds}`
- 젯슨 20초 미수신 시 OFFLINE 판정 → heartbeat 주기 <20s(기본 15s)

**배포 상태** `PATCH /api/v1/robots/me/deployments/{deploymentId}` (JETSON-03)
- `{reportId, sequence, status, progressPercent, items?, error?}`
- status: `PENDING→DOWNLOADING→VERIFYING→APPLYING→SUCCEEDED` / `→FAILED` / `→CANCELED`
- `reportId`+`sequence`(단조증가)로 중복·역순 차단. FAILED면 `error{code,message}` 필수.

**실행 상태** `PATCH /api/v1/robots/me/executions/{executionId}` (JETSON-06)
- `{reportId, sequence, status, currentStep?, progressPercent, error?}`
- status: `STARTING→RUNNING→SUCCEEDED` / `→FAILED` / `→CANCELED`. FAILED면 `error{code,message,recoveryAction?}`.

---

## 5. 배포 흐름

```
APPLY_DEPLOYMENT(SSE)
  → GET /robots/me/deployments/pending → Manifest(models[], steps[])
  → 각 model.downloadUrl(S3 presigned) pull + sha256 검증
  → PATCH DOWNLOADING → VERIFYING → (정책노드 로드) → APPLYING → SUCCEEDED
```

- ckpt는 **manifest의 S3 presigned URL에서 젯슨이 pull** (Authorization 안 붙임 — presigned 자기인증).
- URL 만료 시 `POST /robots/me/deployments/{id}/manifest`로 재발급.
- **Manifest.Model 메타**: `architecture, framework, contractVersion, actionSpace, controlRateHz(30), cameraNames, inputSchema, outputSchema, sha256` — 정책 노드가 로드·설정에 사용.

---

## 6. 젯슨 내부 동작 (데이터 평면 연결)

오케스트레이터가 SSE 명령을 ROS로 번역:

| 명령 | 젯슨 내부 |
|---|---|
| `APPLY_DEPLOYMENT` | 모델 다운로드·검증 → 정책 노드 lifecycle configure(ckpt 로드) |
| `EXECUTE_TASK` | 정책 노드 activate → 30Hz 추론 루프 |
| `CANCEL_EXECUTION` | 정책 노드 deactivate |
| (안전) | safety 노드 → `/estop` → estop_bridge → 하드웨어 deactivate(토크off) |

---

## 7. 남은 확정 (AI/BE와)

1. **actionSpace** — manifest `actionSpace`(예 JOINT_DELTA)가 정책 152 출력(관절 rad+gripper m)과 일치?
2. **CKPT 내부 포맷** — 다운로드한 모델을 정책 노드가 어떻게 로드? (AI+BE)
3. **정책 노드 상태 인터페이스(46)** — 실행 완료(SUCCEEDED/FAILED)·진행률을 정책 노드가 오케스트레이터에 알리는 토픽/서비스. 현재 orchestrator는 STARTING/RUNNING까지만, 완료 리포트는 이거 확정 후.
4. **ckpt 경로 전달(46)** — configure에 다운로드 경로/메타 전달 배관 (현재 lifecycle 전이만).
5. **device token 발급 흐름** — DEVICE-05(앱 163에서 배관), 젯슨이 JWT 획득·갱신.

---

## 8. 기술 스택 (젯슨 오케스트레이터)

- **rclpy 노드 + `httpx`** — SSE 스트리밍 + POST/PATCH
- SSE는 별도 스레드 → 큐 → **워커 스레드**에서 처리(다운로드 블로킹이 executor 안 막게)
- 정책 제어 = ROS 2 lifecycle(configure/activate/deactivate)
- **FastAPI 불필요** (젯슨은 클라이언트)
- 구현: `so101_orchestrator` (be_client.py 전송 + orchestrator_node.py 배관)
