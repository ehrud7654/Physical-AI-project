# so101_orchestrator

젯슨에서 BE와 통신하는 노드입니다. BE에서 SSE로 명령을 받아 모델 배포, 작업 실행, 취소를 처리하고 진행 상황을 HTTP로 보고합니다. 관련 이슈는 115, 46입니다.

## 파일

| 파일 | 역할 |
|---|---|
| `orchestrator_node.py` | 노드 본체. SSE로 받은 명령을 큐에 넣고 워커 스레드에서 처리합니다. heartbeat, 토큰 갱신도 여기서 합니다 |
| `be_client.py` | BE API 호출. 토큰 발급과 재발급, SSE 수신, 보고 재시도, 모델 다운로드 |
| `bundle.py` | 모델 번들(tgz) 압축 해제와 필수 파일 확인 |
| `runtime_executor.py` | UMI 런타임 실행과 종료, 출력 파싱, 서보 진단 |
| `steps.py` | 실행 명령의 step 목록 처리 (REPEAT 펼치기, MOTOR 값 변환) |
| `policy_control.py` | 시뮬 트랙에서 정책 노드 lifecycle 제어 |
| `robot_model.py` | URDF에서 관절 순서와 범위 읽기 |

## 명령 처리

### APPLY_DEPLOYMENT (배포)

대기 중인 배포 정보를 받아와서 모델 tgz를 다운로드하고, 크기와 sha256을 확인한 뒤 `models/<skillVersionId>/`에 풉니다. 진행 상황은 DOWNLOADING, VERIFYING, APPLYING, SUCCEEDED 순으로 보고합니다. 중간에 실패하면 FAILED를 보내고 이전 배포를 그대로 씁니다. 다운로드 URL이 만료돼서 403이 나면 URL을 다시 받아 한 번 더 시도합니다.

### EXECUTE_TASK (실행)

step을 순서대로 실행합니다. SKILL은 실행 엔진으로 돌리고, MOTOR는 관절 명령을 보냅니다. STARTING, RUNNING을 거쳐 SUCCEEDED, FAILED, CANCELED 중 하나로 끝납니다. 진행 보고의 currentStep은 지금 실행 중인 step 번호이고, 진행률은 timeoutSeconds 대비 경과 시간으로 계산합니다.

### CANCEL_EXECUTION (취소)

실행 중이면 바로 멈추고 CANCELED를 보고합니다. 이미 끝난 실행에 대한 취소는 무시합니다.

### 그 밖에

- 같은 명령이 다시 오면(commandId가 같으면) 한 번만 처리합니다. 보고에는 reportId와 순서 번호를 붙여서 BE가 중복을 걸러낼 수 있게 했습니다.
- 재시작하면 BE에 현재 배포 상태를 물어서 복구합니다. BE가 응답하지 않아도 실행할 때 디스크에 받아둔 모델을 찾아서 씁니다.
- SSE 연결이 끊기면 3초부터 간격을 늘려가며 다시 붙습니다.

## 실행 엔진

| executor | SKILL 처리 | 용도 |
|---|---|---|
| `ros2_policy` (기본값) | 정책 노드를 configure, activate 하고 timeoutSeconds 뒤에 deactivate | PC 개발, 시뮬 |
| `umi_runtime` | UMI 런타임을 띄워서 실행. timeoutSeconds는 첫 추론 사이클부터 계산 | 젯슨 실물 |

MOTOR step은 지금 실행 엔진과 상관없이 ros2_control 쪽으로만 명령을 보냅니다. 그래서 `umi_runtime`에서는 MOTOR가 동작하지 않습니다.

## 파라미터

| 이름 | 기본값 | 설명 |
|---|---|---|
| `base_url` | `http://localhost:8080` | BE 주소. 운영은 `https://j15a103.p.ssafy.io` |
| `device_id` | `51000000-...-0002` | 기기 id |
| `device_secret` | 없음 | 비워두면 환경변수 `SO101_DEVICE_SECRET`을 씁니다. 코드나 git에 넣지 마세요 |
| `token` | 없음 | 테스트용 고정 토큰. 비워두면 secret으로 발급하고 알아서 갱신합니다 |
| `executor` | `ros2_policy` | 위 표 참고 |
| `runtime_root` | `~/S15P21A103/ROS` | 런타임, venv, 모델 폴더의 기준 경로 |
| `runtime_python` | 없음 | 비워두면 `<runtime_root>/.venv/bin/python` |
| `runtime_move` | `true` | `false`면 추론만 하고 모터는 움직이지 않습니다 |
| `runtime_cycles` | `0` | 0이면 취소나 timeout까지 계속 돕니다 |
| `motor_port` | `/dev/ttyTHS1` | 서보 포트. 가짜 서보 버스를 쓸 때는 `/tmp/so101_fake_servo` |
| `max_servo_temp_c` | `55` | 실행 전후 온도 검사 기준. 0이면 검사 안 함. 런타임 실행 중 기준(60도)과는 별개입니다 |
| `models_dir` | 없음 | 비워두면 `<runtime_root>/models` |
| `heartbeat_sec` | `15` | heartbeat 간격. BE가 20초 넘게 못 받으면 오프라인으로 봅니다 |
| `motor_settle_sec` | `2` | timeoutSeconds가 없을 때 MOTOR 대기 시간 |
| `skill_default_sec` | `10` | timeoutSeconds가 없을 때 시뮬 SKILL 실행 시간 |

## 실행 방법

```bash
source /opt/ros/humble/setup.bash
source ROS/ros2_ws/install/setup.bash
export SO101_DEVICE_SECRET='BE에서 발급받은 값'

# 젯슨 실물
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io -p executor:=umi_runtime

# PC 시뮬 (bringup bus:=fake, so101_policy policy_node를 먼저 띄워두기)
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io -p executor:=ros2_policy -p models_dir:=$HOME/so101_models
```

젯슨에서 계속 띄워둘 때는 systemd 서비스로 돌립니다. `ROS/deploy/`와 `exec/01_빌드_배포_포팅매뉴얼.md`의 1.10절을 참고하세요.

같은 device_id로 PC와 젯슨을 동시에 띄우면 BE가 SSE 연결을 서로 끊어서 명령이 제대로 안 들어옵니다. 한 기계 안에서 두 번 띄우는 건 락 파일로 막아뒀지만, 기계가 다르면 못 막습니다.

## 테스트

```bash
python3 -m pytest src/so101_orchestrator/test/ -q
```

| 파일 | 내용 |
|---|---|
| `test_sse.py` | SSE 파싱, 토큰, 재시도, 취소 처리, 중복 실행 락 |
| `test_runtime_executor.py`, `fake_runtime.py` | 가짜 런타임으로 출력 파싱, MOVE 입력, 정지, 강제 종료, 실패 사유 확인 |
| `test_steps.py`, `test_bundle.py`, `test_robot_model.py` | step 처리, 번들 해제, URDF 파싱 |
| `fake_servo_bus.py` | 팔 없이 실물 경로를 돌려보기 위한 가짜 서보 버스. 사용법은 파일 위쪽 주석 |
