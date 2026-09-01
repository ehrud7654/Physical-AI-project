# 배포 제어 인터페이스 초안 — BE ↔ 젯슨 오케스트레이터

> 상태: **논의용 초안**. BE 파트와 합의 후 확정. 열린 질문은 §7 참고.
> 범위: **제어 평면(BE↔젯슨)** 만. 젯슨 내부 실시간(ROS)은 `so101_msgs`(별개, 이슈 108).
> 관련 이슈: 51([ROS] 상태·결과 Backend 전송), 24([BE] 관제 수집 API), 42(Jetson 용량)

---

## 1. 두 평면 (헷갈리기 쉬움)

```
━━ 제어 평면 (이 문서, BE↔젯슨, HTTP, 저빈도) ━━━━━━━━━━━━
   BE ──명령(시작/정지/ckpt)──> 젯슨
   젯슨 ──상태/결과──> BE

━━ 데이터 평면 (젯슨 내부, ROS, 30Hz 실시간) ━━━━━━━━━━━━
   정책노드(ckpt) ⇄ 드라이버 ⇄ 모터·카메라   ← so101_msgs, BE 안 낌
```

**BE는 관절 명령을 보내지 않는다.** 고수준(스킬 시작/정지, ckpt 로드)만. 실제 `/joint_command`는 젯슨 내부 정책이 30Hz로 생성.

---

## 2. 연결 방식

- 젯슨은 캠퍼스 NAT 뒤 → **BE(EC2 공인)로 젯슨이 아웃바운드 연결.** BE→젯슨 직접 호출 불가.
- **명령(BE→젯슨): SSE** (Server-Sent Events, 단방향 push, 자동 재연결)
- **상태(젯슨→BE): HTTP POST**
- 젯슨은 **서버 아님** — SSE 클라이언트(`httpx`) + POST만. FastAPI 등 웹서버는 **BE 쪽**.

```
BE (SSE 서버 + POST 수신)  ◄── SSE(명령) ── 젯슨 오케스트레이터 (rclpy + httpx)
                           ◄── POST(상태) ──
                                              └ ROS lifecycle/service → 정책 노드 start/stop
```

---

## 3. 명령 채널 (SSE, BE → 젯슨)

**엔드포인트(제안):** `GET {BE}/api/deploy/stream?device_id=<젯슨ID>` → `text/event-stream`

**이벤트 종류(제안):**

| event | 의미 | data 필드 |
|---|---|---|
| `load_ckpt` | ckpt 다운로드·로드 | `ckpt_id`, `version`, `url`, `sha256`, `vlm_version?` |
| `start_skill` | 스킬 실행 시작 | `skill_id`, `run_id`, `params?` |
| `stop` | 정상 정지 | `run_id` |
| `estop` | 비상 정지 | — |
| `ping` | 연결 유지 | — |

**예시:**
```
event: load_ckpt
data: {"ckpt_id":"pick_v3","version":"2026-09-01T10:00","url":"https://.../pick_v3.ckpt","sha256":"ab12..."}

event: start_skill
data: {"skill_id":"pick_place","run_id":"run_8842","params":{"target":"red_block"}}
```

---

## 4. 상태 채널 (HTTP POST, 젯슨 → BE)

**엔드포인트(제안):** `POST {BE}/api/deploy/status`

**공통 필드:** `device_id`, `ts`(ISO8601), `type`, `run_id?`

| type | 의미 | 추가 필드 |
|---|---|---|
| `ckpt_progress` | ckpt 다운로드 진행 | `ckpt_id`, `pct` |
| `ckpt_ready` | 로드 완료 | `ckpt_id`, `version` |
| `skill_running` | 실행 중 | `skill_id` |
| `skill_done` | 완료 | `success`(bool), `duration_s` |
| `skill_failed` | 실패 | `reason` |
| `estopped` | 비상정지됨 | `reason` |
| `heartbeat` | 생존/자원 | `cpu`, `mem`, `gpu_mem`(8GB 감시, 이슈 42) |

**예시:**
```json
{ "device_id":"jetson-a103", "ts":"2026-09-01T10:05:12+09:00",
  "type":"skill_done", "run_id":"run_8842", "success":true, "duration_s":18.4 }
```

---

## 5. ckpt 배포 흐름

```
BE: load_ckpt(url, sha256) ──SSE──> 젯슨
젯슨: url 다운로드 → sha256 검증 → 정책 노드에 로드
젯슨: ckpt_progress(pct) ... → ckpt_ready ──POST──> BE
```

- ckpt는 **BE가 준 URL에서 젯슨이 pull** (BE가 젯슨으로 push 안 함).
- 무결성: **sha256 검증 필수** (부분 다운로드/손상 방지).
- 버전 기록 (롤백 대비).

---

## 6. 젯슨 내부 동작 (참고, 데이터 평면)

오케스트레이터가 SSE 명령을 ROS로 번역:

| SSE 명령 | 젯슨 내부 ROS 동작 |
|---|---|
| `load_ckpt` | 정책 노드에 ckpt 로드 (lifecycle configure) |
| `start_skill` | 정책 노드 activate → 30Hz 루프 시작 |
| `stop` | 정책 노드 deactivate → 모터 홀드 |
| `estop` | 안전 노드에 `/estop` 발행 → 즉시 정지 |

30Hz 관절/카메라 루프는 **젯슨 안에서만** 돎 (BE로 안 나감). BE엔 §4 상태만.

---

## 7. 논의 필요 (BE 파트와 합의)

1. **인증** — SSE/POST에 토큰? (device_id 위조 방지)
2. **엔드포인트 경로/포맷** — §3·§4 제안 확정
3. **ckpt 저장 위치** — BE가 주는 url이 S3? EC2? 프리사인드?
4. **skill_id / run_id 체계** — 누가 발급? (BE가 run_id 주고 젯슨이 에코?)
5. **오프라인/재연결** — SSE 끊기면 진행 중 스킬 계속? heartbeat 주기?
6. **다중 젯슨** — device_id 체계 (지금은 1대지만)
7. **VLM 트리거** — start_skill의 params로 자연어 지시? VLM grounding은 젯슨 내부?
8. **estop 경로** — SSE로만? 별도 빠른 경로 필요?

---

## 8. 기술 스택 (젯슨 오케스트레이터)

- **rclpy 노드 + `httpx`** (SSE 스트리밍 + POST 한 라이브러리로)
- SSE 클라이언트는 별도 스레드, rclpy는 메인 spin
- 정책 제어는 **ROS 2 lifecycle**(configure/activate/deactivate) 또는 서비스
- **FastAPI 불필요** (젯슨은 클라이언트)
