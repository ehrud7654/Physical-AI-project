# ROS 파트 포팅 매뉴얼 (SO-101 / Jetson)

학습 정책(UMI 체크포인트)을 SO-101 로봇 팔에서 실행하고, 백엔드(BE)와 연동해
배포·실행·상태보고하는 젯슨 측 런타임/오케스트레이터의 설치·실행 문서.

---

## 1. 사용 제품 버전 및 IDE

실측 환경(2026-09-23, 배포 대상 젯슨 기준).

| 구분 | 항목 | 버전 |
|------|------|------|
| 하드웨어 | 컴퓨트 | NVIDIA Jetson Orin Nano Developer Kit (Super) |
| 하드웨어 | 로봇 팔 | SO-101 (5축 + 그리퍼, Feetech ST3215 서보) |
| 하드웨어 | 카메라 | Galaxy S22 (UMI 스트림 앱, TCP) |
| OS | 배포판 | Ubuntu 22.04.5 LTS (aarch64) |
| OS | L4T | R36.4.7 (JetPack 6.x) |
| 가속 | CUDA | 12.6 (V12.6.68) |
| 미들웨어 | ROS | ROS 2 Humble |
| 미들웨어 | ros2_control | 2.54.0 |
| 런타임 | Python | 3.10.12 |
| 런타임 | PyTorch | 2.5.0 (nv24.08, CUDA 지원 빌드) |
| 런타임 | diffusers | 0.27.2 |
| 런타임 | OpenCV | 4.11.0 |
| 런타임 | numpy | 1.26.4 |
| IDE | 편집/빌드 | VS Code (원격 SSH) + colcon |

---

## 2. 필수 · 선행 파일 및 환경 구성 방법

### 2.1 필수 · 선행 파일

| 파일/자원 | 레포 포함 | 확보 방법 |
|-----------|:--------:|-----------|
| ROS 소스 (`ros2_ws/src/`) | O | 레포 |
| 설치 스크립트 (`setup_runtime.sh`, `deploy/`) | O | 레포 |
| 의존성 기록 (`requirements-jetson.txt`) | O | 레포 |
| URDF (`so101_bringup/urdf/`) | O | 레포 |
| **wheelhouse (torch 등 휠 ~1GB)** | **X** | **제출물 별첨 `wheelhouse-jetson.tar.gz`** |
| **device secret (`.env`)** | **X** | BE 에서 발급받아 수동 작성 |
| venv (`.venv`) | X | `setup_runtime.sh` 로 생성 |
| 모델 번들 | X | BE 가 런타임에 배포 |

> **왜 wheelhouse 가 레포에 없나:** 젯슨의 PyTorch 는 NVIDIA JetPack 전용 빌드로
> PyPI 에서 재현이 어렵다. 검증된 휠 세트(약 1GB)를 미리 모아 별첨으로 전달한다.

### 2.2 선행 시스템 패키지

```bash
# ROS 2 Humble + ros2_control (JetPack Ubuntu 22.04)
sudo apt update
sudo apt install -y ros-humble-desktop \
  ros-humble-ros2-control ros-humble-ros2-controllers \
  python3-colcon-common-extensions build-essential

# 서보 시리얼 포트 접근 권한 (재로그인 필요)
sudo usermod -aG dialout "$USER"
```

### 2.3 환경 구성 명령어

```bash
# (1) 레포 배치 — 아래 문서는 ~/S15P21A103 를 기준으로 한다
cd ~/S15P21A103/ROS

# (2) wheelhouse 전개 (별첨 tar.gz 를 ROS/ 밑에 푼다)
tar xzf /path/to/wheelhouse-jetson.tar.gz -C ~/S15P21A103/ROS/

# (3) 런타임 venv 설치 (wheelhouse 의 휠을 오프라인 설치 → ROS/.venv)
./setup_runtime.sh
#   기본 wheelhouse 경로 = ROS/wheelhouse-jetson
#   다른 위치면: ./setup_runtime.sh /path/to/wheelhouse-jetson

# (4) device secret 설정 (BE 발급값)
cp deploy/.env.example .env
$EDITOR .env            # SO101_DEVICE_SECRET=<발급값>
chmod 600 .env
```

torch 는 cusparselt 라이브러리 경로가 있어야 import 된다(설치 검증도 이 경로 사용):

```bash
export LD_LIBRARY_PATH=~/S15P21A103/ROS/.venv/local/lib/python3.10/dist-packages/cusparselt/lib:/usr/local/cuda/lib64
~/S15P21A103/ROS/.venv/bin/python -c 'import torch; print(torch.__version__, torch.cuda.is_available())'
# 기대: 2.5.0a0+... True
```

### 2.4 빌드 명령어

```bash
source /opt/ros/humble/setup.bash
cd ~/S15P21A103/ROS/ros2_ws
colcon build --symlink-install
source install/setup.bash
```

### 2.5 실행 명령어

**A. 자동 실행 (부팅 시 systemd 서비스)** — 권장

```bash
cd ~/S15P21A103/ROS
./deploy/install.sh                              # 유닛 설치 (@경로@ 자동 치환)
sudo systemctl enable --now robot-estop robot-orchestrator
journalctl -u robot-orchestrator -f              # 로그 확인
```

부팅 순서: `sync-time-http`(시각 동기) → `robot-estop` → `robot-orchestrator`.
시각이 틀리면 JWT/TLS 가 깨지므로, orchestrator 는 시각 동기 완료 후에만 뜬다.

서비스 제어:

```bash
sudo systemctl start|stop  robot-orchestrator     # 이번만
sudo systemctl enable|disable robot-orchestrator  # 부팅 자동 on/off
```

**B. 수동 실행 (개발/시뮬)**

```bash
source /opt/ros/humble/setup.bash
source ~/S15P21A103/ROS/ros2_ws/install/setup.bash

# 시뮬 트랙 — 팔 없이 전 파이프라인 검증 (FakeBus)
ros2 launch so101_bringup bringup.launch.py bus:=fake

# 오케스트레이터 (BE 연동 + 실물 추론 엔진)
ros2 run so101_orchestrator orchestrator --ros-args \
  -p base_url:=https://j15a103.p.ssafy.io \
  -p executor:=umi_runtime \
  -p device_id:=51000000-0000-4000-8000-000000000002 \
  -p runtime_root:=~/S15P21A103/ROS

# 비상정지 브리지
ros2 run so101_safety estop_bridge
```

### 2.6 하드웨어 연결 (핀맵)

| 대상 | 인터페이스 | 값 |
|------|-----------|-----|
| ST3215 서보 버스 | UART | `/dev/ttyTHS1`, 1,000,000 bps (group `dialout`) |
| 카메라 (S22) | TCP | 포트 8765 (`adb forward tcp:8765 tcp:8765`) |

서보는 데이지체인(ID 1–5 팔, 6 그리퍼) 단일 UART 버스. 핀은 Jetson 40핀 헤더의
UART TX/RX/GND → 서보 컨트롤 보드에 연결.

---

## 3. API 명세서

오케스트레이터 ↔ BE. base URL: `https://j15a103.p.ssafy.io/api/v1`
인증: `POST /device-auth/token` 으로 발급받은 JWT 를 Bearer 로 사용(만료 900s, 자동 재발급).

| 메서드 | 경로 | 용도 |
|--------|------|------|
| POST | `/device-auth/token` | device secret → JWT 발급 |
| GET (SSE) | `/robots/me/command-stream` | 명령 수신 (APPLY_DEPLOYMENT / EXECUTE_TASK / CANCEL_EXECUTION) |
| POST | `/devices/{deviceId}/heartbeat` | 생존 신호 (firmware/runtime/contract 버전 포함) |
| GET | `/robots/me/deployments/pending` | 대기 중 배포 manifest |
| GET | `/robots/me/deployments/{id}/manifest` | 배포 상세 (모델 다운로드 URL·sha256) |
| PATCH | `/robots/me/deployments/{id}` | 배포 진행 보고 (DOWNLOADING→VERIFYING→APPLYING→SUCCEEDED) |
| GET | `/robots/me/deployment/current` | 현재 배포(재시작 복구용) |
| PATCH | `/robots/me/executions/{id}` | 실행 진행 보고 (STARTING→RUNNING→SUCCEEDED/FAILED/CANCELED) |
| GET | `/robots/me/skills/active` | 활성 스킬 목록 |

멱등성: 보고는 `reportId` + 단조증가 `sequence`, 명령은 `commandId` 중복 차단.

식별자:
- deviceId `51000000-0000-4000-8000-000000000002`
- robotId `53000000-0000-4000-8000-000000000001`

> device secret 은 레포/문서에 포함하지 않으며 `ROS/.env`(권한 600, git 제외)로만 관리한다.
