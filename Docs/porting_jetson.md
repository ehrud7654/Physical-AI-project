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

```bash
sudo apt update
sudo apt install -y ros-humble-desktop \
  ros-humble-ros2-control ros-humble-ros2-controllers \
  python3-httpx python3-colcon-common-extensions build-essential

# 서보 시리얼 포트 권한 (적용하려면 다시 로그인)
sudo usermod -aG dialout "$USER"
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

`install.sh`는 레포 위치와 사용자 계정에 맞춰 서비스 파일을 설치합니다. `.env`가 없거나 secret이 비어 있으면 설치하지 않고 멈춥니다.

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

카메라는 S22를 USB로 연결하고, 폰에서 USB 디버깅을 허용한 뒤 포트를 연결합니다. 폰의 UMI 스트림 앱도 켜져 있어야 합니다. 재부팅하거나 폰을 다시 꽂으면 연결이 풀리니 다시 실행해야 합니다.

```bash
bash ~/Workspace/stanford_umi_demo/setup/adb_local.sh forward tcp:8765 tcp:8765
```

### 2.6 하드웨어 연결

| 대상 | 방식 | 값 |
|---|---|---|
| ST3215 서보 | UART | `/dev/ttyTHS1`, 1,000,000 bps |
| 카메라 (S22) | USB (adb 포트 포워딩) | TCP 8765 |

서보는 한 개의 UART 버스에 줄줄이 연결되어 있습니다(ID 1~5는 팔, 6은 그리퍼). 젯슨 40핀 헤더의 UART TX, RX, GND를 서보 컨트롤 보드에 연결합니다.

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

모델 파일은 BE가 준 S3 presigned URL에서 직접 받습니다. 이때는 인증 헤더를 붙이지 않습니다.

같은 명령이 여러 번 와도 한 번만 처리하도록 commandId로 걸러내고, 보고할 때는 reportId와 순서 번호를 같이 보내서 BE에서 중복을 막을 수 있게 했습니다.

사용하는 식별자는 다음과 같습니다.

- deviceId: `51000000-0000-4000-8000-000000000002`
- robotId: `53000000-0000-4000-8000-000000000001`

device secret은 레포와 문서에 넣지 않습니다. `ROS/.env`에만 두고, 이 파일은 권한 600으로 설정하며 git에서 제외되어 있습니다.
