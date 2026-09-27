# ROS 파트 포팅 매뉴얼 (SO-101 / Jetson)

학습된 정책(UMI 체크포인트)을 젯슨에서 SO-101 로봇 팔로 실행하고, 백엔드(BE)와 연동해 모델 배포, 작업 실행, 상태 보고를 하는 부분의 설치와 실행 방법입니다.

## 1. 사용 제품 버전 및 IDE

2026-09-23에 실제 배포용 젯슨에서 확인한 값입니다.

| 구분 | 항목 | 버전 |
|---|---|---|
| 하드웨어 | 컴퓨터 | NVIDIA Jetson Orin Nano Developer Kit (Super) |
| 하드웨어 | 로봇 팔 | SO-101 (5축 + 그리퍼, Feetech ST3215 서보) |
| 하드웨어 | 카메라 | Galaxy S22 (UMI 스트림 앱) |
| OS | 배포판 | Ubuntu 22.04.5 LTS (aarch64) |
| OS | L4T | R36.4.7 (JetPack 6.x) |
| GPU | CUDA | 12.6 (V12.6.68) |
| 미들웨어 | ROS | ROS 2 Humble |
| 미들웨어 | ros2_control | 2.54.0 |
| 런타임 | Python | 3.10.12 |
| 런타임 | PyTorch | 2.5.0 (NVIDIA nv24.08, CUDA 지원 빌드) |
| 런타임 | diffusers | 0.27.2 |
| 런타임 | OpenCV | 4.11.0 |
| 런타임 | numpy | 1.26.4 |
| 런타임 | pyserial | 3.5 |
| IDE | 편집, 빌드 | VS Code (원격 SSH), colcon |

## 2. 필수 및 선행 파일, 환경 구성 방법

### 2.1 필요한 파일

| 파일 | 레포 포함 | 준비 방법 |
|---|---|---|
| ROS 소스 (`ROS/ros2_ws/src/`) | O | 레포 |
| UMI 런타임 (`ROS/runtime/`) | O | 레포 |
| 설치 스크립트 (`ROS/setup_runtime.sh`, `ROS/deploy/`) | O | 레포 |
| 의존성 목록 (`ROS/requirements-jetson.txt`) | O | 레포 |
| wheelhouse (torch 등 설치 파일, 약 1GB) | X | 별첨한 `wheelhouse-jetson.tar.gz` |
| device secret (`ROS/.env`) | X | BE에서 발급받아 직접 작성 |
| venv (`ROS/.venv`) | X | `setup_runtime.sh`로 생성 |
| 모델 | X | 실행 중에 BE가 배포 |

wheelhouse를 레포에 넣지 않은 이유는 크기와 설치 방식 때문입니다. 젯슨용 PyTorch는 NVIDIA가 JetPack용으로 따로 빌드한 것이라 PyPI에서 그대로 받을 수 없습니다. 그래서 동작을 확인한 설치 파일 묶음을 별도로 첨부했습니다.

### 2.2 시스템 패키지 설치

JetPack 6(Ubuntu 22.04)이 설치된 젯슨을 기준으로 합니다. 먼저 ROS 2 apt 저장소를 등록합니다.

```bash
sudo apt update
sudo apt install -y software-properties-common curl
sudo add-apt-repository -y universe
sudo curl -sSL https://raw.githubusercontent.com/ros/rosdistro/master/ros.key \
  -o /usr/share/keyrings/ros-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/ros-archive-keyring.gpg] \
http://packages.ros.org/ros2/ubuntu $(. /etc/os-release && echo $UBUNTU_CODENAME) main" \
  | sudo tee /etc/apt/sources.list.d/ros2.list >/dev/null
```

ROS 2와 나머지 패키지를 설치합니다.

```bash
sudo apt update
sudo apt install -y ros-humble-desktop \
  ros-humble-ros2-control ros-humble-ros2-controllers \
  python3-httpx python3-pytest python3-colcon-common-extensions build-essential \
  python3-venv adb git

# 서보 시리얼 포트 권한 (적용하려면 다시 로그인)
sudo usermod -aG dialout "$USER"
```

레포를 받습니다.

```bash
git clone <레포 주소> ~/S15P21A103
```

### 2.3 환경 구성

아래 명령은 레포가 `~/S15P21A103`에 있다고 가정합니다.

```bash
cd ~/S15P21A103/ROS

# 1) 첨부한 wheelhouse를 ROS/ 아래에 풀기
tar xzf /path/to/wheelhouse-jetson.tar.gz -C ~/S15P21A103/ROS/

# 2) 런타임 venv 설치 (ROS/.venv 생성, 인터넷 없이 wheelhouse에서 설치)
./setup_runtime.sh
#    wheelhouse를 다른 곳에 뒀다면: ./setup_runtime.sh /path/to/wheelhouse-jetson

# 3) device secret 입력
cp deploy/.env.example .env
nano .env               # SO101_DEVICE_SECRET=발급받은 값
chmod 600 .env
```

torch를 불러오려면 cusparselt 라이브러리 경로가 필요합니다. 설치가 제대로 됐는지는 이렇게 확인합니다.

```bash
export LD_LIBRARY_PATH=~/S15P21A103/ROS/.venv/local/lib/python3.10/dist-packages/cusparselt/lib:/usr/local/cuda/lib64
~/S15P21A103/ROS/.venv/bin/python -c 'import torch; print(torch.__version__, torch.cuda.is_available())'
# 2.5.0a0+... True 가 나오면 정상
```

오케스트레이터로 실행할 때는 이 경로를 자동으로 넣어주기 때문에 따로 설정하지 않아도 됩니다.

### 2.4 빌드

```bash
source /opt/ros/humble/setup.bash
cd ~/S15P21A103/ROS/ros2_ws
colcon build --symlink-install
source install/setup.bash
```

### 2.5 실행

젯슨에서는 부팅할 때 자동으로 뜨도록 systemd 서비스로 설치하는 방법을 권장합니다.

```bash
cd ~/S15P21A103/ROS
./deploy/install.sh
sudo systemctl enable --now robot-estop robot-orchestrator
journalctl -u robot-orchestrator -f     # 로그 확인
```

`install.sh`는 다음 일을 합니다.

- `.env`가 없거나 secret이 비어 있으면 설치하지 않고 멈춥니다.
- 시간 동기화 스크립트(`deploy/sync-time-http.sh`)를 `/usr/local/bin/`에, 서비스(`sync-time-http.service`)를 `/etc/systemd/system/`에 설치합니다.
- `robot-estop`, `robot-orchestrator` 서비스 파일의 경로와 사용자 계정을 이 젯슨에 맞게 바꿔서 설치합니다.
- 세 서비스를 부팅 시 자동 실행으로 등록합니다.

부팅하면 시간 동기화(`sync-time-http`), `robot-estop`, `robot-orchestrator` 순서로 시작합니다. 젯슨은 RTC가 없어서 부팅 직후 시간이 틀릴 수 있고, 시간이 틀리면 토큰 인증과 HTTPS가 실패합니다. 그래서 시간 동기화가 끝나야 오케스트레이터가 시작하게 했습니다.

서비스를 켜고 끄는 명령입니다.

```bash
sudo systemctl start robot-orchestrator      # 지금 켜기
sudo systemctl stop robot-orchestrator       # 지금 끄기
sudo systemctl enable robot-orchestrator     # 부팅 시 자동 실행
sudo systemctl disable robot-orchestrator    # 자동 실행 해제
```

서비스 없이 직접 띄울 때는 이렇게 합니다.

```bash
source /opt/ros/humble/setup.bash
source ~/S15P21A103/ROS/ros2_ws/install/setup.bash
export SO101_DEVICE_SECRET='발급받은 값'

ros2 run so101_safety estop_bridge &
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io \
  -p executor:=umi_runtime \
  -p device_id:=51000000-0000-4000-8000-000000000002
```

같은 device_id로 오케스트레이터를 두 대(예: PC와 젯슨)에서 동시에 띄우면 명령이 제대로 들어오지 않습니다. 한 대에서만 실행하세요.

### 2.6 카메라 연결

S22를 젯슨에 USB로 연결합니다. 폰에는 UMI 스트림 앱이 설치되어 있어야 합니다(앱 설치는 APP 파트 문서 참고).

1. 폰에서 개발자 옵션의 USB 디버깅을 켭니다.
2. 처음 연결하면 폰에 "USB 디버깅을 허용하시겠습니까?" 창이 뜹니다. "이 컴퓨터에서 항상 허용"을 체크하고 허용합니다.
3. 젯슨에서 연결을 확인하고 포트를 연결합니다.

```bash
adb devices                          # 기기 이름 옆에 device 라고 나오면 정상
adb shell am start -n org.umi.stream/.MainActivity   # 스트림 앱 실행
adb forward tcp:8765 tcp:8765        # 폰의 8765 포트를 젯슨 localhost:8765로 연결
```

재부팅하거나 폰을 다시 꽂으면 포트 연결이 풀리니 `adb forward`를 다시 실행해야 합니다.

카메라 보정값(렌즈, 카메라와 그리퍼 사이 위치)은 `ROS/configs/s22_camera_tcp.json`에 있고, 오케스트레이터가 런타임을 띄울 때 자동으로 넘겨줍니다. 다른 폰이나 다른 거치대를 쓰면 이 파일을 다시 보정해야 합니다.

### 2.7 하드웨어 연결

| 대상 | 방식 | 값 |
|---|---|---|
| ST3215 서보 | UART (TTL, 반이중 버스) | `/dev/ttyTHS1`, 1,000,000 bps |
| 카메라 (S22) | USB (adb 포트 포워딩) | TCP 8765 |

서보는 서보 드라이버 보드를 거쳐 한 줄의 버스에 차례로 연결되어 있습니다. 젯슨 40핀 헤더와 드라이버 보드는 이렇게 연결합니다.

| 젯슨 40핀 | 신호 | 드라이버 보드 |
|---|---|---|
| 8번 | UART TX | RX |
| 10번 | UART RX | TX |
| 6번 | GND | GND |

- TX와 RX는 서로 엇갈려 연결합니다.
- 서보 전원은 드라이버 보드에 12V 어댑터로 따로 넣습니다. 젯슨에서 서보 전원을 끌어오지 않습니다. 정상일 때 서보에서 읽히는 전압은 약 12.4V이고, 11.5V 아래로 떨어지면 런타임이 동작을 멈춥니다.
- GND는 젯슨과 드라이버 보드가 반드시 공통이어야 통신이 됩니다.

서보 ID는 다음과 같이 설정되어 있어야 합니다.

| ID | 관절 |
|---|---|
| 1 | shoulder_pan (베이스 회전) |
| 2 | shoulder_lift (어깨) |
| 3 | elbow_flex (팔꿈치) |
| 4 | wrist_flex (손목 굽힘) |
| 5 | wrist_roll (손목 회전) |
| 6 | gripper (그리퍼) |

### 2.8 로봇 캘리브레이션

관절 영점, 회전 방향, 그리퍼 열림과 닫힘 위치는 `ROS/runtime/umi_checkpoint_executor/robot_calibration.py`에 이 팔에서 잰 값으로 들어 있습니다.

| 값 | 내용 |
|---|---|
| `ZERO_TICKS` | 관절 1~5가 0도일 때의 서보 값 |
| `DIRECTIONS` | 관절별 회전 방향 (1 또는 -1) |
| `GRIPPER_CLOSED_TICK`, `GRIPPER_OPEN_TICK` | 그리퍼를 완전히 닫고 열었을 때의 서보 값 |
| `GRIPPER_OPEN_WIDTH_M` | 그리퍼를 완전히 열었을 때의 간격 (m) |

같은 팔을 다른 젯슨으로 옮길 때는 그대로 쓰면 됩니다. 다른 SO-101 팔을 쓰거나 서보를 교체했다면 이 값을 다시 재야 합니다.

### 2.9 설치 확인

모터와 카메라 없이 확인할 수 있는 것부터 봅니다.

```bash
cd ~/S15P21A103/ROS

# 1) 런타임 자체 점검 (서보 프로토콜, 캘리브레이션, IK 계산 확인)
#    UMI_CHECKPOINT_EXECUTOR_SELF_CHECK_OK 가 나오면 정상
export LD_LIBRARY_PATH=$PWD/.venv/local/lib/python3.10/dist-packages/cusparselt/lib:/usr/local/cuda/lib64
.venv/bin/python -m runtime.umi_checkpoint_executor --self-check

# 2) 오케스트레이터 테스트
cd ros2_ws && source install/setup.bash
python3 -m pytest src/so101_orchestrator/test/ -q     # 모두 passed 면 정상
```

서비스를 켠 뒤 로그(`journalctl -u robot-orchestrator -f`)에서 확인할 것입니다.

| 로그 | 의미 |
|---|---|
| `orchestrator 시작 — BE=... device=...` | 노드가 떴습니다 |
| `[BEClient] command-stream 연결됨` | 토큰 발급이 됐고 명령을 받을 준비가 됐습니다 |
| `[BEClient] heartbeat 실패`가 없음 | BE 화면에서 로봇이 온라인으로 보입니다 |
| `BE 명령: APPLY_DEPLOYMENT` → `배포 ... 적용 완료` | 모델 배포가 끝났습니다 |
| `BE 명령: EXECUTE_TASK` → `실행 ... 완료` | 작업 실행이 끝났습니다 |

팔 없이 BE에서 배포와 실행까지 해보려면 가짜 서보 버스를 씁니다. 사용법은 `ROS/ros2_ws/src/so101_orchestrator/test/fake_servo_bus.py` 위쪽 주석에 있습니다. 이때는 모터를 움직이지 않고 추론만 합니다.

### 2.10 자주 생기는 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| torch를 불러올 때 `libcusparseLt.so` 없음 | 라이브러리 경로가 빠짐 | 2.3의 `LD_LIBRARY_PATH`를 설정합니다. 서비스로 돌릴 때는 자동으로 들어갑니다 |
| `No module named serial` | venv에 pyserial이 없음 | 첨부 wheelhouse로 `setup_runtime.sh`를 다시 실행합니다 |
| `/dev/ttyTHS1` 권한 없음 | dialout 그룹이 아님 | `sudo usermod -aG dialout $USER` 후 다시 로그인합니다 |
| `adb devices`에 `unauthorized` | 폰에서 허용하지 않음 | 폰 화면의 USB 디버깅 허용 창에서 허용합니다 |
| 카메라 연결 실패 | 포트 연결이 풀렸거나 앱이 꺼짐 | 스트림 앱을 켜고 `adb forward tcp:8765 tcp:8765`를 다시 실행합니다 |
| 토큰 발급 401 | secret이 틀림 | `ROS/.env`의 값을 확인합니다 |
| 토큰은 되는데 HTTPS 오류, 토큰 바로 만료 | 젯슨 시간이 틀림 | `date`로 확인하고 `sudo /usr/local/bin/sync-time-http.sh`를 실행합니다 |
| 명령이 들어오다 끊기거나 중복 처리 | 같은 device_id로 오케스트레이터가 두 개 떠 있음 | `ps -ef \| grep orchestrator`로 확인하고 하나만 남깁니다 |
| 서비스가 바로 죽음 | 빌드를 안 했거나 `.env` 없음 | 2.4 빌드를 다시 하고 `journalctl -u robot-orchestrator -n 50`으로 원인을 봅니다 |
| 모델 폴더 권한 오류 | `models/`를 root로 만듦 | `sudo chown -R $USER: ~/S15P21A103/ROS/models` |

### 2.11 비상 정지

실행 중인 팔을 소프트웨어로 멈추는 방법입니다. `/estop`에 true를 보내면 `robot-estop`이 `/tmp/umi_ESTOP` 파일을 만들고, 런타임은 이 파일을 보면 바로 멈춥니다.

```bash
# 방법 1: ROS 토픽
ros2 topic pub --once --qos-durability transient_local /estop std_msgs/msg/Bool "{data: true}"

# 방법 2: 파일을 직접 만들기
touch /tmp/umi_ESTOP
```

정지는 자동으로 풀리지 않습니다. 팔 상태를 확인한 뒤 사람이 직접 파일을 지워야 다시 실행할 수 있습니다.

```bash
rm /tmp/umi_ESTOP
```

이 기능은 소프트웨어가 살아 있어야 동작합니다. 최종 안전장치는 서보 전원을 직접 끊는 것입니다.

## 3. API 명세

오케스트레이터가 호출하는 BE API입니다. 기본 주소는 `https://j15a103.p.ssafy.io/api/v1`입니다.

인증은 device secret으로 토큰(JWT)을 발급받아 Bearer 헤더에 넣는 방식입니다. 토큰은 15분 동안 유효하고 오케스트레이터가 만료 전에 다시 받습니다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/device-auth/token` | device secret으로 토큰 발급 |
| GET (SSE) | `/robots/me/command-stream` | 명령 수신 (APPLY_DEPLOYMENT, EXECUTE_TASK, CANCEL_EXECUTION) |
| POST | `/devices/{deviceId}/heartbeat` | 생존 신호 (펌웨어, 런타임, 계약 버전 포함) |
| GET | `/robots/me/deployments/pending` | 대기 중인 배포 정보 (모델 다운로드 URL, sha256) |
| POST | `/robots/me/deployments/{id}/manifest` | 다운로드 URL이 만료됐을 때 재발급 |
| PATCH | `/robots/me/deployments/{id}` | 배포 진행 보고 (DOWNLOADING, VERIFYING, APPLYING, SUCCEEDED, FAILED) |
| GET | `/robots/me/deployment/current` | 현재 적용된 배포 (재시작 후 복구용) |
| PATCH | `/robots/me/executions/{id}` | 실행 진행 보고 (STARTING, RUNNING, SUCCEEDED, FAILED, CANCELED) |

BE 응답은 `{"data": ...}` 형태로 감싸져 있고, 아래 응답 예시는 `data` 안쪽만 적었습니다.

### 3.1 토큰 발급

```http
POST /device-auth/token
Content-Type: application/json

{"deviceId": "51000000-0000-4000-8000-000000000002", "deviceSecret": "<secret>"}
```

```json
{"accessToken": "<JWT>", "expiresIn": 900}
```

### 3.2 heartbeat

15초마다 보냅니다. BE가 20초 넘게 받지 못하면 로봇을 오프라인으로 표시합니다. 응답의 `nextHeartbeatSeconds`가 오면 그 간격으로 바꿉니다.

```http
POST /devices/{deviceId}/heartbeat
Authorization: Bearer <JWT>

{"firmwareVersion": "...", "runtimeVersion": "...", "contractVersion": "robot-command-v1"}
```

```json
{"serverTime": "...", "nextHeartbeatSeconds": 15}
```

### 3.3 명령 수신 (SSE)

`GET /robots/me/command-stream`에 연결해 두면 BE가 이벤트 이름 `robot.command`로 명령을 보냅니다. `data`는 JSON 한 개입니다. 연결이 끊기면 3초부터 간격을 늘려가며 다시 연결합니다.

```text
event: robot.command
data: {"type": "EXECUTE_TASK", "commandId": "...", "executionId": "...", "steps": [...]}
```

| type | 주요 필드 | 동작 |
|---|---|---|
| `APPLY_DEPLOYMENT` | `commandId`, `deploymentId` | 배포 정보를 받아 모델 설치 |
| `EXECUTE_TASK` | `commandId`, `executionId`, `steps` | step을 순서대로 실행 |
| `CANCEL_EXECUTION` | `commandId`, `executionId` | 실행 중인 작업 중단 |

`steps`는 트리를 평평하게 펼친 목록이고 `parentStepId`로 부모를 가리킵니다.

| step type | 필드 | 내용 |
|---|---|---|
| `SKILL` | `skillVersionId`, `timeoutSeconds` | 배포된 모델로 정해진 시간 동안 작업 |
| `MOTOR` | `parameters: {"motor": "J1"~"J6", "angle": 값}` | 관절 하나를 지정한 값으로 이동 |
| `REPEAT` | `repeatCount` | 자식 step을 정해진 횟수만큼 반복 |

같은 부모 아래 step은 `order` 순서로 실행합니다.

### 3.4 모델 배포

`APPLY_DEPLOYMENT`를 받으면 대기 중인 배포 정보를 가져옵니다.

```json
GET /robots/me/deployments/pending

{
  "deploymentId": "...",
  "models": [
    {"deploymentItemId": "...", "skillVersionId": "...",
     "downloadUrl": "<S3 presigned URL>", "sizeBytes": 123456, "sha256": "..."}
  ]
}
```

모델 파일은 `downloadUrl`에서 직접 받고, 이때는 인증 헤더를 붙이지 않습니다. 받은 뒤 크기와 sha256을 확인하고 `ROS/models/<skillVersionId>/`에 풉니다. 모델 파일(tgz) 안에는 `encoder.pt`, `denoiser.pt`, `metadata.json`이 있어야 하고, 없으면 배포를 실패로 처리합니다.

진행 상황은 이렇게 보고합니다.

```json
PATCH /robots/me/deployments/{deploymentId}

{
  "reportId": "...", "sequence": 3, "status": "VERIFYING", "progressPercent": 80,
  "items": [{"deploymentItemId": "...", "status": "VERIFIED"}]
}
```

상태는 `DOWNLOADING` → `VERIFYING` → `APPLYING` → `SUCCEEDED` 순서로 바뀝니다. 실패하면 `FAILED`와 함께 `"error": {"code": "...", "message": "..."}`를 보냅니다.

### 3.5 실행 보고

```json
PATCH /robots/me/executions/{executionId}

{"reportId": "...", "sequence": 5, "status": "RUNNING", "currentStep": 2, "progressPercent": 40}
```

- 상태는 `STARTING` → `RUNNING` → `SUCCEEDED`, `FAILED`, `CANCELED` 중 하나로 끝납니다.
- `currentStep`은 지금 실행 중인 step 번호입니다(REPEAT를 펼친 기준, 1부터).
- `progressPercent`는 `timeoutSeconds` 대비 경과 시간으로 계산합니다.
- `FAILED`일 때는 `error`를 반드시 보냅니다.

### 3.6 ROS 토픽

오케스트레이터와 안전 노드가 쓰는 ROS 토픽입니다.

| 토픽 | 타입 | 발행 | 구독 | 내용 |
|---|---|---|---|---|
| `/estop` | `std_msgs/Bool` (latched) | safety 노드, 오케스트레이터 | estop 브리지 | 비상 정지 |
| `/joint_states` | `sensor_msgs/JointState` | ros2_control | 오케스트레이터, 정책 노드 | 현재 관절 값 (시뮬 트랙) |
| `/forward_position_controller/commands` | `std_msgs/Float64MultiArray` | 오케스트레이터(MOTOR), 정책 노드 | ros2_control | 관절 목표 값 (시뮬 트랙) |
| `/robot_description` | `std_msgs/String` (latched) | robot_state_publisher | 오케스트레이터 | URDF (관절 순서와 범위) |

젯슨 실물(`executor:=umi_runtime`)에서는 UMI 런타임이 서보 포트를 직접 쓰기 때문에 ros2_control을 띄우지 않고, `/estop`만 사용합니다.

### 3.7 그 밖에

같은 명령이 여러 번 와도 한 번만 처리하도록 commandId로 걸러내고, 보고할 때는 reportId와 순서 번호를 같이 보내서 BE에서 중복을 막을 수 있게 했습니다.

사용하는 식별자는 다음과 같습니다.

- deviceId: `51000000-0000-4000-8000-000000000002`
- robotId: `53000000-0000-4000-8000-000000000001`

device secret은 레포와 문서에 넣지 않습니다. `ROS/.env`에만 두고, 이 파일은 권한 600으로 설정하며 git에서 제외되어 있습니다.
