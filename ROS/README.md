# ROS — so101 ROS2 워크스페이스

SO-101 로봇 팔의 **배포·실물 제어** ROS2 (Humble) 워크스페이스.
학습·시뮬은 `AI/`(순수 파이썬), 여기는 **실물이 도는 곳**이다.

> 이 문서는 **살아있는 설계 문서**다. 구조·계약이 바뀌면 여기부터 고친다.
> 마지막 갱신(2026-09-09): **ros2_control 표준층 전환**. 커스텀 `so101_driver`·`so101_msgs` 폐기 → `so101_hardware`(132)+`so101_bringup`(150). 정책=EE pose 액션, IK는 AI측. FakeBus 30Hz end-to-end 검증 완료.
> 같은 날: **정책 노드 골격(46)** — lifecycle + 더미 사인 모드. 정책노드→/commands→FakeBus→/joint_states 루프 닫힘 확인. ckpt·관측·IK 는 슬롯만.
> **2026-09-21 (46): 실행 엔진 = UMI 체크포인트 런타임 채택.** 실물 검증된 팀원 런타임(카메라→추론→IK→모터)을 `ROS/runtime/`+`ROS/shanks/` 로 들여오고, 오케스트레이터가 **subprocess 로 기동 + stdout/exit 파싱 → BE 리포트**. 자체 추론 이식(so101_policy 슬롯 채우기)은 병행 발전 트랙으로 유지. 상세 §3.5.

---

## 1. 전체 아키텍처 — 두 평면

헷갈리기 쉬운데, 통신이 두 층이다.

```
━━ 제어 평면 (BE ↔ 젯슨, HTTP, 저빈도) ━━━━━━━━━━━━━━━━━━━━
   BE ──명령(스킬 시작/정지, ckpt 로드)── 젯슨   (SSE, 젯슨이 아웃바운드)
   젯슨 ──상태/결과── BE                        (HTTP POST)
   상세: docs/deploy_control_interface_draft.md

━━ 데이터 평면 (젯슨 내부, ROS, 30Hz 실시간) ━━━━━━━━━━━━━━
   정책노드(ckpt, EE pose) →[AI측 IK]→ 관절각[rad] 30Hz
        → forward_command_controller → SO101System(rad↔tick) → 모터
                  ← 관절상태(joint_states)·카메라 ←
```

**IK는 AI측**(정책이 EE pose→관절각까지 냄). ROS는 관절각 라디안을 받아 서보로 흘리는 ros2_control 층.

**BE는 관절 명령을 보내지 않는다.** 고수준(시작/정지/ckpt)만. 실제 관절 명령은 젯슨 내부 정책이 30Hz로 생성한다.

---

## 2. 데이터 파이프라인 (학습까지 — 참고)

```
arpose 앱 (폰, ARCore) ── SE(3) pose + 영상 + IMU ──> BE /upload
   → GPU 학습 (t₀ 기준 상대 SE(3)) → ckpt(+메타) → 젯슨 배포
```

- 시연 수집 = **arpose** (`APP/arpose/`). UMI 방식의 폰 버전. 원본이 **6DoF pose**(위치+쿼터니언) @30Hz.
- 시뮬(`AI/sim`)은 **검증용**이지 학습 소스가 아니다.
- 계약: `AI/contract/episode.py` (AI 트랙 소유). EE pose 전환 제안은 `ROS/docs/action_space_decision.md`.

---

## 3. 배포 런타임 — 젯슨 ROS 그래프 (ros2_control)

```
[오케스트레이터]  ── SSE/POST ──> BE          (rclpy + httpx, FastAPI 아님)
      │ lifecycle/service (start/stop, ckpt 로드)
      ▼
[정책 노드](46, lifecycle, ckpt+IK 슬롯) ──관절각[rad] 6, 30Hz──> /forward_position_controller/commands
                                                    │  (controller_manager)
                                                    ▼
                          [forward_command_controller] → [SO101System](132, rad↔tick)
                                                    │        │ IServoBus(tick)
                                                    │        ▼
                                                    │   [st3215_bus](151) / FakeBus → 모터
   [joint_state_broadcaster] ──/joint_states(rad)──> 정책 관측
   [안전 노드](49) ──/estop──> 전체
```

- **명령 = ros2_control 표준 인터페이스**(`Float64MultiArray` → forward_command_controller). 커스텀 msg 없음.
- **bus 스왑:** `bus:=fake`(기본, 실물 없이 검증) ↔ `bus:=st3215`(실물, 151 완료 후). 같은 그래프, 하드웨어 플러그인만 교체.
- **리허설(팔 없을 때):** FakeBus 로 controller_manager 까지 그대로 돎 (검증 완료). MuJoCo 연동은 별도.
- **정책 노드 = lifecycle** (`/so101_policy`, configure/activate/deactivate). 오케스트레이터가 `/so101_policy/change_state` 로 전이. ckpt 없으면 **더미 사인파**로 팔을 흔들어 배관 검증. 상세: `ros2_ws/src/so101_policy/README.md`.

---

## 3.5 실행 엔진 — UMI 체크포인트 런타임 (46, A안)

실물 추론은 **팀원이 하드웨어 검증을 끝낸 독립 런타임**을 그대로 쓴다. 우리는 **켜고·끄고·상태를 BE에 올리는** 어댑터만 담당한다(코드 복사·재구현 없음).

```
오케스트레이터(rclpy, 시스템 python)
   │ APPLY_DEPLOYMENT → tgz 다운로드·sha256 → ROS/models/<skillVersionId>/ 압축해제
   │ SKILL step       → subprocess 기동 (별도 venv 인터프리터)
   ▼
ROS/.venv/bin/python -m umi_checkpoint_executor
   --model ROS/models/<svid> --shanks-dir ROS/shanks --camera-tcp ROS/configs/...
   → 카메라(S22 TCP) → TorchScript 추론 → IK → 안전게이트 → ST3215 직접 제어
   → stdout(JSON report)·exit code
   ▲
   └ 오케스트레이터가 파싱 → report_execution(RUNNING/SUCCEEDED/FAILED)
```

**왜 subprocess 인가:** 런타임은 torch/diffusers 가 깔린 **자기 venv 인터프리터**로 돌아야 하고, 오케스트레이터는 rclpy(시스템 python)다. 한 프로세스에 못 섞는다. 팀원의 `runtime/umi_checkpoint_executor_ros2/node.py` 도 같은 이유로 subprocess 래퍼다.

⚠️ **런타임은 `/dev/ttyTHS1` 을 직접 잡는다.** `so101_hardware`(ros2_control)와 **동시 실행 불가** — 실물 실행 중엔 bringup 을 띄우지 않는다. FakeBus/시뮬·수동제어·estop 경로는 ros2_control 을 계속 쓴다.

### 디렉터리 (ROS/ 기준, ros2_ws 밖 = colcon 빌드 대상 아님)

| 경로 | 내용 | git |
|---|---|---|
| `runtime/` | UMI 체크포인트 실행기 (cli·policy_runtime·motion_planner·robot_controller·camera_stream·calibration) | ✅ |
| `shanks/` | SO-101 저수준 lib (`common`/`grasp`/`kinematics`) — 런타임이 `--shanks-dir` 로 동적 import | ✅ |
| `configs/s22_camera_tcp.json` | camera-TCP 캘리브 (provenance sha256 대조용) | ✅ |
| `requirements-jetson.txt` · `setup_runtime.sh` | venv 재현 | ✅ |
| `.venv/` | 런타임 전용 venv | ❌ |
| `models/` | 배포 tgz 압축해제본 | ❌ |

### 모델 배포 아티팩트 = **export 번들 tgz** (raw ckpt 아님)

런타임은 단일 `.ckpt` 를 못 읽는다. GPU 학습 후 **TorchScript export** 된 번들이 필요하다.

```
GPU 학습 → best.ckpt → export(smoke_export/export_so101_pick_v1) → <name>_jetson.tgz → S3 → 젯슨
```
tgz 내용(필수 4): `encoder.pt` · `denoiser.pt` · `metadata.json` · `dataset.report.json`
(참고 포함 권장: `config.yaml` · `pc_check.json` · `reference.npz` · `*.manifest.json`)

- **export 는 학습(GPU) 쪽에서** — hydra/diffusion_policy 등 학습 스택이 필요해 젯슨에서 못 돈다.
- BE `Model.downloadUrl`/`sha256`/`sizeBytes` = **tgz 하나** 기준 → 기존 다운로드·검증 로직 그대로.

### venv 세팅 (젯슨, 1회)

```bash
cd ROS
./setup_runtime.sh [wheelhouse 경로]   # 기본 ~/Workspace/stanford_umi_demo/wheelhouse-jetson
```
젯슨 torch 는 PyPI 에 없다(JetPack aarch64 전용 휠) → **wheelhouse 오프라인 설치**.
실행 시 `LD_LIBRARY_PATH` 에 `cusparselt/lib` 가 없으면 `libcusparseLt.so.0` 로 **torch import 부터 실패** → 오케스트레이터가 subprocess env 에 넣어준다.

---

## 4. 인터페이스 계약 (ros2_control 표준)

**선(wire)엔 생 SI 단위**(라디안·미터·원본 이미지). 정규화([-1,1], 224²)는 **정책 노드 한 곳**에서. → 하드웨어에 정직, 계약 드리프트 방지. 커스텀 msg 없음 — ros2_control 표준 인터페이스만.

| 토픽/인터페이스 | 방향 | 타입 | 단위·규칙 |
|---|---|---|---|
| `/forward_position_controller/commands` | 정책→ctrl | `std_msgs/Float64MultiArray` | 관절각 6, **라디안**, 순서=so101.yaml, 30Hz |
| `/joint_states` | HW→정책 | `sensor_msgs/JointState` | 라디안, 순서=so101.yaml, 30Hz |
| `/camera/<name>/image` | HW→정책 | `sensor_msgs/CompressedImage` | 원본 프레임 |
| `/estop` | 안전→전체 | `std_msgs/Bool` | true=전 서보 토크 off |

- **IK는 AI측**: 정책이 EE pose→관절각까지 냄. ROS 경계엔 관절각 라디안이 도착 → 별도 IK 노드·`EECommand`·`JointCommand` 불필요(폐기).
- **틱은 최하위(IServoBus/st3215_bus)까지만**. rad↔tick 변환 = `so101_hardware` SO101System 한 곳. 캘리브(zero_tick·방향)는 URDF `<joint><param>`(로봇별 온보딩 프로필).
- 정책이 틱 관측(raw present-position) 원하면 SO101System에서 별도 발행 — **AI 확인 대기**.

---

## 5. 액션 공간 결정 — EE pose (상대)

**근거:** 데이터 소스 arpose가 네이티브로 SE(3) pose를 뱉는다(관절 없음). 관절각으로 가면 매 프레임 IK 변환 → 가지 튐·불연속. EE pose면 원본 그대로.

- **표현:** 위치 델타 + 회전 6D 표현(쿼터니언/오일러 아님) + 그리퍼. (`ROS/docs/action_space_decision.md`)
- **상대(델타):** arpose 세션마다 원점이 달라 절대 pose 불가 → t₀ 상대. 데이터 특성과 일치.
- **그리퍼 차원은 지금 계약에 넣어둔다** (나중 shape 변경 = 데이터 무효 방지). 신호 들어오기 전엔 상수.

**IK 위치:** **AI측 정책 파이프라인 안**(EE pose→관절각까지 정책이 냄). ROS는 IK 안 함 → 관절각 라디안을 forward_command_controller 로 스트림. MoveIt 은 시각화/검증용으로만 (실시간 경로 아님).

**ckpt 메타:** ckpt는 `contract_version`·`action_space`·정규화 등 메타를 달고 와야 정책 노드가 검증·설정 (엉뚱한 ckpt 로드 방지).

---

## 6. 소유권

| 구성요소 | 패키지/이슈 | 담당 |
|---|---|---|
| **하드웨어 인터페이스** (rad↔tick, IServoBus) | `so101_hardware` (132) ✅ | ROS/신현우 |
| **bringup** (URDF+컨트롤러+launch) | `so101_bringup` (150) ✅ | ROS/신현우 |
| **모터 드라이버** (st3215_bus, IServoBus 구현) | `so101_hardware` (151) | ROS 팀원 (C++) |
| 오케스트레이터 (BE↔ROS) | `so101_orchestrator` | ROS |
| 정책 노드 (lifecycle 골격·더미 ✅ / ckpt·관측·IK 슬롯) | `so101_policy` (46) | ROS 래퍼 + AI ckpt |
| 안전 노드 (FK충돌·estop) | `so101_safety` (49) | ROS |
| IK | AI 정책 파이프라인 | AI |
| hand-eye·좌표변환 | (28·29·30) | 트랙 A (오프라인) |

폐기: `so101_driver`(→132), `so101_msgs`/`EECommand`·`JointCommand`(→ros2_control 표준 인터페이스).

---

## 7. 머신 / 환경

| | 젯슨 (실물) | PC (개발·시뮬) |
|---|---|---|
| OS | JetPack 6 / Ubuntu 22.04 | WSL2 Ubuntu 22.04 |
| ROS | Humble `ros-base`(headless) | Humble (desktop 가능) |
| `ROS_DOMAIN_ID` | **0** (실물) | **42** (시뮬) |
| 역할 | 배포 전용 (8GB) | sim_bridge·rviz·개발 |

⚠️ 리허설로 PC↔젯슨 통신 시 도메인 일시 정렬 필요. 캠퍼스망 격리(멀티캐스트/AP isolation) 주의 → 유선/도메인 분리 권장.

---

## 8. 빌드 · 세팅

```bash
cd ROS/ros2_ws
source /opt/ros/humble/setup.bash
colcon build
source install/setup.bash
```

- `build/ install/ log/` 는 아키텍처별 산출물 → 커밋 금지 (.gitignore)
- 각 머신에서 각자 빌드 (x86 결과물 arm64 공유 불가)

**전체 루프 검증 (실물 없이, 헤드리스):**
```bash
bash src/so101_policy/scripts/verify_fakebus_loop.sh   # bringup(FakeBus)+정책노드 띄우고 lifecycle→/joint_states 샘플→정리
```
기대: lifecycle 전이 3개 성공, `/commands`·`/joint_states` ≈30Hz, 6관절 값이 샘플마다 변함.
`ros2` CLI 가 `!rclpy.ok()` 로 죽으면 데몬이 죽은 것 → `ros2 daemon stop && ros2 daemon start` (스크립트는 자동 처리).

**젯슨 세팅 시 겪은 함정 (재삽질 방지):**
- **RTC 시간 어긋남** → apt "Release file not valid yet". NTP(123) 캠퍼스망 차단 시 HTTP로: `sudo date -s "$(curl -sI https://www.google.com | grep -i '^date:' | cut -d' ' -f2- | tr -d '\r')"`
- **`rosdep update` base.yaml HTTP 400** (캠퍼스망 프록시). 빈 패키지 빌드엔 무관. 실제 의존성 생기면 다른 망/Tailscale에서 1회 캐싱.
- 이더넷 장치명은 `eth0` 아니라 `enP8p1s0` (젯슨). SSH 헤드리스면 `avahi`로 `<host>.local` 권장.

---

## 9. 미결 / 열린 항목

| 항목 | 상태 |
|---|---|
| **st3215_bus C++** (IServoBus 구현) | 151, 팀원 진행 (핸드오프 전달) |
| **실물 캘리브** (zero_tick·방향) | URDF param placeholder 상태. 실측 후 교체 → REAL 게이트 |
| **그리퍼 신호 캡처** | arpose 미지원 (손가락 마커/포텐셔미터 추가 필요). **파지 학습 전제조건** |
| 틱 관측(raw present-position) 발행 여부 | AI 확인 대기 |
| ckpt 메타데이터 스키마 | AI와 합의 |
| **정책 노드 슬롯** (`_build_obs`·`_infer`·`_ik`) | 골격만. 관측 스펙·ckpt 는 AI, IK(pinocchio 포팅)는 ROS |
| 오케스트레이터 BE 계약 | SSE/POST 포맷·인증·run_id → BE와 (docs 초안) |
| MoveIt/RViz 시각화 | ver1 URDF(mesh·gripper prismatic) 붙일 때 |
| hand-eye 캘리브 값 `T_cam→tcp` | 트랙 A (오프라인 데이터 변환에서 적용) |

---

## 10. 관련 이슈

`44`(환경, 완료) · **`132`(so101_hardware, 완료✅)** · **`150`(bringup, 완료✅)** · `151`(st3215_bus 모터, 진행) · **`46`(정책 노드 골격✅, 슬롯 미구현)** · `48`(카메라) · `49`(안전) · `51`(상태→BE) · `66`(sim→real adapter) · `67`(시뮬 추론검증) · `42`(젯슨 용량) · `24`(BE 관제 API)
폐기: `45`(ROS IK→AI측) · `50`(드라이버→132) · `108`(so101_msgs→표준 인터페이스)
