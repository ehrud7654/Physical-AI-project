# ROS — so101 ROS2 워크스페이스

SO-101 로봇 팔의 **배포·실물 제어** ROS2 (Humble) 워크스페이스.
학습·시뮬은 `AI/`(순수 파이썬), 여기는 **실물이 도는 곳**이다.

> 이 문서는 **살아있는 설계 문서**다. 구조·계약이 바뀌면 여기부터 고친다.
> 마지막 갱신 기준: 액션 공간 = EE pose(상대) 방향, 그리퍼 신호 미결 (§5·§9).

---

## 1. 전체 아키텍처 — 두 평면

헷갈리기 쉬운데, 통신이 두 층이다.

```
━━ 제어 평면 (BE ↔ 젯슨, HTTP, 저빈도) ━━━━━━━━━━━━━━━━━━━━
   BE ──명령(스킬 시작/정지, ckpt 로드)── 젯슨   (SSE, 젯슨이 아웃바운드)
   젯슨 ──상태/결과── BE                        (HTTP POST)
   상세: docs/deploy_control_interface_draft.md

━━ 데이터 평면 (젯슨 내부, ROS, 30Hz 실시간) ━━━━━━━━━━━━━━
   정책노드(ckpt) → IK → 드라이버 → 모터
                  ← 관절상태·카메라 ←
```

**BE는 관절 명령을 보내지 않는다.** 고수준(시작/정지/ckpt)만. 실제 관절 명령은 젯슨 내부 정책이 30Hz로 생성한다.

---

## 2. 데이터 파이프라인 (학습까지 — 참고)

```
arpose 앱 (폰, ARCore) ── SE(3) pose + 영상 + IMU ──> BE /upload
   → GPU 학습 (t₀ 기준 상대 SE(3)) → ckpt(+메타) → 젯슨 배포
```

- 시연 수집 = **arpose** (`APP/arpose/`). UMI 방식의 폰 버전. 원본이 **6DoF pose**(위치+쿼터니언) @30Hz.
- 시뮬(`AI/sim`)은 **검증용**이지 학습 소스가 아니다.
- 계약: `AI/contract/episode.py` (+ EE pose 제안 `AI/contract/PROPOSAL_eepose.py`).

---

## 3. 배포 런타임 — 젯슨 ROS 그래프

```
[오케스트레이터]  ── SSE/POST ──> BE          (rclpy + httpx, FastAPI 아님)
      │ lifecycle/service (start/stop, ckpt 로드)
      ▼
[정책 노드](46) ──/ee_command(pose+gripper)──> [IK 노드](45, MoveIt)
                                                   │ pose→관절각
                                                   ▼
                              ──/joint_command(관절각 6)──> [드라이버](50) → 모터
[안전 노드](49) ──/estop──> 전체                  <──/joint_states, /camera── 
```

- **리허설(팔 없을 때):** 드라이버 자리에 `so101_sim_bridge`(MuJoCo 래핑) 꽂음. 같은 토픽, 반대편만 교체.
- 3 모드: **검증**(PC 단독, ROS 없음) / **리허설**(PC sim_bridge ↔ 젯슨 ROS) / **실물**(젯슨).

---

## 4. 인터페이스 계약 (so101_msgs)

**선(wire)엔 생 SI 단위**(라디안·미터·원본 이미지). 정규화([-1,1], 224²)는 **정책 노드 한 곳**에서. → 드라이버·sim_bridge가 하드웨어에 정직, 계약 드리프트 방지.

### 관측·안전 (표준 msg — 확정)

| 토픽 | 방향 | 타입 | 단위·규칙 |
|---|---|---|---|
| `/joint_states` | HW→정책 | `sensor_msgs/JointState` | 라디안, 순서=so101.yaml, **30Hz, header.stamp=캡처시각** |
| `/camera/<name>/image` | HW→정책 | `sensor_msgs/CompressedImage` | 원본 프레임 |
| `/estop` | 안전→전체 | `std_msgs/Bool` | true=전 서보 토크 off |

### 명령 (커스텀 msg — EE pose 계약)

| msg | 구간 | 내용 |
|---|---|---|
| `EECommand` | 정책 → IK | `geometry_msgs/Pose`(위치+회전) + `float64 gripper` |
| `JointCommand` | IK → 드라이버 | `float64[] positions` 관절각 6 (팔5+그리퍼1), 라디안 |

- IK는 pose만 변환, **그리퍼는 통과**시켜 JointCommand에 합침.
- ⛔ `.msg` 정확한 필드 확정 = 이슈 108 (액션 공간 결정 후).

---

## 5. 액션 공간 결정 — EE pose (상대)

**근거:** 데이터 소스 arpose가 네이티브로 SE(3) pose를 뱉는다(관절 없음). 관절각으로 가면 매 프레임 IK 변환 → 가지 튐·불연속. EE pose면 원본 그대로.

- **표현:** 위치 델타 + 회전 6D 표현(쿼터니언/오일러 아님) + 그리퍼. (`PROPOSAL_eepose.py`)
- **상대(델타):** arpose 세션마다 원점이 달라 절대 pose 불가 → t₀ 상대. 데이터 특성과 일치.
- **그리퍼 차원은 지금 계약에 넣어둔다** (나중 shape 변경 = 데이터 무효 방지). 신호 들어오기 전엔 상수.

**IK 위치:** 드라이버 아님 → **별도 IK 노드(45, MoveIt)**. 드라이버는 항상 관절각만 받아 하드웨어에서 절연.

**ckpt 메타:** ckpt는 `contract_version`·`action_space`·정규화 등 메타를 달고 와야 정책 노드가 검증·설정 (엉뚱한 ckpt 로드 방지).

---

## 6. 소유권

| 구성요소 | 패키지/이슈 | 담당 |
|---|---|---|
| 토픽 계약 | `so101_msgs` (108) | ROS (인터페이스) |
| IK 노드 | (45, MoveIt) | ROS |
| 오케스트레이터 (BE↔ROS) | (51 근처) | ROS |
| 정책 노드 | `so101_policy` (46) | ROS 래퍼 + AI ckpt |
| 드라이버 (Feetech) | `so101_driver` (50) | ROS 팀원 (하드웨어) |
| 안전 노드 | `so101_safety` (49) | ROS |
| sim_bridge | (66) | AI (시뮬 소유) |
| hand-eye·좌표변환 | (28·29·30) | 트랙 A (오프라인) |

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

**젯슨 세팅 시 겪은 함정 (재삽질 방지):**
- **RTC 시간 어긋남** → apt "Release file not valid yet". NTP(123) 캠퍼스망 차단 시 HTTP로: `sudo date -s "$(curl -sI https://www.google.com | grep -i '^date:' | cut -d' ' -f2- | tr -d '\r')"`
- **`rosdep update` base.yaml HTTP 400** (캠퍼스망 프록시). 빈 패키지 빌드엔 무관. 실제 의존성 생기면 다른 망/Tailscale에서 1회 캐싱.
- 이더넷 장치명은 `eth0` 아니라 `enP8p1s0` (젯슨). SSH 헤드리스면 `avahi`로 `<host>.local` 권장.

---

## 9. 미결 / 열린 항목

| 항목 | 상태 |
|---|---|
| **그리퍼 신호 캡처** | arpose 미지원 (손가락 마커/포텐셔미터 추가 필요). **파지 학습 전제조건** |
| 액션 공간 최종 확정 | EE pose 방향이나 팀 합의 D-AI 기록 필요 (108·계약) |
| `so101_msgs` .msg 필드 | 액션 확정 후 (108) |
| ckpt 메타데이터 스키마 | AI와 합의 |
| 오케스트레이터 BE 계약 | SSE/POST 포맷·인증·run_id → BE와 (docs 초안) |
| hand-eye 캘리브 값 `T_cam→tcp` | 트랙 A (오프라인 데이터 변환에서 적용) |
| PC↔젯슨 크로스머신 DDS | 리허설(66)에서 검증 |

---

## 10. 관련 이슈

`44`(환경, 완료) · `45`(MoveIt IK) · `46`(정책 노드) · `48`(카메라) · `49`(안전) · `50`(드라이버) · `51`(상태→BE) · `66`(sim→real adapter) · `67`(시뮬 추론검증) · `108`(so101_msgs) · `42`(젯슨 용량) · `24`(BE 관제 API)
