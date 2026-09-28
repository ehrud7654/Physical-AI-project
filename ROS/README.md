# ROS

SO-101 로봇 팔을 젯슨에서 실제로 돌리는 쪽 코드입니다. 학습과 시뮬레이션은 `AI/`에서 하고, 여기서는 학습된 모델을 받아서 팔을 움직이고 결과를 백엔드(BE)에 보고합니다.

ROS 2 Humble 기준이고, 젯슨은 Jetson Orin Nano(JetPack 6, Ubuntu 22.04)를 씁니다. 설치 절차는 `exec/01_빌드_배포_포팅매뉴얼.md`의 1.10절에 정리했습니다.

## 전체 흐름

통신은 두 층으로 나뉩니다.

- BE와 젯슨 사이는 HTTP입니다. 젯슨이 BE에 SSE로 붙어서 명령(배포, 실행, 취소)을 받고, 진행 상황은 HTTP로 보고합니다. BE가 관절 명령을 직접 보내지는 않습니다.
- 젯슨 안에서는 실행 엔진이 카메라를 보고 추론해서 서보를 움직입니다.

실행 엔진은 두 가지가 있고, 오케스트레이터 파라미터 `executor`로 고릅니다.

| executor | 쓰는 곳 | 동작 |
|---|---|---|
| `umi_runtime` | 젯슨 실물 | UMI 체크포인트 런타임이 카메라, 추론, IK, 서보 제어를 전부 합니다 |
| `ros2_policy` | PC 개발, 시뮬 | ros2_control과 FakeBus 위에서 정책 노드가 더미 사인파로 팔을 흔듭니다 |

실물은 `umi_runtime`을 씁니다. `ros2_policy`는 팔 없이 BE 연동 흐름을 확인하는 용도로 남겨뒀습니다.

## 실물 실행 경로 (umi_runtime)

실물 추론은 팀에서 하드웨어 검증까지 마친 UMI 런타임을 그대로 가져와 씁니다. 오케스트레이터는 이 런타임을 켜고 끄고, 결과를 BE에 올리는 역할만 합니다.

```
BE ──SSE 명령──> 오케스트레이터 ──subprocess──> UMI 런타임 ──UART──> 서보
   <──진행 보고──                <──stdout JSON, exit code──
```

1. 배포 명령이 오면 모델 번들(tgz)을 받아 sha256을 확인하고 `models/<skillVersionId>/`에 풉니다.
2. 실행 명령이 오면 step을 순서대로 돕니다. SKILL step마다 런타임을 새로 띄우고, 런타임이 사이클마다 내보내는 JSON을 읽어 진행률을 보고합니다.
3. timeoutSeconds가 지나면 런타임에 SIGINT를 보내 멈추고 SUCCEEDED를 보고합니다. 시간은 첫 추론 사이클부터 셉니다. 런타임 로딩에 12초쯤 걸리기 때문입니다.
4. 취소가 오면 바로 멈추고 CANCELED, 런타임이 에러로 끝나면 마지막 에러 메시지를 담아 FAILED를 보고합니다.

런타임을 별도 프로세스로 띄우는 이유는 파이썬 환경이 달라서입니다. 런타임은 torch가 깔린 자체 venv에서 돌아야 하고, 오케스트레이터는 rclpy 때문에 시스템 파이썬을 씁니다.

런타임은 서보 포트(`/dev/ttyTHS1`)를 직접 엽니다. 그래서 실물로 돌릴 때는 ros2_control(bringup)을 같이 띄우면 안 됩니다.

런타임은 2026-09-22에 팀원이 외부 의존(shanks, so101_speed_measure)을 없앤 자기완결 버전으로 바꿨고, 여기도 그 버전을 넣었습니다. 우리가 추가한 건 실행 중 안전 검사 하나이고(`robot_controller.py`), 팀원 쪽에도 반영을 요청할 예정입니다.

### 안전 장치

ros2_control을 안 쓰는 경로라 `/joint_states` 기반 감시는 못 씁니다. 대신 이렇게 막습니다.

- 과열, 과부하, 저전압: 런타임이 동작할 때마다 서보 진단값을 확인해서 온도 60도(3번 연속), 부하 750, 전압 115 기준을 넘으면 멈춥니다. 오케스트레이터도 실행 전후로 온도를 한 번 더 봅니다. 서보가 자체적으로 70도쯤에서 토크를 끊으면 팔이 떨어지기 때문에 그 전에 막으려는 것입니다. 부하 750은 실제 팔에서 정상 동작 중에도 나올 수 있어서 실측 후 조정이 필요합니다.
- 관절 범위: 목표 틱을 관절별 범위 안으로 자르고, 토크가 꺼진 서보가 있으면 실행을 거부합니다. 작업공간, IK 오차, 바닥 높이 같은 판정은 런타임이 계산해서 로그에는 남기지만 실행을 막지는 않습니다. 캘리브 오차 때문에 오탐이 잦아서 팀에서 꺼둔 상태입니다.
- 비상정지: `/estop` 토픽을 받으면 `estop_bridge`가 `/tmp/umi_ESTOP` 파일을 만들고, 런타임은 동작 직전마다 이 파일이 있는지 확인합니다. 해제는 팔 상태를 보고 파일을 직접 지웁니다.
- 프로세스 관리: 오케스트레이터가 죽으면 런타임도 같이 멈추게 해뒀고(PR_SET_PDEATHSIG), 정지 요청 후 10초 안에 안 끝나면 강제 종료합니다.
- 중복 실행: 오케스트레이터는 한 기계에 하나만 뜹니다(`/tmp/so101_orchestrator.lock`). 같은 device_id로 두 개가 붙으면 BE가 SSE 연결을 서로 끊어서 명령이 빠집니다. 락은 한 기계 안에서만 막으니, PC와 젯슨을 동시에 켜지 마세요.

### 모델 번들

런타임은 학습 체크포인트(.ckpt)를 바로 읽지 못하고, TorchScript로 export한 번들이 필요합니다. export는 학습 쪽(GPU 서버)에서 합니다.

번들(tgz) 안에는 `encoder.pt`, `denoiser.pt`, `metadata.json`이 꼭 있어야 하고, `dataset.report.json`도 넣는 게 좋습니다. BE의 downloadUrl, sha256, sizeBytes는 이 tgz 하나 기준입니다. 스킬마다 tgz가 따로 오고, 스킬이 여러 개인 작업이면 manifest에 여러 개가 들어옵니다.

## 폴더 구성

`ros2_ws/` 밖에 있는 건 colcon 빌드 대상이 아닙니다.

| 경로 | 내용 | git |
|---|---|---|
| `ros2_ws/src/` | ROS 패키지 (orchestrator, safety, hardware, bringup, policy) | O |
| `runtime/` | UMI 체크포인트 런타임 | O |
| `deploy/` | 부팅 자동 실행용 systemd 유닛, 설치 스크립트, `.env` 예시 | O |
| `shanks/` | 수동 점검 도구(토크 끄기, 관절 이동 등). 런타임은 이제 이걸 쓰지 않습니다 | O |
| `configs/s22_camera_tcp.json` | 카메라-TCP 캘리브 | O |
| `requirements-jetson.txt`, `setup_runtime.sh` | 런타임 venv 설치 | O |
| `.env` | device secret | X |
| `.venv/` | 런타임 venv | X |
| `wheelhouse-jetson/` | torch 등 오프라인 설치 파일(약 1GB). 제출할 때는 tar.gz로 따로 첨부 | X |
| `models/` | 배포받은 모델 | X |

`runtime/README.md`는 팀원이 쓴 원래 문서라 경로가 팀원 작업 폴더(`stanford_umi_demo`) 기준이고, 리팩터 이전 내용입니다. 팀원 쪽 최신본으로 바꿔야 합니다.

ROS 패키지별 설명은 각 폴더 README에 있습니다.

- `so101_orchestrator`: BE 연동, 배포, 실행 관리
- `so101_safety`: 비상정지 브리지
- `so101_hardware`, `so101_bringup`: ros2_control 하드웨어 인터페이스와 launch (시뮬 트랙)
- `so101_policy`: 정책 노드, 지금은 더미 모드 (시뮬 트랙)

## 설치와 빌드

자세한 순서는 `exec/01_빌드_배포_포팅매뉴얼.md`의 1.10절을 보면 됩니다. 요약하면 이렇습니다.

```bash
# 런타임 venv (젯슨). wheelhouse는 첨부된 tar.gz를 ROS/ 아래에 풀어서 준비
cd ROS
./setup_runtime.sh

# ROS 패키지
cd ros2_ws
source /opt/ros/humble/setup.bash
colcon build --symlink-install
source install/setup.bash
```

젯슨용 torch는 PyPI에 없어서 미리 받아둔 wheelhouse로 오프라인 설치합니다. torch를 import하려면 `LD_LIBRARY_PATH`에 cusparselt 라이브러리 경로가 있어야 하는데, 오케스트레이터가 런타임을 띄울 때 알아서 넣어줍니다.

`build/`, `install/`, `log/`는 기계마다 따로 빌드하고 커밋하지 않습니다.

## 실행

젯슨에서는 systemd 서비스로 띄웁니다.

```bash
cp deploy/.env.example .env && chmod 600 .env   # SO101_DEVICE_SECRET 입력
./deploy/install.sh
sudo systemctl enable --now robot-estop robot-orchestrator
journalctl -u robot-orchestrator -f
```

부팅하면 시간 동기화(`sync-time-http`), `robot-estop`, `robot-orchestrator` 순으로 뜹니다. 젯슨은 RTC가 없어서 시간이 틀리면 토큰 인증이 실패하기 때문에, 시간 동기화가 끝나야 오케스트레이터가 시작됩니다.

카메라(S22 폰)는 USB로 연결하고 포트 포워딩을 따로 걸어야 합니다. 재부팅하거나 폰을 다시 꽂으면 풀리니 다시 해줘야 합니다.

```bash
adb forward tcp:8765 tcp:8765
```

### 팔 없이 테스트

- 실물 경로: `so101_orchestrator/test/fake_servo_bus.py`가 가짜 서보 버스를 만들어줍니다. 오케스트레이터에 `runtime_move:=false`, `motor_port:=/tmp/so101_fake_servo`를 주면 카메라, 추론, 진행 보고까지 팔 없이 확인할 수 있습니다. 방법은 파일 위쪽 주석에 적어뒀습니다.
- 시뮬 트랙: `bash src/so101_policy/scripts/verify_fakebus_loop.sh`로 bringup(FakeBus)과 정책 노드를 띄워서 확인합니다.

## 시뮬 트랙 설계 (ros2_control)

처음에는 이 구조로 실물까지 가려고 했고, 지금은 PC 개발용으로 씁니다.

```
정책 노드 ──관절각(rad) 30Hz──> forward_position_controller ──> SO101System(rad↔tick) ──> FakeBus / ST3215
joint_state_broadcaster ──/joint_states──> 정책 노드
```

- 명령과 상태는 ros2_control 표준 인터페이스만 씁니다. 관절각은 라디안, 그리퍼는 미터입니다.
- `bus:=fake`면 가짜 버스, `bus:=st3215`면 실제 서보입니다. 그래프는 같고 하드웨어 플러그인만 바뀝니다.
- 정책 노드는 lifecycle 노드이고, 오케스트레이터가 configure, activate, deactivate를 호출합니다. 추론과 IK 부분은 아직 비어 있어서 더미 사인파로 돕니다.
- 액션은 EE pose 상대값(위치 변화 + 회전 6D + 그리퍼)입니다. 수집 앱이 원래 pose를 기록하기 때문에 관절각보다 이쪽이 자연스럽습니다. 자세한 근거는 `docs/action_space_decision.md`에 있습니다.

## 담당

| 부분 | 패키지 / 이슈 | 담당 |
|---|---|---|
| 오케스트레이터 (BE 연동) | `so101_orchestrator` (115, 46) | 신현우 |
| 비상정지 | `so101_safety` (49) | 신현우 |
| 하드웨어 인터페이스, bringup | `so101_hardware` (132), `so101_bringup` (150) | 신현우 |
| ST3215 C++ 드라이버 | `so101_hardware` (151) | ROS 팀원 |
| 정책 노드 | `so101_policy` (46) | ROS + AI |
| UMI 런타임 | `runtime/` | 팀원 |

## 환경

| | 젯슨 | PC |
|---|---|---|
| OS | JetPack 6 (Ubuntu 22.04) | Ubuntu 22.04 (WSL2) |
| ROS | Humble ros-base | Humble |
| ROS_DOMAIN_ID | 0 | 42 |

젯슨을 처음 세팅하면서 막혔던 것들입니다.

- 시간이 틀어져 있으면 apt가 "Release file not valid yet"으로 실패합니다. 캠퍼스망에서 NTP가 막혀 있으면 HTTP 응답 헤더로 맞춥니다.
  `sudo date -s "$(curl -sI https://www.google.com | grep -i '^date:' | cut -d' ' -f2- | tr -d '\r')"`
- `rosdep update`가 캠퍼스망 프록시 때문에 HTTP 400으로 실패할 수 있습니다. 필요하면 다른 망에서 한 번 받아둡니다.
- 젯슨 이더넷 장치 이름은 `eth0`이 아니라 `enP8p1s0`입니다.

## 남은 작업

실물 런타임 쪽

- 실제로 모터를 움직이는 실행(`runtime_move=true`)은 아직 끝까지 돌려보지 못했습니다. 추론만 하는 모드와 가짜 서보 버스까지는 확인했습니다.
- 과부하 기준 750은 실제 팔에서 재보고 조정해야 합니다.
- 실물 모드에서 MOTOR step이 동작하지 않습니다. 지금은 실행 엔진과 상관없이 ros2_control 쪽으로만 명령을 보냅니다.
- BE에서 보내는 비상정지 명령은 아직 `/estop`으로 연결하지 않았습니다. 젯슨에서 토픽을 직접 보내는 건 됩니다.
- 카메라 포트 포워딩이 수동입니다.
- 스킬마다 런타임을 새로 띄워서 스킬 사이에 12초쯤 공백이 생깁니다.
- 이미 받은 모델도 배포할 때마다 다시 받습니다.
- 서비스가 시작하기 직전에 한 번 더 시간을 맞추는 부분은 권한 문제로 실제로는 동작하지 않습니다. 부팅할 때 맞추는 건 정상입니다.
- 오케스트레이터 실행 전 온도 기준은 55도이고, 런타임 실행 중 기준은 60도라 서로 다릅니다.

시뮬 트랙 쪽

- 정책 노드의 관측, 추론, IK 부분이 비어 있습니다.
- URDF의 캘리브 값이 임시값입니다. 실물 런타임은 `robot_calibration.py` 값을 씁니다.
- RViz, MoveIt 시각화는 메시가 들어간 URDF를 붙여야 합니다.

## 관련 이슈

44(환경), 46(실행 엔진, 정책 노드), 48(카메라), 49(안전), 51(상태 보고), 115(오케스트레이터), 132(하드웨어 인터페이스), 150(bringup), 151(ST3215 드라이버), 42(젯슨 용량), 66(sim→real 어댑터), 67(시뮬 추론 검증), 24(BE 관제 API)

폐기된 이슈: 45(ROS에서 IK, AI 쪽으로 이동), 50(드라이버, 132로 통합), 108(so101_msgs, 표준 인터페이스로 대체)
