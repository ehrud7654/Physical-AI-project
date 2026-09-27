# so101_orchestrator (S15P21A103-115 · 46)

BE(제어 평면) ↔ 젯슨(실행) 다리. BE 명령을 **SSE 로 받아** 모델 배포·작업 실행·취소를 처리하고,
진행 상태를 **HTTP PATCH/POST** 로 보고한다. 젯슨은 클라이언트(`httpx`) — FastAPI 아님.

## 구성

| 파일 | 역할 |
|---|---|
| `orchestrator_node.py` | rclpy 노드. SSE 스레드 → 명령 큐 → 워커 스레드 처리, heartbeat·토큰 갱신 타이머, 단일 인스턴스 락 |
| `be_client.py` | BE 호출(토큰 발급·401 재발급·SSE 수신·리포트 PATCH 재시도·모델 다운로드) |
| `bundle.py` | 배포 번들 tgz 안전 해제·필수 파일(`encoder.pt`·`denoiser.pt`·`metadata.json`) 확인 |
| `runtime_executor.py` | UMI 런타임 subprocess 기동·stdout JSON 리포트 파싱·정지(SIGINT→SIGKILL)·고아 방지·서보 진단 |
| `steps.py` | EXECUTE_TASK step 펼치기(REPEAT)·MOTOR 파라미터 → 관절 명령 |
| `policy_control.py` | 시뮬 트랙 정책 노드(`so101_policy`) lifecycle 전이 |
| `robot_model.py` | URDF(`/robot_description`) 관절 순서·한계 파싱 |

## 명령 처리

| BE 명령 | 동작 | 보고 |
|---|---|---|
| `APPLY_DEPLOYMENT` | pending manifest 조회 → 모델 tgz 다운로드(403 시 URL 재발급) → 크기·sha256 검증 → 해제 | 배포 `DOWNLOADING → VERIFYING → APPLYING → SUCCEEDED`(실패 시 `FAILED`, 이전 배포 유지) |
| `EXECUTE_TASK` | step 순차 실행. SKILL = 실행 엔진, MOTOR = 관절 명령 | 실행 `STARTING → RUNNING → SUCCEEDED/FAILED/CANCELED`, `currentStep`=step 번호, 진행률 % |
| `CANCEL_EXECUTION` | 실행 중이면 즉시 정지 플래그 + 엔진 정지. 이미 끝난 실행이면 무시 | `CANCELED` |

- 멱등: 명령은 `commandId` 중복 차단, 보고는 `reportId` + 단조증가 `sequence`
- 재시작 복구: 기동 시 `GET /robots/me/deployment/current` 로 배포 상태 복원. 실패해도 SKILL 실행 시 디스크 `models/<skillVersionId>/` 를 찾아 쓴다
- SSE: 끊기면(오류·정상 종료 모두) 3s 부터 지수 백오프 재연결

## 실행 엔진 (`executor`)

| 값 | SKILL step | 용도 |
|---|---|---|
| `ros2_policy` (기본) | `so101_policy` lifecycle configure→activate, timeoutSeconds 뒤 deactivate | 시뮬·개발 (ros2_control FakeBus) |
| `umi_runtime` | UMI 런타임 subprocess. timeoutSeconds 는 **첫 사이클 보고부터** 계산(로딩 제외) | 젯슨 실물 |

⚠️ `umi_runtime` 에서도 **MOTOR step 은 ros2_control 토픽으로만 발행**된다(실물 모드에서 미동작, 미결).

## 파라미터

| 이름 | 기본 | 설명 |
|---|---|---|
| `base_url` | `http://localhost:8080` | BE 주소 (운영 `https://j15a103.p.ssafy.io`) |
| `device_id` | `51000000-…-0002` | 기기 id |
| `device_secret` | `""` | 비우면 환경변수 `SO101_DEVICE_SECRET` 사용. **소스·git 에 넣지 말 것** |
| `token` | `""` | 고정 JWT(테스트용). 비우면 secret 으로 발급·자동 갱신 |
| `executor` | `ros2_policy` | 위 표 |
| `runtime_root` | `~/S15P21A103/ROS` | 런타임·venv·models 기준 경로 |
| `runtime_python` | `""` | 비우면 `<runtime_root>/.venv/bin/python` |
| `runtime_move` | `true` | `false` 면 추론만(prediction_only), 모터 명령 안 보냄 |
| `runtime_cycles` | `0` | 0 = 취소/timeout 까지 |
| `motor_port` | `/dev/ttyTHS1` | 서보 포트. 진단과 런타임 모두 이 포트 (가짜 버스 `/tmp/so101_fake_servo`) |
| `max_servo_temp_c` | `55` | 실행 전·후 온도 검사 임계(0=끔). 런타임 실행 중 게이트는 별도(60°C 3연속) |
| `models_dir` | `""` | 비우면 `<runtime_root>/models` |
| `heartbeat_sec` | `15` | BE offline 임계 20s 미만. 서버 `nextHeartbeatSeconds` 반영 |
| `motor_settle_sec` / `skill_default_sec` | `2` / `10` | timeoutSeconds 없을 때 MOTOR 대기 / 시뮬 SKILL 실행 시간 |

## 실행

```bash
source /opt/ros/humble/setup.bash && source ROS/ros2_ws/install/setup.bash
export SO101_DEVICE_SECRET='<BE 발급값>'

# 젯슨 실물
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io -p executor:=umi_runtime

# PC 시뮬 (별도 터미널에 bringup bus:=fake, so101_policy policy_node)
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io -p executor:=ros2_policy -p models_dir:=$HOME/so101_models
```

젯슨 상시 운영은 systemd 서비스(`ROS/deploy/`, 상세 `Docs/porting_jetson.md`).
**같은 device_id 로 두 대(PC·젯슨)를 동시에 띄우지 말 것** — BE 가 SSE 를 서로 끊는다(한 기계 안의 중복은 락이 막음).

## 테스트

```bash
python3 -m pytest src/so101_orchestrator/test/ -q
```

| 파일 | 내용 |
|---|---|
| `test_sse.py` | SSE 파싱·토큰·재시도·취소 처리·단일 인스턴스 락 |
| `test_runtime_executor.py` + `fake_runtime.py` | 런타임 I/O 계약(스텁): JSON 수집·MOVE·SIGINT·SIGKILL·실패 사유 |
| `test_steps.py`·`test_bundle.py`·`test_robot_model.py` | step 펼치기·번들 해제·URDF |
| `fake_servo_bus.py` | 가짜 ST3215 버스(pty) — 팔 없이 실물 경로 테스트. 사용법은 파일 상단 |
