# UMI `relative_target_trajectory` 잠정 계약

관련 Jira: `S15P21A103-154`(UMI 상대 청크 경로),
`S15P21A103-171`(8-step chunk 시뮬레이션 검증)

상태: **Track A/B 합의 전 잠정안**

이 문서는 실수집 UMI 데이터의 상대 EEF 궤적 계약을 고정한다. 현행 계약 0.3.0의
관절 `(6,)` 단일-step `action`을 변경하거나 대체하지 않는다. 트랙 B의 기존
학습·평가 코드도 이 문서만으로 변경하지 않는다.

## 1. 의미 분리

| 데이터 | 필드 | 의미 |
|---|---|---|
| 시뮬레이션·로봇 | `command` | 실제 controller에 전달했거나 기록한 명령 |
| 핸드헬드 UMI | `relative_target_trajectory` | 사람이 실제로 이동한 미래 pinch 궤적 |

UMI raw에는 robot `ctrl`, EEF controller target 또는 IK 입력 명령이 없다. 따라서
`relative_target_trajectory`를 `command`라고 부르거나 존재하지 않는 명령값을
역산해 채우지 않는다.

## 2. 텐서 계약

스키마 이름은 `umi_relative_chunk/0.1.0-provisional`이다.

| 항목 | 값 |
|---|---|
| 샘플링 주기 | 원본 timestamp를 보존한 10 Hz nearest-frame sampling |
| observation horizon | 2 step, 약 0.2초 |
| action horizon | 8 future step, 약 0.8초 |
| `image` | `(N, 2, 3, 224, 224)`, `uint8`, canonical RGB |
| `proprio` | `(N, 2, 10)`, `float32` |
| `relative_target_trajectory` | 저장 배열명 `action`, `(N, 8, 10)`, `float32` |
| `observation_timestamp` | `(N,)`, `float64`, 초 |
| `action_timestamp` | `(N, 8)`, `float64`, 초 |
| `source_row` | `(N,)`, 원본 pose 행 위치 |

10차원 벡터 순서는 다음과 같다.

```text
[x_m, y_m, z_m, r0x, r0y, r0z, r1x, r1y, r1z, gap_m]
```

- `x_m..z_m`: 현재 pinch 기준 미래 pinch 평행이동, 단위 m
- `r0*`, `r1*`: 현재 pinch 기준 미래 pinch 회전행렬의 첫 두 행
- `gap_m`: 상대 변화량이 아닌 미래 시점의 절대 접촉면 gap, 단위 m
- 허용 gap 범위: `[0.0, 0.09] m`

`action`은 현재 파일 구현과의 호환을 위한 배열 이름이다. 계약 의미와 외부 API
필드명은 반드시 `relative_target_trajectory`로 기록한다.

## 3. 기준 pose와 좌표계

모든 미래 target은 직전 target이 아니라 **같은 현재 pinch pose**를 anchor로 한다.

```text
T_relative[k] = inverse(T_current_pinch) @ T_future_pinch[k]
T_robot_target[k] = T_robot_current_pinch @ T_relative[k]
```

canonical pinch child frame은 다음과 같다.

- `+X`: 그리퍼 jaw 개폐축
- `+Y`: 위쪽
- `+Z`: 세워진 물체 몸통을 향하는 수평 접근축
- 오른손 좌표계

카메라 pose 입력은 ARCore/OpenGL 규약이다.

- 카메라 `+X`: 오른쪽
- 카메라 `+Y`: 위
- 카메라 `-Z`: 전방
- pose 회전: 카메라 local → ARCore world

2026-09-11 파일럿의 현행 extrinsic은
`configs/real/umi_s22_canonical_pinch_side_grasp_provisional.json`이다. 이는 영상
의미와 중력 방향으로 확인한 **잠정 마커중점 extrinsic**이며 실물 패드 중심 및
robot-base/workspace 등록 확정값이 아니다.

## 4. 파지 task 의미

0911 시연은 세로로 선 구운감자 과자 케이스의 몸통 양옆을 컵처럼 잡아 들어 올리는
동작이다. MuJoCo task frame에서는 다음 조건으로 표현한다.

- 접근: 세계 `+X` 방향의 수평 접근
- jaw 개폐: 세계 `Y`축 방향
- 접촉 높이: 물체 몸통 중앙 높이
- 금지하는 해석: 위에서 아래로 내려와 상단을 집는 top-down/claw grasp

세계축 방향은 MuJoCo 진단용 task-frame 정의이며, 실물 robot base에 그대로 적용할
수 있는 측정 캘리브레이션이 아니다.

## 5. 시간 및 실행 계약

학습 라벨은 원본 timestamp를 보존하고 `time_scale=1.0`으로 유지한다. 속도·가속도
한계를 맞추기 위해 데이터셋 timestamp나 waypoint 값을 수정하지 않는다.

실행 시 균일 시간 확대가 필요하면 청크 전체에 같은 배율을 적용하고 다음을
계측한다.

- `required_time_scale`
- `scheduled_command_period_s`
- 확대 전·후 arm 최대 속도·가속도
- 확대 전·후 gap 최대 속도·가속도
- 청크 경계에서의 pose 추종 오차와 위상 지연

현재 오프라인 사전검사 잠정 기준은 다음과 같다.

```text
arm speed       <= 1.0 rad/s
arm acceleration <= 3.0 rad/s^2
gap speed       <= 0.08 m/s
gap acceleration <= 0.3 m/s^2
```

이 값은 `configs/real/so101_ver1.json`의 소프트웨어 검사 기준이며 하드웨어 승인
안전 한계가 아니다. `S15P21A103-113`의 실행가능성 검사 기준과 합의 후 통일한다.
시간 확대는 청크 경계의 관측 분포를 바꿀 수 있으므로 성능 보존을 가정하지 않고
`S15P21A103-171`에서 별도로 검증한다.

## 6. 기존 계약과의 호환 경계

- 현행 `(6,)` 관절 단일-step 계약은 시뮬레이션 `command` 경로에서 유지한다.
- 실제 command가 없는 UMI에는 관절 `state[t+1]`을 command로 사용하지 않는다.
- `(8, 10)` UMI 데이터는 전용 dataset loader, policy, decoder를 사용한다.
- 트랙 B가 `(6,)`만 지원하는 동안 이 데이터를 기존 학습기에 입력하지 않는다.
- Track A/B 검토와 `S15P21A103-171` 결과 전에는 본수집 계약이나 배포 계약으로
  승격하지 않는다.

## 7. 현재 구현과 검증 상태

관련 구현:

- `umi/relative_dataset.py`: shape, dtype, timestamp와 gap 검증
- `data/relative_chunk_dataset.py`: 전용 loader
- `policy/relative_chunk_bc.py`: 진단용 `(8,10)` chunk BC
- `tools/train_relative_chunk_bc.py`: 전용 학습기
- `umi/relative_robot_preflight.py`: 현재 robot pose에 decode 및 IK 사전검사
- `umi/timing.py`: 균일 시간 확대율과 실행 timestamp 계산

현재 v9 파일럿은 70개 episode, 1,248개 anchor이며 데이터 계약 위반은 0건이다.
그러나 32개 MuJoCo 등록 후보 탐색 결과는
`REJECTED_NO_VALID_REGISTRATION`이다. 측면 파지 방향·몸통 중앙 높이는 13/14
validation episode에서 만족했지만, 장면 조건과 IK를 동시에 만족한 candidate는
없었다. 따라서 데이터 계약 검증 완료를 정책·시뮬레이션 성공으로 해석하지 않는다.

## 8. 합의가 필요한 항목

다음 항목이 모두 완료되어야 이 잠정안을 정식 계약으로 승격할 수 있다.

1. Track A/B의 shape·좌표계·필드 의미 검토
2. `S15P21A103-171`의 8-step/8-step 시뮬레이션 대조
3. `S15P21A103-113`과 속도·가속도 기준 통일
4. chunk 경계에서 시간 확대에 따른 관측 지연 계측
5. robot-base/workspace 및 실물 pinch extrinsic 확정

공유 GPU 서버에서는 학습만 수행한다. 추론, probe, preflight, 등록 탐색,
MuJoCo와 rollout은 로컬 CPU에서 수행한다.
