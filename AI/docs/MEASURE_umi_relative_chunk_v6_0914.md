# MEASURE — 실물 UMI 상대 action chunk v6 파일럿 (2026-09-14)

관련 Jira: `S15P21A103-34`(학습 기준선), `S15P21A103-154`(UMI 상대 action preflight),
`S15P21A103-171`(8-step chunk 시뮬레이션 검증)

차원·좌표계·청크 길이와 기존 `(6,)` 계약의 호환 경계는
`docs/SPEC_umi_relative_target_trajectory_0914.md`를 정본으로 한다.

## 결론

`umi_real_20260911_v2/v4`의 SO-101 관절 단일-step BC는 비교용 기준선으로만
보존한다. 다음 학습 입력은 원본 UMI의 카메라 pose와 gripper gap에서 직접 만든
카메라 중심 상대 SE(3) action chunk를 사용한다.

이 변경은 다음 문제를 학습 라벨에서 제거한다.

- ARCore world를 임의의 로봇 base에 등록한 값
- 데이터 생성 시점의 SO-101 IK 오차와 관절 자세 편향
- 짧은 사람 시연을 수백 step으로 늘린 시간 보간
- 위치 위주 IK에서 발생한 파지 방향 손실

로봇 base 등록과 IK는 정책 출력 이후, 실행 시점의 현재 로봇 pose를 기준으로
수행한다. 기존 `umi.action_decode`, `umi.arm_preflight`의 `(T, 10)` 경계와 같은
의미다.

## 이 결정을 내린 근거

v2와 v4는 관절 궤적만으로 다음 상태를 매우 쉽게 예측할 수 있었다.

| 데이터 | 30 Hz | 15 Hz | 10 Hz |
|---|---:|---:|---:|
| v2, hold 대비 constant velocity 개선 | 94.10% | 88.20% | 82.74% |
| v4, hold 대비 constant velocity 개선 | 96.09% | 92.12% | 88.22% |

같은 체크포인트에서 이미지만 검정·셔플·노이즈로 바꾼 검사에서는 출력 변화가
있었지만 gripper 출력의 영향이 컸고, 물체 위치를 사용한다고 입증되지는 않았다.
특히 v4는 v2보다 궤적 shortcut이 더 강했다. 따라서 낮은 validation loss나 이미지
민감도 하나만으로 정책 성능을 주장하지 않는다.

## v6 잠정 데이터 계약

스키마: `umi_relative_chunk/0.1.0-provisional`

| 항목 | 값 |
|---|---|
| 표본 주기 | 원본 시간을 보존한 10 Hz nearest-frame sampling |
| observation horizon | 2 frames |
| action horizon | 8 future steps, 약 0.8초 |
| 이미지 | 정방향 RGB, `(2, 3, 224, 224)`, `uint8` |
| proprio | 현재 pinch 기준 pose history + absolute gap, `(2, 10)` |
| action | 모든 미래 pose를 같은 현재 pinch pose에 고정한 `(8, 10)` |
| action 열 | xyz[m] + rotation matrix 첫 두 행 6D + gap[m] |
| 시간 확대 | 없음, `time_scale=1.0` |
| robot base 등록 | 없음 |
| SO-101 IK | 없음 |

각 action target은 이전 target에 연쇄해서 표현하지 않는다. 모두 같은 observation
시점의 `T_current`를 기준으로 한다.

```text
T_relative[k] = inverse(T_current_pinch) @ T_future_pinch[k]
T_target[k]   = T_robot_current_pinch @ T_relative[k]
```

카메라 extrinsic은
`configs/real/umi_s22_marker_midpoint_provisional.json`을 사용한다. 이 값은
ARCore/OpenGL 카메라 축으로 명시되어 있지만, 패드 중심이 아닌 마커 중점이며
독립 hand-eye 확정값이 아니므로 계속 `provisional`이다.

## 생성 및 검증

```bash
cd AI

python tools/build_real_umi_relative_dataset.py \
  --bundles out/real_20260911_normalized \
  --extrinsic configs/real/umi_s22_marker_midpoint_provisional.json \
  --out datasets/umi_real_relative_20260911_v6 \
  --rate-hz 10 \
  --obs-horizon 2 \
  --action-horizon 8 \
  --image-rotation-deg 180

python tools/check_umi_relative_dataset.py
python tools/verify_umi_relative_dataset.py \
  datasets/umi_real_relative_20260911_v6
```

`--image-rotation-deg 180`은 로컬의 과거 canonical bundle에 방향 필드가 없어서
사용한 검토된 fallback이다. 새 정규화 bundle에
`camera.rotate_to_canonical_deg`가 있으면 메타데이터 값을 사용하며, CLI 값과
충돌하면 생성기를 중단한다.

## 0911 전체 생성 결과

- 입력 canonical bundle: 70편
- 제외된 원본: `rec_20260911_145737` 1편
  (`no_usable_segments_after_tracking_qc`, 앞선 canonical 정규화 단계에서 제외)
- 생성 episode: 70편
- 학습 anchor: 1,248개
- 편당 anchor: 최소 1, 중앙 18, 최대 31
- 선택된 연속 유효 구간: 최소 0.896초, 중앙 2.600초, 최대 3.900초
- 마지막 action target 시간: 중앙 약 0.800초
- 미래 상대 이동 거리: 중앙 64.4 mm, p95 198.5 mm, 최대 338.5 mm
- 미래 상대 회전: 중앙 2.28도, p95 11.10도, 최대 22.21도
- gap: 31.91~69.93 mm
- 계약 위반: 0건
- 회전 encode/decode·world-frame 불변성 등 단위검사: 4건 통과

constant-velocity shortcut의 미래 translation 오차는 중앙 55.7 mm,
p95 223.7 mm였다. 기존 v4의 한-step 관절 라벨처럼 거의 그대로 예측되는 구조는
아니지만, 이것만으로 이미지 사용을 입증한 것은 아니다.

샘플 contact sheet에서 초기·중간·후반 모두 구운감자 케이스가 정방향으로 화면에
포함되는 것을 확인했다.

배포용 tar는 Git에 넣지 않는다.

```text
file: datasets/umi_real_relative_20260911_v6.tar
bytes: 305114112
sha256: b211da54953a9089f4d41eba886ec837d498e7d886550ca5506996cd0f2dd78a
```

## 로컬 데이터셋 검증

공유 GPU 서버로 올리기 전에 로컬 PC에서 계약 검증을 끝낸다.

```bash
cd AI
python tools/verify_umi_relative_dataset.py \
  datasets/umi_real_relative_20260911_v6
```

## V100 smoke 학습 — 서버에서 허용되는 유일한 단계

이 데이터는 기존 `tools/train_bc.py`의 관절 `(6,)` 계약과 호환되지 않는다.
반드시 전용 학습기를 사용한다.

공유 GPU 서버(`jupyter01`)에서는 아래 학습 명령만 실행한다. 데이터 검증, probe,
preflight, 등록 탐색, MuJoCo, rollout은 체크포인트와 필요한 데이터를 로컬 PC로
내려받은 뒤 실행한다.

```bash
cd ~/S15P21A103_umi/AI
conda activate aiot_v100

export CUDA_VISIBLE_DEVICES=2
export AI_THREADS=2
export OMP_NUM_THREADS=2
export MKL_NUM_THREADS=2
export OPENBLAS_NUM_THREADS=2
export NUMEXPR_NUM_THREADS=2

python tools/train_relative_chunk_bc.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --device cuda \
  --epochs 3 \
  --batch-size 32 \
  --seed 0 \
  --out checkpoints/relative_chunk/umi_real_relative_v6_smoke.pt
```

smoke 학습은 loader, shape, CUDA, 역전파와 checkpoint 저장만 확인한다. loss가
내려가도 성능 통과가 아니다.

학습 후 체크포인트를 로컬 PC로 내려받아 같은 체크포인트에서 이미지 의존성을
검사한다. 공유 GPU 서버에서는 별도 추론·검증·시뮬레이션을 실행하지 않는다.

```bash
python tools/probe_relative_chunk_policy.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --policy-ckpt checkpoints/relative_chunk/umi_real_relative_v6_smoke.pt \
  --samples 1000 \
  --batch-size 64 \
  --device cpu \
  --out out/relative_chunk_v6_smoke_probe.json
```

검정·셔플·노이즈 영상에서 gap만 달라지고 translation/orientation이 거의 같다면
이미지로 물체 위치를 읽는 정책이 아니다. 이 경우 epoch를 늘리기 전에 구조 또는
데이터를 고쳐야 한다.

## 아직 주장할 수 없는 것

- `relative_chunk_bc`는 ACT/Diffusion Policy가 아니라 진단용 action-chunk BC다.
- 1,248개 anchor와 단일 물체/환경으로 sim-to-real 일반화를 입증할 수 없다.
- validation episode는 물체 위치가 명시적으로 라벨링된 spatial holdout이 아니다.
- UMI 마커 중점→실제 패드 중심 hand-eye가 확정되지 않았다.
- 0911 데이터의 success label은 검증되지 않았다.
- 사람 시연 속도를 SO-101이 그대로 실행 가능한지는 robot-time dynamic preflight로
  별도 확인해야 한다.
- MuJoCo 평가는 실물과 같은 카메라 방향·intrinsic·물체 노출을 맞추고,
  `(T,10)` 출력→IK→관절 명령 wrapper를 연결한 뒤에만 성능 지표로 해석한다.

## 다음 판정 순서

1. **V100 서버:** 3-epoch smoke 학습과 checkpoint 저장 확인
2. **로컬:** 같은 checkpoint의 검정·셔플·노이즈 이미지 검사
3. **V100 서버:** 한 episode overfit 학습과 30-epoch 기준선 학습
4. **로컬:** 상대 chunk를 현재 SO-101 pose에 decode하고 IK/dynamic preflight 수행
5. **로컬:** 시작 자세·물체 등록 탐색
6. **로컬:** 카메라 조건을 맞춘 MuJoCo 폐루프 영상과 성공률 확인

## 30-epoch 기준선 결과와 robot-time 사전검사

V100 seed 0, 30 epoch 학습 결과는 train `0.40240`, validation `0.42742`였다.
검증 최저값도 30 epoch에서 나왔다. 체크포인트는 다음과 같다.

```text
file: checkpoints/relative_chunk/umi_real_relative_v6_seed0.pt
bytes: 4137941
sha256: 47d530265f72eea42af6ba9d40603aa766232afbc23b662930e56345e72b6c32
```

같은 체크포인트의 967개 표본 이미지 치환 검사에서 원본 예측의 translation
오차는 34.5mm였고 constant-velocity 기준선은 78.9mm였다. 검정 이미지와 셔플
이미지로 바꾸면 원본 출력 대비 translation이 각각 79.0mm, 66.9mm 변했다.
따라서 정책이 이미지 전체를 사용하며 단순 등속 shortcut보다 낫다는 것은 확인했다.
다만 배경/진행 단계가 아니라 물체 위치 자체를 읽는지는 아직 입증되지 않았다.

다음 명령은 **로컬 PC에서만** 실행하는 offline 사전검사다. 모터를 전혀 사용하지
않는다. 체크포인트에 기록된
validation episode에서 균형 표본을 뽑고, 각 상대 action chunk를 같은 잠정 SO-101
시작 pose에 붙여 MuJoCo IK와 실물 설정의 명령 속도·가속도 한계를 검사한다. 같은
표본의 기록된 정답 chunk도 동일 조건에서 함께 검사한다. 정답도 실패하면 등록·IK·
시간 조건 문제이고, 정답은 통과하지만 예측만 실패하면 학습 정책 문제다.
기하 판정에는 IK 잔차와 실물 관절 범위만 적용한다. 속도에서 환산한 step 제한은
기하 판정에 섞지 않고, 통과한 관절열의 timestamp 기반 속도·가속도로 따로 판정한다.

```bash
cd AI

python tools/preflight_relative_chunk_policy.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --policy-ckpt checkpoints/relative_chunk/umi_real_relative_v6_seed0.pt \
  --device cpu \
  --samples 200 \
  --out out/relative_chunk_v6_seed0_robot_preflight.json
```

등록값 `configs/real/umi_so101_registration_provisional.json`은 이전 등록 탐색에서
`위치 도달 + 가로 파지축 + 첫 화면 물체 포함`을 동시에 만족한 후보를 옮긴 것이다.
실측 robot-base↔workspace 캘리브레이션이 아니며 실물 명령에는 사용할 수 없다.
각 episode의 첫 표본만 잠정 시작 pose에 붙인다. 이후 표본의 현재 pose는 직전
기록 정답의 첫 미래 target을 누적해 복원하고, 연속 oracle IK로 현재 관절 seed를
만든다. v6의 인접 표본 1,178쌍에서 이 target과 다음 proprio의 역변환을 합성한
오차는 Frobenius norm 최대 `9.92e-9`로 확인했다. 모든 진행 단계 청크를 동일한
시작 pose에 붙이면 후반 동작을 시작점에서 반복하는 잘못된 검사가 된다.

이 검사는 여전히 물체 접촉, 폐루프 복구, 성공률을 평가하지 않는다. geometry와
commanded dynamics를 확인한 다음 카메라 조건을 맞춘 MuJoCo 진단 영상을 만든다.

## phase 정렬 사전검사 결과와 다음 등록 탐색

표본마다 기록 정답의 첫 미래 target을 누적해 현재 pose를 복원한 뒤 validation
episode 균형 표본 25개를 검사했다.

| 경로 | geometry 통과 | dynamics 통과 |
|---|---:|---:|
| 학습 정책 | 5/25 (20%) | 0/25 |
| 기록 정답 oracle | 4/25 (16%) | 0/25 |

두 경로 모두 현재 anchor IK 불가가 12/25였으므로 정책보다 단일 episode에서 고른
잠정 시작 등록값이 먼저 병목이다. dynamics도 양쪽 모두 0%였다. 기록 정답의 팔
속도는 최대 2.45rad/s, 가속도는 최대 9.10rad/s²로 실물 계약의 1.0rad/s와
3.0rad/s²를 넘었다. 따라서 이 결과를 근거로 재학습하지 않는다.

사전검사는 이제 geometry를 통과한 각 chunk에 대해 균일 시간 확대율도 기록한다.
시간을 `s`배 늘리면 속도는 `1/s`, 가속도는 `1/s²`가 되므로 다음 최댓값이다.

```text
max(
  arm_speed / arm_speed_limit,
  sqrt(arm_accel / arm_accel_limit),
  gap_speed / gap_speed_limit,
  sqrt(gap_accel / gap_accel_limit),
  1
)
```

이 값은 로봇 실행 스케줄에만 적용한다. v6의 원본 timestamp와 학습 label을 다시
늘리거나 재생성하지 않는다.

단일 시연 후보를 바로 교체하지 않고 validation episode 전체의 기록 정답을 먼저
통과시키는 탐색기를 추가했다. 목적함수는 sampled oracle chunk IK, 현재 anchor
도달률, 가로 jaw/수평 접근축, 초기 wrist camera 물체 포함, 테이블 배치와 MuJoCo
충돌 없음의 결합 통과율이다. 물체 위치는 각 시연의 near-closed pinch pose에서
추론한다. 결과는 계속 잠정 등록이며 실물 캘리브레이션이나 모터 안전 승인이 아니다.

정책 추론은 없지만 검증 작업이므로 **로컬 PC 전용**이다. 공유 GPU 서버에서는
실행하지 않는다. 먼저 32개 후보를 validation episode 3편으로 빠르게 거른 뒤
상위 4개만 전체 14편에서 평가한다.

```bash
python tools/search_relative_chunk_registration.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --preflight out/relative_chunk_v6_seed0_phase_aligned.json \
  --candidates 32 \
  --screen-episodes 3 \
  --finalists 4 \
  --samples-per-episode 2 \
  --out out/relative_chunk_v6_registration_search32.json
```

선택 후보를 정책과 기록 정답 양쪽에 다시 적용한다.

```bash
python tools/preflight_relative_chunk_policy.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --policy-ckpt checkpoints/relative_chunk/umi_real_relative_v6_seed0.pt \
  --registration out/relative_chunk_v6_registration_search32.json \
  --device cpu \
  --samples 200 \
  --out out/relative_chunk_v6_seed0_registration32_preflight.json
```

기록 정답 geometry가 충분히 통과하지 않으면 epoch를 추가하지 않는다. 후보 수를
늘려도 개선되지 않으면 시작 등록 하나의 문제가 아니라 5-DoF orientation 투영,
IK branch 또는 hand-eye 정의를 다시 검사한다. geometry 통과 후에는 보고된 시간
확대율로 실행 스케줄을 늦춘 다음, 대표 validation episode와 그 episode에서 추론한
물체 위치를 사용해 camera-matched MuJoCo 영상을 만든다.

## 0914 후속: canonical pinch v8과 비대칭 그리퍼 중점

v6의 카메라→마커중점 회전은 저장 영상의 OpenCV 축을 ARCore/OpenGL 축으로 바꿨지만,
그 결과의 child frame이 로봇에서 사용하는 canonical pinch frame과 같다는 검증이
없었다. 70개 시연의 폐쇄 시점 자세를 ARCore 중력축과 대조하니 v6의 child `+Z`가
세계 위쪽을 향했다. 손가락 접근축은 아래를 향해야 하므로, 로컬 `+X` jaw 축은
유지하고 `Y/Z`를 뒤집는 `diag(1,-1,-1)` basis change를 적용한 v8을 만들었다.

첫 번째 축 가설로 만들었던
`configs/real/umi_s22_canonical_pinch_provisional.json`은 중력 검증에서 탈락했으며
`REJECTED_AXIS_HYPOTHESIS`로 보존했다. v8은 다음 설정을 사용한다.

```text
configs/real/umi_s22_canonical_pinch_gravity_checked_provisional.json
dataset: datasets/umi_real_relative_20260911_v8
episodes: 70
rows: 1,248
contract problems: 0
```

v8을 처음 MuJoCo에 재생했을 때 물체 중심 궤적은 맞았지만 열린 고정 턱이 먼저
물체를 밀었다. 원인은 SO-101이 대칭 양방향 그리퍼가 아니라 고정 턱 하나와 가동 턱
하나를 갖는데도, 모든 gap에서 로컬 `[0,0,-0.080]`을 패드 중점으로 사용한 것이었다.
MuJoCo 패드 형상에서 고정 접촉면은 로컬 `x=-0.0075m`이고 턱은 `+x`로 벌어진다.
따라서 시뮬레이션 파지 중점을 다음처럼 정의했다.

```text
pinch_midpoint_local(gap) = [-0.0075, 0, -0.080] + gap/2 * [1, 0, 0]
```

이 값은 MuJoCo 패드 형상의 FK·IK·rollout 채점을 서로 일치시키기 위한
**시뮬레이션 기하값**이다. 실물 SO-101 모터/패드 캘리브레이션으로 사용하지 않는다.
gap 종속 중점은 wrist-roll 축에서 벗어나므로 roll 보정 뒤 위치가 움직인다. IK는
roll 보정과 고정-roll 위치·접근축 풀이를 번갈아 수행하고, 마지막 동일 pose에서
위치·축·roll 잔차를 다시 계산한다.

회귀검사는 다음과 같다.

```text
MuJoCo 3.3.7, 120 targets
pad midpoint x max error: 0.0245 mm
FK→IK max position error: 0.00007236 mm
FK→IK max rotation error: 0.00002366 deg
gap roundtrip max error: 6.939e-18 m
unreachable target: rejected
camera frame tests: 8/8 PASS
```

14개 validation 시연, 32개 시작 자세 후보 탐색 결과는
`out/relative_chunk_v8_registration_search32_dynamic_pinch.json`에 기록했다.
선택 후보의 top-down 조건은 14/14, 전체 scene 조건은 10/14, 표본 oracle
geometry는 9/27이었다. 이는 실물 base 등록이 아니라 MuJoCo 진단용 잠정값이다.

대표 시연 `rec_20260911_150833`을 기록 정답으로 재생한 결과:

```text
geometry error before final tail: none
closest dynamic-pinch distance: 9.3 mm
first jaw contact: cycle 7
final jaw contacts: 5
object lift: 28.0 mm
success threshold: not reached
final tail: 8개 중 도달 가능한 2개 실행, step 2 IK reject
```

보정 전에는 열린 한쪽 턱이 물체를 밀고 접촉을 잃었지만, 보정 후에는 양쪽 접촉을
유지한 채 물체를 28mm 들어 올렸다. 아직 성공 기준을 넘지 않았고 마지막 미래
waypoint가 5-DoF IK에서 탈락하므로 성공으로 보고하지 않는다.

v6 체크포인트는 v8과 이미지가 같아도 상대 pose target의 표현 frame이 다르므로
v8 정책 평가에 사용할 수 없다. v8은 별도 smoke 재학습 후 로컬 이미지 의존성 검사와
MuJoCo learned-policy 진단을 해야 한다. 당시에는 공유 GPU 서버에서 학습만
허용했다. 0915 합의 후에는 학습 정책을 불러오지 않는 recorded-oracle MuJoCo와
등록 탐색도 서버에서 허용하지만, 체크포인트 추론·probe·policy preflight는 계속
금지한다.

전달용 v8 묶음은 다음과 같다.

```text
file: datasets/umi_real_relative_20260911_v8.tar
bytes: 305116160
sha256: 363fd01dc18ab3df69bb2105803aae978b598baa982271ae4d2f16fb74101bef
tar entries: 142 (dataset.json 포함)
```

서버에서는 압축 해제 후 아래 smoke **학습만** 실행한다.

```bash
tar -xf datasets/umi_real_relative_20260911_v8.tar -C datasets

python tools/train_relative_chunk_bc.py \
  --data datasets/umi_real_relative_20260911_v8 \
  --device cuda \
  --epochs 3 \
  --batch-size 32 \
  --seed 0 \
  --out checkpoints/relative_chunk/umi_real_relative_v8_smoke.pt
```

## 0914 후속: 접촉 jaw yaw 조건을 포함한 등록 탐색

기존 top-down 조건은 jaw 축이 수평인지만 확인해 세계 `X/Y` 중 어느 방향으로
닫히는지는 구분하지 못했다. 물체의 MuJoCo footprint도 `39 x 39mm` 정사각형이라
접촉 물리만으로는 90도 차이가 드러나지 않는다. 첫 oracle 영상에서 세계 `+X`가
물체의 위·아래를 끼우는 방향으로 보이는 것을 확인했다. 실제 시연처럼 물체의 양옆을
끼우도록 접근축은 유지하고 jaw yaw를 90도 돌려 세계 `+Y`를 task-frame 기준으로
정했다. 접촉 시점 jaw 축의 부호 대칭 yaw 오차가 15도 이내인지 별도 조건으로
검사한다.

```text
desired_world_jaw_axis: [0, 1, 0]
jaw_yaw_tolerance_deg: 15
source: MuJoCo task-frame convention; 실물 robot-base/workspace yaw 실측값 아님
```

각 시연의 시작→near-closed 접촉 회전을 평균해 목표 접촉 회전에서 역산한 시작 회전을
IK 후보 생성에 사용했다. 32개 후보를 3편으로 선별하고 상위 4개를 validation 14편에
재평가한 결과는 다음 파일에 기록했다.

```text
result: out/relative_chunk_v8_registration_search32_dynamic_pinch_yaw_y.json
selected candidate: 17
combined geometry+scene: 5/27 = 18.5%
oracle geometry: 9/27 = 33.3%
all scene constraints: 7/14 = 50.0%
jaw yaw aligned: 13/14 = 92.9%
top-down: 14/14 = 100%
representative episode: rec_20260911_150833
representative contact jaw yaw error: 0.739 deg
```

대표 시연의 기록 정답 oracle을 같은 등록값으로 로컬 MuJoCo에서 재생했다.

```text
video: out/relative_chunk_v8_oracle_150833_dynamic_pinch_yaw_y.gif
report: out/relative_chunk_v8_oracle_150833_dynamic_pinch_yaw_y.json
simulation wrist-camera video: out/relative_chunk_v8_oracle_150833_dynamic_pinch_yaw_y_simcam.gif
comparison: out/relative_chunk_v8_oracle_yaw_90deg_compare.png
closest dynamic-pinch distance: 1.23 mm
first jaw contact: cycle 8
final jaw contacts: 4
object lift: 32.8 mm
final tail: 2 commands executed, step 2 IK reject
success threshold: not reached
```

영상과 수치상 대표 시연은 물체 중심에 도달해 양옆 방향으로 접촉한 뒤 물체를
32.8mm 들어 올렸다. 하지만 마지막 waypoint가 5-DoF IK에서 탈락했고 성공 기준에는
도달하지 않아 성공 rollout으로 보고하지 않는다. 또한 `+X` yaw는 실제 작업대에서
측정한 방향이 아니므로, 향후 실물 base 등록이 확보되면 해당 기준으로 다시 탐색해야
한다. 이 변경은 robot-side 등록·검증 조건만 바꾸며 v8 데이터셋과 tar의 내용·해시는
변경하지 않는다.

## 0914 후속: 실제 측면 접근 의미를 반영한 canonical pinch v9

실물 영상은 카메라 시점이 유지된 상태에서 세로로 선 과자 케이스가 점점 크게
보이고, 두 마커가 물체 몸통의 좌우에 남는다. 이는 위에서 내려오는 top-down 파지가
아니라 물체 몸통을 향해 수평으로 접근해 양옆을 잡는 동작이다.

v8 validation trajectory의 시작→near-closure 중앙 이동은
`[-0.2, +123.3, +4.1] mm`로, 실제 전진축이 v8 `+Y`에 들어 있었다. v8 child
frame을 `Rx(-90deg)`로 basis change해 다음 v9 canonical frame으로 고쳤다.

```text
+X: jaw opening
+Y: up
+Z: horizontal forward/approach
extrinsic: configs/real/umi_s22_canonical_pinch_side_grasp_provisional.json
dataset: datasets/umi_real_relative_20260911_v9
episodes: 70
rows: 1,248
contract problems: 0
```

등록 탐색도 top-down 판정을 제거하고 수평 접근, jaw 축 직교, 몸통 중앙 높이,
양쪽 pad 중심 포함, 초기 카메라 가시성, 충돌과 IK를 함께 검사하도록 변경했다.
32개 후보 탐색 결과는 다음과 같다.

```text
result: out/relative_chunk_v9_registration_search32_side_body_grasp.json
status: REJECTED_NO_VALID_REGISTRATION
selected candidate: 1
side-grasp direction: 13/14 = 92.9%
jaw yaw aligned: 13/14 = 92.9%
horizontal approach aligned: 13/14 = 92.9%
body mid-height aligned: 13/14 = 92.9%
all scene constraints: 2/14 = 14.3%
oracle geometry: 4/27 = 14.8%
combined geometry+scene: 0/27 = 0%
```

좌표계 해석과 파지 의미는 개선됐지만 장면 조건과 IK를 동시에 만족한 등록값은
아직 없다. 따라서 v9를 성공한 rollout이나 확정 robot-base 등록으로 보고하지
않는다. v9 계약과 8-step 실행 의미는 Track A/B 합의 및 `S15P21A103-171` 결과
전까지 잠정 상태다.

첫 v9 탐색은 데이터 child frame만 v9로 바꾸고 MuJoCo `cam_wrist`는 v8
top-down extrinsic을 유지한 채 실행한 결함이 있었다. 이 때문에 실제 시연에서는
물체가 보이는데 초기 가시성 판정은 2/14에 그쳤다. camera proxy를 동일한 v9
side-grasp extrinsic에서 다시 계산한 값으로 교체했다.

```text
cam_wrist pos: [0.0014, -0.07747884, 0.05422045]
cam_wrist x axis: [1, 0, 0]
cam_wrist y axis: [0, 0.96592583, 0.25881905]
```

또한 첫 결과에서 접촉 IK가 존재한 7편은 양쪽 pad 중심 정렬이 모두 통과했고
중심 높이 오차도 사실상 0이었다. `pad_centered=7/14`는 패드 형상 오차가 아니라
나머지 7편의 접촉 IK 부재가 false로 합산된 결과다. 이후 보고에서는 접촉 IK
가용률과 `접촉 IK가 있을 때의 pad 정렬률`을 분리한다.

## 0915 후속: 카메라 up 축, 테이블 범위, 대표 시연 및 접촉 preload 검증

평행 jaw는 축 부호를 뒤집어도 같은 접촉면을 만들지만 손목 카메라의 up 방향은
부호까지 보존해야 한다. 등록 탐색에 세계 up 목표 `[0, 0, 1]`과 15도 허용오차를
추가하고, 카메라 proxy도 v9 side-grasp extrinsic으로 통일했다. 또한 탐색기가
사용한 테이블 반폭 `0.4 x 0.4m`를 결과 계약에 기록하고 renderer가 같은 크기를
강제하도록 했다. 이전 renderer의 기본 반폭은 `0.3 x 0.3m`여서 탐색에서 유효했던
물체가 재생 시 테이블 가장자리 밖으로 나가는 불일치가 있었다.

대표 시연은 화면 중심 오차만 최소화하지 않고 `scene_constraints_ok`이면서 sampled
oracle chunk를 실제로 통과한 횟수를 우선하도록 선정 기준을 바꿨다. 32개 후보를
다시 탐색한 결과는 다음과 같다.

```text
result: out/relative_chunk_v9_registration_search32_side_body_grasp_cam_upfix_repselect.json
selected candidate: 6
representative episode: rec_20260911_150943
combined geometry+scene: 5/27 = 18.5%
oracle geometry: 10/27 = 37.0%
all scene constraints: 3/14 = 21.4%
jaw yaw aligned: 13/14 = 92.9%
horizontal approach aligned: 13/14 = 92.9%
camera up aligned: 14/14 = 100%
side-grasp direction: 13/14 = 92.9%
representative sampled oracle chunks: 2/2
```

선택된 등록값은 `configs/real/umi_so101_registration_provisional.json`에 반영했지만
상태는 계속 `PROVISIONAL_NOT_PHYSICAL_CALIBRATION`이다. 결합 통과율이 18.5%에
불과하고 실제 robot-base/workspace를 계측한 값도 아니므로 실물 명령에 사용하지
않는다.

기록 정답 oracle을 로컬 MuJoCo에서 재생하면 `rec_20260911_150943`은 물체 위에서
내려오는 인형뽑기 자세가 아니라, 세로로 선 과자 케이스의 양옆 몸통으로 수평 접근한다.
UMI gap은 aperture만 나타내고 접촉력은 기록하지 않기 때문에, 시뮬레이션 접촉 모델에
한정해 near-closed 구간에 1mm under-closure preload를 적용했다. 데이터셋과 학습
라벨은 변경하지 않았다.

```text
video: out/relative_chunk_v9_oracle_representative_preload1mm.gif
report: out/relative_chunk_v9_oracle_representative_preload1mm.json
mode: recorded_oracle (learned policy/checkpoint bypassed)
closest pinch XY: 4.18 mm
first contact: cycle 10
object lift: 55.82 mm
final jaw contacts: 2
success: true
simulation-only grip preload: 1 mm
```

장면 조건을 통과한 나머지 두 편도 같은 조건으로 재생했다. `150833`은 접촉과
16.19mm 리프트까지 갔지만 final tail의 두 번째 명령이 5-DoF IK에서 탈락했고,
`150948`은 접근·폐쇄 과정에서 물체를 먼저 밀어 넘어뜨렸다. `150948`은 preload를
2mm로 늘리거나 실행 시간 여유를 2배로 늘려도 실패가 재현됐다. 즉 현재 동적 oracle
성공은 1/3이며, 1편의 성공은 좌표계·측면 접근·접촉 가능성을 확인한 진단 결과일 뿐
등록의 에피소드 일반화나 학습 정책 성공을 증명하지 않는다. 다음 단계는 실패
에피소드의 접촉 후 상대 궤적과 5-DoF IK 오차를 분리하고, 등록 후보를 최종 확정하기
전에 다중 에피소드 동적 oracle 게이트를 적용하는 것이다.

이를 반복 가능한 로컬 검사로 만들기 위해 `tools/gate_relative_chunk_oracle.py`를
추가했다. 등록 탐색 결과의 `scene_constraints_ok=true` 에피소드를 자동 선택하고,
렌더링 없이 기록 정답만 재생해 접촉·최대/최소 리프트·IK 오류·전도를 집계한다.
기본 합격선은 80%이며 학습 정책을 로드하지 않는다.

```bash
python tools/gate_relative_chunk_oracle.py \
  --data datasets/umi_real_relative_20260911_v9 \
  --registration out/relative_chunk_v9_registration_search32_side_body_grasp_cam_upfix_repselect.json \
  --cycles 30 \
  --grip-preload-m 0.001 \
  --min-success-fraction 0.8 \
  --out out/relative_chunk_v9_dynamic_oracle_gate_scene_valid_v2.json
```

```text
status: FAIL_LOCAL_DYNAMIC_ORACLE_GATE
contacts: 3/3
successes: 1/3 = 33.3% (required 80%)
150833: ik_reject_after_contact, max lift 20.18mm
150943: success, max lift 68.36mm
150948: object_displaced_or_tipped, max lift 55.07mm then final -18.85mm

## 0915 후속: 동적 실패를 후보 선택에 반영한 64개/33개 재탐색

기존 등록 탐색은 정적 기하·카메라·장면 조건으로 finalist를 고른 뒤 동적 oracle
gate를 별도로 실행했다. 이 구조에서는 `rec_20260911_150833`의 접촉 후 IK 거부와
`rec_20260911_150948`의 전도/이탈이 후보 순위에 전혀 반영되지 않았다.

`tools/search_relative_chunk_registration.py`에 다음 최종 단계를 추가했다.

- 모든 finalist를 동일한 고정 challenge episode에서 비교한다.
- `ik_reject_after_contact`, `object_displaced_or_tipped`,
  `static_scene_reject`, `runner_error`를 서로 다른 실패로 기록한다.
- 동적 성공률을 최우선으로 하고 각 실패 수와 접촉·최대 lift를 차례로 비교한다.
- 동적 성공률이 기준보다 낮으면 결과 상태를
  `REJECTED_DYNAMIC_ORACLE_GATE`로 기록한다.
- 고정 challenge episode는 값이 쉬운 편만 scene-valid가 되도록 후보가 유리하게
  바뀌는 것을 막기 위해 1차 screen에도 반드시 포함한다.

첫 64개 재탐색은 기존과 같은 7개 측면 IK 후보와 무작위/로컬 후보를 늘린
실험이었다.

```text
result: out/relative_chunk_v9_registration_search64_dynamic_challenge3.json
selected: candidate 6 (기존 선택과 동일)
dynamic: 1/3 = 33.3%, required 80%
150833: ik_reject_after_contact
150943: success
150948: object_displaced_or_tipped
status: REJECTED_DYNAMIC_ORACLE_GATE
```

후보 수만 늘려서는 개선되지 않았다. 실패 후보 대부분은 물체 몸통 중앙과 패드
중앙 높이를 함께 만족하지 못했다. 이 과정에서 `_candidate_starts()`가 소스 등록을
8% 탐색 margin 안으로 clip하면서, 로그와 달리 기존 성공 자세를 정확히 보존하지
않는 버그도 찾았다. 소스 등록은 real joint limit 안이면 그대로 보존하고, 국소
refinement도 broad-search margin을 적용하지 않도록 수정했다. 고정 도전 편을
screen에 포함하고 원본 등록을 finalist에 항상 남기는 회귀 조건도 추가했다.

정확한 소스 등록 주변에 32개 작은 관절 perturbation을 만든 총 33개 재탐색 결과는
다음과 같다.

```text
result: out/relative_chunk_v9_registration_search33_source_refined_dynamic_challenge3.json
base candidates: 1 exact source registration
refined candidates: 32, sigma_fraction=0.003
selected: candidate 4
start arm: [0.02371975, 0.72752479, 0.78653879, -1.50176976, 1.61533093]
static combined: 25.9% (기존 18.5%)
static scene: 28.6% (기존 21.4%)
side grasp: 92.9%
dynamic: 1/3 = 33.3%, required 80%
status: REJECTED_DYNAMIC_ORACLE_GATE
```

정적 최고 후보는 combined 37.0%, scene 42.9%까지 개선됐지만 동적 결과는 0/3으로
오히려 나빴다. 동적 순위를 적용한 candidate 4와 기존 기준선 candidate 0은 모두
1/3이었다. 즉 시작 등록의 작은 보정은 정적 도달 범위를 넓혔지만 두 동적 실패를
제거하지 못했다.

`150833` 상세 진단에서는 cycle 14까지 접촉 5개와 lift 14.44mm를 유지한 뒤 다음
목표에서 아래 결과로 중단됐다.

```text
solver converged: true
solver stayed within limits: false
position error: 4.66591mm / external budget 5mm
axis error: 0.863904deg / external budget 5deg
roll error: 0deg / external budget 5deg
```

외부 실행 예산 안에서 수렴했지만 solver 반복 중 관절 한계를 한 번 건드려
`within_limits=false`가 된 경우다. 기존 오류 메시지가 이를 `converged=false`로
합쳐 표시하던 문제를 수정해 두 조건을 분리했다. 이는 허용치를 바로 완화할 근거가
아니며, 관절 한계를 건드리지 않는 등록 또는 IK branch가 필요하다는 결과다.

`150948`은 cycle 13까지 접촉 6개와 lift 9.89mm를 유지한다. 마지막 청크의 step 0
까지는 접촉 3개로 약 50mm를 들지만 step 1에서 상대 이동 목표가 약 39mm에서
80mm로 커지는 순간 접촉을 모두 잃고 89.5도로 넘어졌다. 전체 실행 중 최대 수평
이동은 58.80mm, 최대 기울기는 106.44도다. 실행을 2배 느리게 하고 preload를
2~3mm로 늘려도 step 0에서 접촉을 더 일찍 잃었다. 따라서 단순 속도 또는 preload
보정으로 해결되지 않는다.

따라서 새 등록값도 승인하지 않는다. 다음 단계는 `150833`에서 solver가 관절 한계를
건드리지 않는 IK branch를 찾고, `150948`의 마지막 상승 청크가 물체를 유지할 수
있는지 접촉 모델과 궤적 실행을 분리 검증하는 것이다.

따라서 현재 등록값은 대표 영상 생성에는 쓸 수 있지만 학습 정책 rollout의 기준
등록값으로 확정할 수 없다. 다음 등록 후보 탐색에서는 kinematic 순위 뒤에 이 동적
게이트를 적용하고, 80%를 넘는 후보가 없으면 등록을 실패로 유지한다.

## 0915 후속: 실패 다중 집계와 넓은 33개 재탐색

물체 위치뿐 아니라 회전행렬을 매 simulation tick에서 읽어 초기 자세 대비 물체
기울기와 수평 이동을 기록했다. 30도 이상 기울면 `object_tipped`, 물체 폭 절반
이상 수평 이동하고 최종 jaw 접촉을 잃으면 `object_displaced`로 판정한다. 성공은
여전히 최우선 판정이라, 정상적으로 물체를 든 채 옮긴 이동은 실패로 바뀌지 않는다.

실패는 배타적이지 않다. 예를 들어 넓은 탐색의 candidate 16에서 `150833`은 IK
거절과 전도가 함께 발생했다. 따라서 단일 `classification_counts`만으로 순위를
매기지 않고 `failure_counts`에서 접촉 후 IK 거절·전도·이탈을 각각 모두 센다.

기존 등록 주변을 더 넓게 총 33개로 다시 탐색했다.

```text
result: out/relative_chunk_v9_registration_search33_failure_aware_wide.json
base candidates: 1 exact source registration
refined candidates: 32, sigma_fraction=0.01
static best screen combined: 57.1%
dynamic challenge episodes: 150833, 150943, 150948
best dynamic success: 1/3 = 33.3%, required 80%
status: REJECTED_DYNAMIC_ORACLE_GATE
```

검색 실행 당시의 배타적 분류 순위는 candidate 16을 선택했지만, 같은 결과를 수정된
다중 실패 순위로 다시 계산하면 기존 source candidate 0이 가장 낫다. candidate 16은
성공 수는 같지만 전도·이탈이 각각 2건이라 과대평가됐기 때문이다. 즉 넓은 시작 자세
보정도 기존 등록보다 동적 결과를 개선하지 못했으며 새 등록값은 승인하지 않는다.

수정된 집계로 source candidate 0을 다시 실행한 정본 진단은
`out/relative_chunk_v9_source_candidate_dynamic_gate_multifailure_v2.json`이다.
결과는 성공 1/3, 접촉 후 IK 거절 1건, 전도 1건, 이탈 1건이다. 전도와 이탈은 같은
`150948`에서 동시에 발생했기 때문에 성공·실패 분류 합계와 다중 실패 합계는 서로
다를 수 있다.

관련 회귀검사는 다음을 보장한다.

- 소스 등록이 탐색 margin으로 몰래 clip되지 않는다.
- 관절 한계 접촉과 solver 수렴을 별도 원인으로 보고한다.
- 한 에피소드에서 IK 거절과 전도가 동시에 발생해도 두 실패를 모두 집계한다.
- 다중 실패가 더 많은 후보는 정적 점수가 높아도 동적 순위에서 뒤로 간다.

## 0915 후속: 접근 접촉과 waypoint 시간 확대 실행 검증

기존 정적 탐색은 near-closed 한 시점만 검사해 접근 중 접촉을 보지 않았다. 10Hz
관절 목표 사이를 10개로 보간해 물체와 jaw의 첫 정적 접촉을 계측했다. 기존 source
등록에서 세 도전 편 모두 첫 접촉의 pinch 수평 오차는 1.5~3.1mm로 작았지만,
접촉점은 물체 접근축 방향 중앙이 아니라 앞 모서리에서 약 19mm 떨어진 곳이었다.
성공 편 `150943`에도 같은 모서리 접촉이 있어 이 값만으로 `150948` 전도를
분류할 수 없었다. 따라서 첫 접촉 수치는 진단으로 기록하되 후보 탈락 조건이나
순위에는 사용하지 않는다.

미실측 marker-midpoint→pad-centre 깊이 오프셋 가능성을 확인하려고 물체 중심을
접근축으로 -19mm와 -30mm 이동한 정적 검사도 수행했다. -19mm에서는 세 편 모두
여전히 앞 모서리에 닿았고, -30mm에서는 한 편만 몸통 중앙 접촉 조건을 만족했다.
단일 오프셋이 세 편을 일관되게 개선하지 않았으므로 새 오프셋이나 등록값은
채택하지 않는다.

동적 실행기에서는 더 중요한 오류를 찾았다. `uniform_time_schedule`로 waypoint
시간을 늘려도 최종 관절 목표를 즉시 명령하고 같은 목표를 여러 simulation tick 동안
반복하고 있었다. 이는 시간 확대가 아니라 목표 도달 대기다. 수정 후에는 각 예정된
tick마다 팔 관절과 gap 목표를 선형 보간해 실제 명령 속도를 낮춘다. 보고서에는
`simulation_waypoint_execution`과 각 명령의 target/actual pinch pose도 기록한다.

수정된 source candidate 0의 세 편 결과는 다음과 같다.

```text
report: out/relative_chunk_v9_source_candidate_dynamic_gate_interpolated.json
tracking margin: 1.0
preload: 1mm
150833: ik_reject_after_contact
150943: success
150948: object_tipped + object_displaced
success: 1/3, required 80%
```

`150948`의 최대 수평 이탈은 기존 71.23mm에서 37.68mm로 줄었지만 전도는 남았다.
tracking margin 2.0은 접촉 상태의 옆힘이 더 오래 작용해 `150943`까지 전도시켰고
0/3이었다. margin 1.0에서 preload를 0으로 바꿔도 1/3으로 동일했다. 따라서 현재
근거로는 감속 배율이나 preload를 추가 보정값으로 채택하지 않는다.

목표 pinch 궤적을 직접 기록한 결과 `150948`은 접촉을 유지한 cycle 13 이후 다음
목표가 물체 초기 위치 대비 z=9.7mm에서 48.9mm로 한 번에 증가했고, 그 구간에서
접촉을 잃었다. 성공 편 `150943`은 같은 구간에서 z=0.6mm에서 21.3mm로 먼저
이동한 뒤 다음 step에서 60.4mm로 올라갔다. 즉 전체 궤적 최대 이동량만 볼 것이
아니라, 하중을 잡은 직후의 waypoint 간 상승 변화와 접촉 유지성을 함께 검사해야
한다. 현재 동적 게이트는 여전히 실패이며 새 등록값을 승인하지 않는다.

## 0915 후속: ver1 phone-holder handoff와 재개 가능한 등록 탐색

HW handoff archive SHA-256은
`5386a22ea54729215cb544b844079621150a1ff4d751b54daf9f031a9c077d8b`이며,
패키지 내부 40개 파일의 SHA-256을 모두 대조했다. 최신 URDF의
`wrist_roll -> custom_hand -> gripper_tcp` 고정 변환을 합성하면 TCP 원점은
기존 MuJoCo gripper body 기준 약 `(0.00000021, -0.00000056, -0.15811882)m`이다.
턱축은 약 `(-0.04866, 0.99882, 0)`, 접근축은 약 `(0, 0, -1)`이다.

기존 MuJoCo 설정은 pinch가 `-0.080m`, 턱축이 body `+X`인 비대칭 구형
그리퍼였다. 새 ver1은 대칭 평행 턱이며 턱축이 거의 body `+Y`이므로, 기존
등록 탐색은 실제 장착 그리퍼보다 파지점이 약 78mm 짧고 턱 방향이 약 90도
다른 모델을 사용했다. 이 차이를
`configs/real/so101_ver1_phone_holder_kinematic.json`의 기구학 전용 overlay로
분리했다. overlay FK↔IK 왕복검사 12개 목표에서 미수렴 0건, 위치 오차 중앙
0.0088mm, 최대 0.0098mm였다. 이 검사는 좌표계·부호의 자기일관성만 확인하며
실행 가능성이나 동역학을 승인하지 않는다.

등록 탐색은 후보별로 `screen/refine/final/dynamic` 결과를 progress JSON에
원자적으로 저장한다. 데이터, 설정, scene, 코드와 후보 목록 해시가 모두 같은
경우에만 `--resume`을 허용한다. 16개 광역 후보와 상위 2개 주변 16개 국소 후보를
기존과 같은 14개 validation episode에서 검사한 결과는 다음과 같다.

```text
result: out/relative_chunk_v9_ver1_kinematic_registration_search32_refined_val14_20260915.json
progress: out/relative_chunk_v9_ver1_kinematic_registration_search32_refined_val14_20260915.progress.json
selected candidate: 1
side grasp: 13/14 = 92.9%
kinematic scene constraints: 8/14 = 57.1%
sampled chunk geometry: 16/27 = 59.3%
combined: 11/27 = 40.7%
status: KINEMATIC_REGISTRATION_CANDIDATE_NOT_DYNAMIC_READY
```

이 실행 당시 첫 화면 가시성은 기존 임시 `cam_wrist` proxy로 계산됐다. handoff에는
실제 렌즈 optical transform이 없으므로 이 카메라 수치는 ver1 holder 검증으로
인용하지 않는다. 이후 코드는 optical extrinsic이 없는 기구학 overlay에서 카메라
조건을 합격 기준에서 제외하고 결과 상태를
`KINEMATIC_REGISTRATION_CANDIDATE_CAMERA_AND_DYNAMICS_UNVERIFIED`로 제한한다.

또한 handoff에는 검증된 custom inertia, collision-safe limit와 동역학용 ver1
collision model이 없다. 따라서 구형 MuJoCo 패드의 접촉·전도 결과를 새 ver1
그리퍼 결과로 재사용하지 않는다. 다음 필수 입력은 (1) 렌즈 optical frame의
실장착 변환과 (2) 단순화해도 실제 접촉면 위치·대칭 개폐를 보존한 ver1 collision
model이다. 이 둘 전에는 추가 learned-policy rollout보다 기구학 후보와 데이터
계약 검증만 유효하다.

## 0915 후속: 물체 외곽 여유와 대표 시연 선택 보정

첫 ver1 기구학 영상에서 대표 시연 `150948`의 물체가 테이블 끝에 걸쳐 보였다.
수치를 확인하면 테이블 X 끝은 `0.55m`, 물체 바깥 면은 `0.5459m`로 실제 남은
여유가 약 `4.1mm`뿐이었다. 기존 검사는 물체 중심에 고정 `20mm` margin만 적용해
물체 크기를 명시적으로 빼지 않았고, 실제 optical extrinsic이 없는데도 임시
카메라 중앙 점수를 대표 시연 선택에 사용했다.

등록 탐색을 다음과 같이 수정했다.

- `table_half - abs(object_center - table_center) - object_half_size`로 물체 전체
  외곽부터 각 테이블 끝까지의 여유를 계산한다.
- 기본 `--object-edge-margin-m 0.03`으로 모든 축에서 최소 30mm를 요구한다.
- 후보 동률 시 테이블 중심 정규화 거리를 사용한다.
- optical extrinsic이 없으면 대표 시연 선택에서 임시 카메라 중앙 점수를 제외한다.
- 후보별 X/Y 여유, 최소 여유, 중심 거리를 결과 JSON에 보존한다.

16개 광역 후보와 16개 국소 후보를 빠른 1편 screen 후 같은 14개 validation
episode에서 상위 4개를 재검증했다.

```text
result: out/relative_chunk_v9_ver1_registration_search32_margin30mm_val14_20260915.json
progress: out/relative_chunk_v9_ver1_registration_search32_margin30mm_val14_20260915.progress.json
selected candidate: 1
table margin pass: 6/14 = 42.9%
full kinematic scene pass: 5/14 = 35.7%
side grasp: 13/14 = 92.9%
sampled chunk geometry: 16/27 = 59.3%
combined: 6/27 = 22.2%
representative episode: rec_20260911_150034
representative object xyz: (0.40189, -0.00453, 0.03835)m
representative minimum table-edge clearance: 128.6mm
status: KINEMATIC_REGISTRATION_CANDIDATE_CAMERA_AND_DYNAMICS_UNVERIFIED
```

새 대표 영상은
`out/relative_chunk_v9_ver1_candidate1_150034_margin30mm_kinematic.gif`이다.
15개 행짜리 기존 `150948` 대신 4개 행짜리 `150034`가 선택돼 동작 길이는 짧지만,
물체가 테이블 안쪽에 놓이고 ver1 턱이 몸통 측면을 향하는 것을 확인했다. 정책
추론과 접촉 동역학은 실행하지 않았다. 전체 UMI 회귀검사는 `25/25 PASS`다.

이 보정은 대표 영상의 가장자리 문제를 해결했지만 등록을 확정하지 않는다. 같은
후보에서 30mm 테이블 여유를 만족한 편은 6/14뿐이고, ver1 collision과 optical
extrinsic도 여전히 없다. 따라서 다음 판단은 더 많은 학습이 아니라 실제 ver1
collision proxy를 추가한 recorded-oracle과, 실패 편을 포함할 수 있는 작업대/로봇
base 등록 자유도의 재검토다.

## 0915 후속: ver1 잠정 양측 collision proxy와 recorded-oracle 접촉 분리

handoff에는 동역학용 collision geometry가 없으므로 실물 검증을 주장하지 않는
진단 전용 대칭 평행 턱 proxy를 추가했다. 두 패드는 ver1 기구학 overlay의 턱축과
접근축을 사용하고, 매 MuJoCo substep 전에 현재 gripper qpos를 실측 gap curve로
변환해 안쪽 면 간격을 동기화한다. 구형 rotary jaw collision은 동시에 비활성화한다.
박스 반크기 `(5, 12, 40)mm`, 마찰과 물체 폭 39mm는 모두 잠정값이다. 설정에는
`measured_collision_geometry=false`, `dynamic_mujoco_ready=false`를 유지한다.

기존의 턱-물체 접촉 총합은 한 손가락이 물체를 미는 상태와 양측 파지를 구별하지
못했다. 따라서 보고서에 패드별 접촉 수, 최초 양측 접촉 cycle, 양측 접촉 tick 수와
최대 동시 접촉 패드 수를 추가했다. 잠정 proxy에서는 lift 임계값뿐 아니라 양쪽
패드가 동시에 접촉해야 `MujocoPickEnv.is_success()`가 참이다. 별도로
`stable_side_grasp_success`는 최대 기울기 30도와 물체 크기 기반 수평 이탈 임계값도
넘지 않은 경우만 참이다.

학습 정책을 불러오지 않고 v9 recorded target만 실행해 challenge 3편을 preload
`0/0.5/1/2mm`로 비교했다. preload는 gap이 55mm 이하일 때 시뮬레이터 명령만 더
닫으며 원본 라벨과 데이터셋은 바꾸지 않는다.

| episode | preload | 양측 접촉 tick | 최대/최종 상승 | 최대 XY 이탈 | 최대 기울기 | 결과 |
|---|---:|---:|---:|---:|---:|---|
| 150833 | 0mm | 0 | 0.93/0.93mm | 3.30mm | 2.82도 | 한쪽 접촉 뒤 IK 한계 |
| 150833 | 2mm | 7 | 2.09/2.09mm | 3.18mm | 2.28도 | 접촉 후 IK 한계, lift 실패 |
| 150943 | 0mm | 0 | 0.10/0.00mm | 0.40mm | 0.25도 | 양측 파지 없음 |
| 150943 | 1mm | 22 | 60.14/60.14mm | 9.01mm | 40.84도 | lift했으나 순간 전도 |
| 150943 | 2mm | 21 | 59.19/59.19mm | 4.56mm | 0.78도 | 잠정 안정 측면 파지 |
| 150948 | 0mm | 0 | 0.79/0.00mm | 5.79mm | 2.89도 | 양측 파지 없음 |
| 150948 | 1mm | 34 | 62.34/62.34mm | 11.12mm | 43.77도 | lift했으나 순간 전도 |
| 150948 | 2mm | 37 | 81.78/81.78mm | 12.51mm | 5.83도 | 잠정 안정 측면 파지 |

이 결과로 실패 원인을 하나로 묶지 않는다. `150943/150948`은 2mm 진입 깊이에서
양측 접촉과 lift가 성립하므로 무보정 실패에는 접촉 여유가 지배적이다. 반면
`150833`은 2mm에서도 접촉 직후 다음 목표가 관절 한계를 벗어나므로 5-DoF IK/등록
한계가 별도 원인이다. 확인 영상은
`out/ver1_proxy_rec_20260911_150948_preload2mm_oracle.gif`이다.

2mm preload를 실물 보정값이나 최종 시뮬레이션 상수로 승인하지 않는다. 실측
손가락 collision 형상, 과자 케이스 몸통 폭과 마찰, 카메라 optical extrinsic이
없기 때문이다. 현재 결론은 (1) ver1 좌우 몸통 파지를 접촉 모델로 재현할 수 있고,
(2) 등록 탐색은 접촉 이후 전체 궤적 IK 통과율을 별도로 높여야 한다는 진단까지다.

### 측면 후보 우선순위와 접촉 후 IK 국소 보정

잠정 proxy를 포함한 첫 32개 재탐색에서는 cheap screen의 완전 장면 통과율이 모든
후보에서 0%였다. 이때 기존 정적 순위가 테이블 중앙 배치를 측면 파지축보다 먼저
비교해, 측면 파지 0%인 candidate 4를 첫 finalist로 골랐다. 해당 장시간 실행은
후보별 progress를 보존한 뒤 중단했다. 순위를 `완전 통과 -> 측면 task frame 4조건
-> 최초 몸통 접촉 중심 -> 테이블/IK 부분 지표` 순으로 고쳤으며, zero/zero 동률에서
중앙 top-down 후보보다 측면 후보가 앞서는 회귀검사를 추가했다.

측면 후보의 물체 X 등록은 테이블 외곽 여유가 약 14mm라 30mm 규칙을 통과하지
못했다. 상대 궤적과 데이터 라벨은 그대로 두고 `object_approach_offset_m`만
`-20mm`, `-30mm`로 비교했다. `-20mm`에서는 `150943` 한 편만 장면을 통과해
양측 접촉 21tick, 최종 상승 59.05mm, 최대 기울기 1.94도로 성공했다. 이 과정에서
동적 집계기의 상대 출력 경로가 자식 프로세스 cwd에서 `AI/AI/out`로 해석되어 완료
JSON을 `runner_error`로 오인하는 Windows 경로 버그도 발견해 절대경로로 수정했다.

`-30mm`에서는 challenge 3편 모두 30mm 테이블 여유와 측면 장면 제약을 통과했다.
기본 측면 후보는 `150943/150948`만 성공하고 `150833`이 접촉 후 관절 한계로
실패해 2/3이었다. 이 후보 주변 시작 관절을 joint range의 1% 표준편차로 8개
국소 탐색한 결과 candidate 3이 3/3을 통과했다.

```text
result: out/relative_chunk_v9_ver1_proxy_side_offset_minus30mm_refine8_dynamic3_20260915.json
selected candidate: 3
start arm rad: (0.0495351, 0.702779, 0.834085, -1.513346, 0.0403620)
object xyz: (0.479759, -0.024126, 0.038350)m
static scene: 3/3
sampled geometry: 5/6 = 83.3%
dynamic stable side grasp: 3/3
status: DIAGNOSTIC_COLLISION_PROXY_CANDIDATE_NOT_PHYSICAL_VALIDATION

150833: lift 80.53mm, bilateral 47tick, max tilt 5.61deg
150943: lift 58.72mm, bilateral 21tick, max tilt 2.74deg
150948: lift 84.44mm, bilateral 32tick, max tilt 3.16deg
```

`150833`은 물체를 들어 올려 양측 접촉을 유지한 뒤 final tail의 step 4에서 위치
오차 5.81mm로 5mm IK 기준을 넘었다. 현재 pick-only 게이트는 이미 안정 lift가
성립한 뒤의 이 후속 reject보다 성공을 우선하지만, 전체 8-step 실행가능성이
100%라는 뜻은 아니다. 확인 영상은
`out/ver1_proxy_candidate3_rec_20260911_150833_preload2mm_oracle.gif`이다.

이 결과는 challenge 3편에서 실패 원인을 분리하고 잠정 등록 후보를 찾은 것이다.
아직 14편 전체 gate, 실측 collision/물체 폭·마찰, optical extrinsic과 실물 명령을
검증하지 않았으므로 학습 정책 성공률이나 물리 재현 성공으로 인용하지 않는다.

### 정정: 구형 외형과 잠정 collision의 불일치

위 `dynamic stable side grasp: 3/3` 결과를 물리·시각 파지 성공으로 해석한 것은
잘못이었다. 당시 씬은 기존 SO-101 회전식 집게 메시를 계속 표시하면서, 접촉은 그
외형과 분리된 두 개의 ver1 잠정 박스가 만들었다. 따라서 영상의 손가락이 과자
케이스 몸통을 양옆에서 잡지 않아도 보이지 않는 박스가 물체를 들어 올릴 수 있었다.
위 3/3은 reporter와 접촉 proxy 실행 경로의 진단값으로만 남기며 파지 성공 근거로
사용하지 않는다.

이를 막기 위해 `handoff.zip/model/assets/ver1`의 좌우 holder/finger와 hand 본체
메시를 씬에 추가하고 구형 rotary jaw 외형을 숨겼다. 각 잠정 collision pad는
해당 좌우 finger visual과 **같은 moving body**에 부착했으며, gap 변경 시 body를
이동시키도록 변경했다. visible-hand 선언이 없는 예전 registration/payload는 이제
fail-closed로 중단한다. 회귀검사는 다음을 확인한다.

- 구형 집게 visual/collision 네 geom의 alpha 및 contact가 비활성화됨
- 좌우 pad가 각각 가시 finger mesh 두 개와 같은 body에 속함
- 손가락 안쪽 면 간격이 5.3/39/79.4mm 명령을 그대로 추종함
- 두 pad 중심의 중점이 gap과 무관하게 유지됨

수정 후 `rec_20260911_150833`을 같은 candidate 3, preload 2mm로 다시 렌더했다.
`out/ver1_visible_parallel_candidate3_150833_oracle.gif`에서 실제 ver1 좌우 손가락이
세로로 선 물체의 몸통 양옆에 위치하고, 접촉과 함께 닫히며 들어 올리는 것을 확인했다.
이 1편은 visual-collision 불일치를 제거했다는 확인일 뿐이다. proxy 크기·마찰은
여전히 미실측이고 전체 challenge 3편을 새 visible-hand 필수 계약으로 다시 gate하지
않았으므로, 앞의 3/3 수치는 계속 철회 상태로 둔다.

### 0916 정정: 하드웨어 패키지 fitted contact patch 반영

`handoff_20260916.zip`의 전체 SO-101/ver1 모델을 현재 overlay와 대조했다. TCP
고정변환, 양 손가락의 `+-X` 이동축, `0~90mm` 전체 gap과 현재 8개 주요 OBJ는
일치한다. 따라서 전체 URDF를 교체하지 않고, 접촉 형상만 패키지의 진단용 fitted
box에 맞췄다.

패키지 `handoff/simulation/robot.py`의 손가락 body-frame 값은 중심
`(+-3,-65,27)mm`, 반크기 `(3,13,7.7)mm`, 마찰 `(1.2,0.01,0.001)`이다.
현재 builder는 pad의 두 번째·세 번째 축을 바꾸는 local frame을 사용하므로 설정값은
동일한 물리 형상을 나타내는 `(3,7.7,13)mm`다. archive SHA-256은
`fd7e49fe1680bef55e21ab97be71293e22f19a34abef731df0529508971c2ac2`이다.

이 box도 실측 collision geometry가 아니다. 하드웨어 패키지 자체가 fitted box라고
명시할 뿐 측정 provenance를 제공하지 않으므로 `measured_collision_geometry=false`,
`dynamic_mujoco_ready=false`를 유지한다. 기존 `(5,12,40)mm` box는 손가락 대부분을
덮어 접촉을 과대평가할 수 있었으므로, 그 형상으로 얻은 동적 recorded-oracle 성공
수치는 모두 철회하고 challenge 3편부터 다시 측정한다.

새 fitted patch와 현재 visible-hand payload를 aggregate gate까지 명시적으로 전달해
candidate 3을 다시 검사했다. 먼저 challenge 3편은 모두 양측 접촉과 안정 lift를
통과했다. 이는 정책 성능이 아니라 recorded target replay 결과다. `150833`은 lift
뒤 final tail에서 위치 오차 `5.81mm > 5mm`가 남으므로 전체 궤적 완전 통과로
해석하지 않는다.

같은 시작 자세와 `object_approach_offset=-30mm`를 바꾸지 않고 기존 validation
14편으로 확장한 결과는 다음과 같다.

```text
result: out/relative_chunk_v9_ver1_fitted_patch_candidate3_val14_retry_20260916.json
side-grasp axes: 13/14
table edge margin: 10/14
contact IK available: 10/14
fitted-pad centred: 4/14
all static scene constraints: 4/14
dynamic recorded-oracle: 4/14 success, 10 static-scene reject
tip/displacement among dynamically replayed episodes: 0/4
status: REJECTED_DYNAMIC_ORACLE_GATE
```

동적으로 실행 가능한 4편은 `145912`, `150926`, `150943`, `150948`이며 모두
양측 접촉 후 `55.7~99.6mm` lift를 보였다. 반면 실패 편 다수의 최초 접촉
pinch-centre 오차는 약 `22~31mm`로 8mm 기준을 넘었다. 따라서 방향 오류가 다시
발생한 것이 아니라, 큰 proxy에서 찾은 `-30mm` 접근축 오프셋과 단일 base/world
평행이동이 작은 tip patch 및 전체 시연 분포에 맞지 않는 것이 현재 지배적 병목이다.
다음 탐색은 fitted patch를 고정한 채 접근축 오프셋과 base/world 평행이동을 다시
최적화해야 한다.

### 0916 후속: fitted patch 오프셋 분리와 Cartesian 등록 재탐색

같은 candidate 3과 validation 14편을 고정하고
`object_approach_offset_m`만 비교했다. 이 값은 로봇/월드 등록을 대신하는 임의의
물체 이동으로 사용하면 안 되므로, 각 실행에서는 전체 recorded trajectory의 시작
자세를 동일하게 유지했다.

| 접근축 오프셋 | 전체 정적 장면 | 최초 접촉 몸통 깊이 중심 | 테이블 여유 | contact IK | pad 중심 |
|---:|---:|---:|---:|---:|---:|
| -30mm | 4/14 | 4/14 | 10/14 | 10/14 | 4/14 |
| -20mm | 3/14 | 7/14 | 8/14 | 10/14 | 4/14 |
| -10mm | 2/14 | 3/14 | 7/14 | 10/14 | 4/14 |
| 0mm | 1/14 | 1/14 | 7/14 | 10/14 | 4/14 |

`-20mm`는 접촉점의 몸통 깊이 정렬을 가장 많이 살렸지만 전체 장면 통과율은
낮아졌다. 특히 pad 중심 높이와 contact IK는 오프셋으로 개선되지 않았다. 따라서
접근축 오프셋 하나로 등록 실패를 덮는 방법은 기각했다.

기존 후보 생성기는 32개 중 측면 자세를 직접 만족하는 Cartesian IK 후보가 2개뿐이고
나머지를 관절공간 로컬/무작위 값으로 채웠다. 기존의 유효한 측면 회전을 고정한 채
시작 TCP를 X/Y/Z로 평행이동하는 후보를 먼저 푸는 경로를 추가했다. 단위검사에서는
후보 순서와 회전 보존을 확인했고, 실제 모델에서는 측면 Cartesian 후보가 2개에서
8개로 늘었다.

같은 `-20mm`, 같은 validation 14편으로 32개를 다시 검색한 결과 새 후보는 기존
시작값을 넘지 못했다.

```text
result: out/relative_chunk_v9_fitted_patch_cartesian_offset_minus20mm_search32_static14_20260916.json
sha256: 5aed6e8c51e97ca70c577acfee69ab33c28595c1cb3748a27bc9ca8353666326
side-grasp Cartesian IK candidates: 8/32
best existing start: full static scene 3/14, combined sampled fraction 22.2%
best new Cartesian start: full static scene 2/14, combined sampled fraction 14.8%
dynamic recorded-oracle: not run because the static/contact gate failed
```

이 결과는 측면 방향을 90도 돌리는 문제가 남았다는 뜻이 아니다. 최고 후보의 측면
축은 여전히 13/14를 만족한다. 병목은 한 공통 시작 등록에서 시연별 상대 이동을
로봇 workspace, 고정 테이블 높이와 pad 중심에 동시에 맞추지 못하는 것이다. 다음
단계는 proxy 확대나 8mm gate 완화가 아니라, 시연별 첫 관측을 공통 시작으로
재앵커링한 현재 규칙이 물체 배치 분산과 grasp-height 분산을 어떻게 보존하는지
검사하고, 데이터에서 정당화되는 canonical workspace 등록 규칙을 정의하는 것이다.
그 전에는 학습 정책이나 동적 finalist를 실행하지 않는다.

선택 후보에서 추론된 접촉 물체 위치는 X `0.372~0.658m`로 28.6cm 퍼졌고,
contact pinch Z는 `0.0333~0.0487m`였다. 큰 X를 한 공통 변환으로 안쪽에 넣을 수
있는지 확인하려고 시작 TCP를 `-0.12/-0.16/-0.20m`까지 옮기는 측면 자세 IK도
시도했지만, 유효한 측면 후보 수는 오히려 2개뿐이었다. SO-101 5-DoF에서 현재
측면 회전을 유지한 큰 평행이동은 성립하지 않았다. 이 장시간 중복 검색은 screen
1개 뒤 중단했고 성공 결과로 기록하지 않는다. 후보 생성기는 검증된
`X=-40/-80/+40mm`, `Y=0/+-40mm`, `Z=0/+-15mm` 로컬 조합으로 되돌렸다.

### 0916 후속: 공통 시작 재앵커링 workspace audit

탐색 결과에 에피소드별 이름 있는 실패 사유와 공통 시작 TCP에서 접촉까지의 변위를
저장하도록 추가했다. 같은 source registration, `object_approach_offset=-20mm`,
validation 14편을 정책 없이 재검사한 결과는 다음과 같다.

```text
result: out/relative_chunk_v9_workspace_audit_source_offset_minus20mm_20260916.json
sha256: c288b6206680a536e2a04ee7f90c2f5a2b4aef4a3520a0275708129222fdadeb
side-grasp axes: 13/14
table edge margin: 8/14
contact IK available: 10/14
fitted-pad centred: 4/14
first swept contact detected: 10/14
legacy full-XY pinch-centred <=8mm: 0/14
first swept contact body-depth-centred <=10mm: 7/14
```

공통 시작 TCP로 재앵커링한 뒤 시작→접촉 변위 span은 X `286.2mm`, Y
`286.7mm`, Z `15.4mm`다. 추론 물체 위치도 같은 X/Y span을 보인다. 파지 높이
절대오차는 중앙 `3.32mm`, p95 `7.91mm`, 최대 `10.32mm`이므로 높이보다 수평
workspace 분산이 더 크다.

14편의 물체 footprint를 테이블 안에 넣는 공통 XY 평행이동 교집합 자체는 존재한다
(X `[-572.3,-157.5]mm`, Y `[-193.5,+220.9]mm`). 그러나 이는 테이블 배치만
가능하다는 뜻이며, 5-DoF IK 또는 카메라 시야를 보장하지 않는다. 실제로 4편은
contact IK가 없고, 10편은 pad 중심 조건을 실패한다. 따라서 후보 수를 늘리기보다
recorded data로 정당화할 수 있는 canonical workspace 규칙과 실제 배치 허용 범위를
확정해야 한다. 에피소드별 object-only offset이나 존재하지 않는 robot command를
만들어 실패를 숨기지 않는다.

기존 `8mm` 계산은 최초 접촉 순간의 **pinch midpoint와 물체 중심 사이 전체 XY
거리**였다. 측면 접근에서는 표면에 처음 닿을 때 물체 반깊이만큼 접근축 거리가
남으므로, 이를 lateral centring 오차로 세면 안 된다. 기준값을 늘리거나 삭제하지
않고, 전체 XY를 `접근축 깊이`와 `접근축에 수직인 planar lateral 오차`로 분해해
8mm를 lateral 성분에 적용하도록 수정했다. 몸통 깊이는 기존 contact-centroid
10mm 조건이 별도로 담당한다.

기존 JSON으로 새 정의를 읽기 전용 재계산하면 접촉 검출 10편 중 lateral 8mm 통과가
9편이며, lateral과 몸통 깊이를 함께 통과한 것은 7편이다. 다른 scene failure까지
그대로 적용하면 정적 통과 예상값은 `3/14`(`150926`, `150943`, `150948`)다. 이는
수정 코드로 전체 MuJoCo 재실행하기 전의 파생값이며 최종 결과가 아니다.

### 0916 확정: lateral 8mm와 정적 80% 게이트 재현

수정된 lateral 정의와 정적 scene 최소 통과율 `0.8`을 탐색기 판정에 연결했다.
이전 코드는 한 편만 정적 통과해도 diagnostic candidate status를 내는 문제가 있었고,
이제 `scene_constraint_episode_fraction >= 0.8`이 아니면 명시적으로 거부한다.

```text
result: out/relative_chunk_v9_workspace_audit_lateral8mm_gate80_offset_minus20mm_20260916.json
sha256: 73676d8bce873e624001d4c4d656d76520e17a7951ed69ffb8ef5cf68cbf8bda
static threshold: 80%
static scene pass: 3/14 = 21.4%
pass episodes: 150926, 150943, 150948
first contact detected: 10/14
lateral <=8mm: 9/14 (9/10 detected)
body-depth <=10mm: 7/14
contact IK available: 10/14
pad centred: 4/14
table margin: 8/14
status: REJECTED_NO_VALID_REGISTRATION
```

따라서 `150948`은 fitted patch가 양측 접촉 후 물체를 들 수 있다는 positive fixture로
유지하지만, 전체 등록 성공이나 학습 정책 성공의 근거가 아니다.

통과 3편에서 추론된 물체 위치 envelope는 X `0.456~0.500m`, Y
`-0.024~0.051m`다. 그러나 이것은 독립적으로 관측한 물체 pose가 아니라 near-close
gripper pose에서 역으로 놓은 값이고 표본도 3편뿐이다. 이 범위를 실제 canonical
workspace 또는 수집 프로토콜로 확정하면 순환 추정이 된다. 현재 0911 raw에는
테이블 fiducial, 독립 object pose 또는 robot-base extrinsic이 없으므로 하나의 실제
`T_base_world`는 식별할 수 없다.

다음 진단은 에피소드별 object-only offset이 아니라, **물체와 recorded trajectory
전체에 동일한 rigid translation을 적용하는 simulation-only canonical replay**로
제한한다. 이 검사는 상대 궤적/접촉 형상이 workspace 안에서 성립하는지만 분리하며,
실제 캘리브레이션이나 배포 등록값을 생성하지 않는다. 여기서도 80%를 넘지 못하면
등록이 아니라 궤적/IK/접촉 모델이 병목이다. 넘으면 독립 object/base 기준을 추가로
수집하기 전까지 학습 정책 rollout으로 넘어가지 않는다.

### 0916 확정: simulation-only canonical replay도 80% 게이트 실패

진단 구현은 물체만 옮기지 않는다. 각 에피소드에서 추론된 물체 중심을 지정한
canonical 점으로 옮긴 뒤, **기록된 모든 TCP pose에 동일한 평행이동**을 적용하고
각 pose의 IK를 처음부터 다시 푼다. 후보 탐색, 동적 rollout 및 정책 추론은 이
모드에서 금지한다. 통과하더라도 status는
`DIAGNOSTIC_CANONICAL_REPLAY_NOT_REGISTRATION`으로만 낼 수 있다.

두 개의 사전 고정 기준점을 검사했다.

| canonical object centre | anchor IK | contact IK | static scene | 결과 |
|---|---:|---:|---:|---|
| `(0.30, 0.00, 0.03835)m` | 0.0% | 0/14 | 0/14 | 전 궤적 도달 불가 |
| `(0.48, 0.00, 0.03835)m` | 40.7% | 9/14 | 0/14 | 접촉 일부 회복, 공통 gate 실패 |

두 번째 점은 물체 가시성과 테이블 margin이 모두 `14/14`, 측면 축이 `13/14`였지만
시작 pose IK는 `3/14`, pad 중심은 `4/14`, 최초 접촉의 lateral 8mm는 `9/14`,
몸통 깊이 10mm는 `7/14`에 그쳤다. 접촉 IK가 있는 경우 collision-free는 `9/9`라서
현재 0%의 직접 원인은 마찰이나 물체 전도가 아니라 궤적/작업공간과 pad 중심이다.

```text
result 0.30m: out/relative_chunk_v9_canonical_replay_x030_20260916.json
sha256: 4bfb496088455686f5cfb8f357b12929e45898ed243bca1ebc30b55c7f323ae4
result 0.48m: out/relative_chunk_v9_canonical_replay_x048_20260916.json
sha256: 858295161bbe9abca174ed47543cee430e48fa16d20b7cccad2396fef507c498
status: REJECTED_DIAGNOSTIC_CANONICAL_REPLAY (both)
```

이 결과를 실제 `T_base_world`로 해석하면 안 된다. canonical replay에서는 시연의
물체-relative 궤적을 보존하기 위해 시작 TCP도 에피소드마다 함께 이동한다. 반면 실제
배포는 공통 robot home에서 시작한다. 즉 이 검사는 "한 안전한 작업공간 점으로 전체
궤적을 옮기면 기하가 살아나는가"만 묻고, 실제 등록값을 식별하지 않는다.

결론적으로 더 많은 무작위 등록 후보를 탐색하기 전에 지원할 **물체 배치/상대 이동
범위**를 먼저 정해야 한다. 현 14편 시작→접촉 평면 span `286.2×286.7mm` 전체를
단일 공통 home의 ver1 5-DoF 측면 파지로 80% 재현한다는 전제는 현재 증거와 맞지
않는다. 다음 검사는 최신 ver1 TCP·측면 자세·collision patch로 도달 가능 영역을
다시 계측하고, 그 고정된 영역 안에 들어오는 시연 집합을 사전에 정의하는 것이다.
이는 성공 결과를 보고 편을 고르는 절차가 아니라, 수집·평가 전에 task support를
고정하는 절차여야 한다.

### 0916 확정: 최신 ver1 측면 작업공간 재계측

과거 `workspace.py`의 `15×35cm` 수치는 top-down 자세와 pre-ver1 TCP 기준이므로
현재 몸통 측면 파지에 재사용하지 않았다. 별도 정책 없는 진단기
`tools/probe_ver1_side_workspace.py`를 추가해 다음 조건을 동시에 검사했다.

- handoff ver1 TCP와 수평 jaw/approach/up frame
- fitted finger patch와 가시 finger가 같은 body에 부착된 현재 모델
- 40mm gap, 물체 중심 높이 38.35mm, 접근축 오프셋 -20mm
- IK/관절범위, 테이블·자기충돌 없음, pad 수직 중심, wrist-camera 가시성,
  물체 footprint 30mm 테이블 여유

50mm coarse grid 72점에서는 `x=0.40/0.45/0.50m, y=0`의 3점만 통과했다.
그 주변을 25mm로 재계측한 81점에서는 18점이 통과했고 최대 연속 직사각형은
`x=[0.375,0.500]m`, `y=[-0.025,0.025]m`, 즉 약 `12.5×5cm`였다.

```text
coarse: out/ver1_side_workspace_coarse_50mm_20260916.json
sha256: e39a1684faae0907dc10827b5ec86672c3b43e01a443e21236ce6bdb8ed2a378
refine: out/ver1_side_workspace_refine_25mm_v2_20260916.json
sha256: fac0d016a00d84a9663e0d7ab161a560e1870fa470959aeb583cda914a72e323
status: DIAGNOSTIC_VER1_SIDE_WORKSPACE_NOT_PHYSICAL_CALIBRATION
```

source registration에서 추론한 validation 14편의 물체 중심을 이 직사각형과 단순
대조하면 2편만 안에 든다. 이 대조는 독립 object pose가 아니므로 에피소드 채택
정본으로 쓰지 않지만, 시작→접촉 span `28.6×28.7cm`가 현 측면 workspace보다
넓다는 진단과 일치한다.

이 지도는 정적 상한이다. 양측 접촉·전체 궤적·lift를 보장하지 않고 실제
`T_base_world`도 아니다. 다음 본수집 전에는 독립적인 object/table/base 기준으로
실제 배치 허용 범위를 이 영역 안에 정의하고 기록해야 한다. 현재 데이터에 대해서는
이 결과를 근거로 성공 편만 사후 선택하지 않으며, 정책 학습 rollout도 계속 보류한다.

### 0916 추가: HW `can_side` 배치 envelope와 측면 프레임 분리 진단

HW handoff의 `configs/can_side.yaml`에서 사전에 선언된 시뮬레이션 배치 범위
`x=[0.385,0.415]m`, `y=[-0.035,0.035]m`를 별도 task-envelope 설정으로
옮겼다. 이는 YCB 토마토 캔용 시뮬레이션 범위이며 실물 구운감자 케이스의 측정
배치나 `T_base_world`가 아니다. 최신 ver1 정적 측면 자세로 5mm grid를 검사하면
105점 중 104점이 IK, 충돌 없음, pad 수직 중심, 잠정 MuJoCo wrist-camera 가시성
및 테이블 margin을 동시에 통과했다. ver1 optical extrinsic은 없으므로 가시성은
실물 카메라 검증이 아니다. 최대 연속 직사각형은
`x=[0.385,0.415]m`, `y=[-0.035,0.030]m`다.

```text
envelope config: configs/real/ver1_side_task_envelope_can_side_handoff_20260916.json
result: out/ver1_side_workspace_can_side_envelope_5mm_20260916.json
sha256: b0f86e02f13a6b758ec137f9cebd1b30df761164b9179307c12168a075c189c2
accepted: 104/105 (99.0%)
```

따라서 사전 고정한 시뮬레이션 배치 범위 자체는 정적 canonical 측면 자세를 거의
모두 지원한다. 다음으로 그 중심 `(0.40,0.00,0.03835)m`에서 같은 14편의 전체
recorded trajectory를 세 조건으로 비교했다. 회전 및 3D 정렬을 바꾸는 조건은 모두
명시적인 simulation-only 진단이며 원본 데이터와 학습 라벨은 수정하지 않았다.

| 조건 | anchor geometry | full scene | side frame | pad 중심 | 첫 접촉 |
|---|---:|---:|---:|---:|---:|
| 원본 회전 + XY 이동 | 0/27 | 0/14 | 0/14 | 0/14 | 8/14 |
| 고정 측면 회전 + XY 이동 | 9/27 | 0/14 | 14/14 | 1/14 | 4/14 |
| 고정 측면 회전 + 접촉점 3D 정렬 | 10/27 | 3/14 | 14/14 | 14/14 | 7/14 |

```text
raw+xy:
  out/relative_chunk_v9_canonical_replay_can_side_center_x040_20260916.json
  sha256 721a189484ba7a6bc6bbe13f2dfb627b71e1b11642fb108fea5e7d62ccbe9df4
side+xy:
  out/relative_chunk_v9_canonical_replay_can_side_center_x040_projected_20260916.json
  sha256 a5c7e736fd689f963f55c12e42661afee7ba31c548e57bea157ed24a543ff2b3
side+3d:
  out/relative_chunk_v9_canonical_replay_can_side_center_x040_projected_aligned3d_20260916.json
  sha256 2c235c7a6c1c3dd4b5b8586662de4d93b0c6f77f314a96820784d25ecf8b6c66
status: REJECTED_DIAGNOSTIC_CANONICAL_REPLAY (all)
```

고정 측면 회전으로 side frame이 `14/14`가 된 것은 원본 UMI 회전을 그대로
SO-101 5-DoF에 요구하는 것이 병목이었음을 보인다. 접촉점을 물체 몸통 중심에 3D로
맞추면 pad 중심과 contact IK도 `14/14`가 되므로 에피소드별 수직 기준 불일치도
별도 병목이다. 하지만 이는 측정 캘리브레이션이 아니라 결과를 분해하기 위한 강제
투영이므로 정본 변환이나 학습 라벨로 승격하지 않는다.

남은 첫 접촉 실패는 gap과 명확히 분리된다. 접촉한 7편의 near-close target gap은
`34.84~38.15mm`, 접촉하지 않은 7편은 `39.81~41.21mm`로 겹치지 않는다. 현재
물체 폭 `39mm`와 fitted pad는 잠정값이며 캘리퍼 실측이 아니다. 따라서 다음 물리
게이트는 무작위 위치 탐색이 아니라 다음 두 값을 독립 측정해 고정하는 것이다.

1. 실제 구운감자 케이스의 파지 방향 몸통 폭
2. SO-101/ver1의 명령 gap과 양쪽 pad 내측면 사이 실제 거리의 대응

이 둘이 확정되기 전에는 object width나 pad를 성공률에 맞춰 조정하지 않고,
recorded-oracle 동적 lift 및 학습 정책 rollout도 계속 보류한다.

### 0916 확정: 40mm 폭·고정 K=2 시작의 policy-free 동적 게이트

사용자가 구운감자 케이스의 파지축 몸통 폭을 `40mm`로 실측했다고 회신했다.
측정 도구·측정 위치·반복 횟수는 전달되지 않았으므로
`configs/real/baked_potato_case_grasp_width_20260916.json`에 그 한계를 함께
기록했다. 이 값은 물체 폭만 고정하며 SO-101 명령 gap과 실제 pad 내측 간격을
보정하지 않는다.

원본 gap 라벨은 바꾸지 않고 실행 사본에만 0/1/2mm preload를 적용한 14편 비교에서
최초 양측 접촉은 각각 `8/14`, `12/14`, `14/14`였다. 2mm는 gap 병목을 분리하기
위한 MuJoCo 전용 진단값이며 실물 보정값이 아니다. 접촉행을 결과에서 고르는 대신
모든 편에 동일한 규칙 `start = contact_row - K`를 적용했고, 10Hz에서 2-frame 관측
이력을 보존하는 최소값 K=2가 정적 scene `14/14`, geometry `22/27`, contact
`14/14`를 통과했다. K=0은 접근 과정이 없는 하한 진단이라 성공 근거로 쓰지 않는다.

K=2 시작행·그 행의 정적 IK 해·2mm preload를 보고서에 고정한 뒤 정책 체크포인트를
전혀 로드하지 않고 14편 recorded-oracle 동적 replay를 실행했다. 첫 집계는
`11/14`였으나, 실패 세 편의 JSON과 GIF를 확인하자 양측 접촉과 리프트는 모두
유지됐고 리프트 뒤 수평 운반을 테이블 위 shove로 잘못 합산한 scorer 오류였다.
19.5mm 임계값은 변경하지 않았다. 대신 물체가 5mm 이하로 들린 구간의 XY 이동만
pre-lift shove로 판정하고, 전체 XY 이동은 별도 진단 필드로 계속 저장하도록
수정했다.

```text
report:
  out/relative_chunk_v9_width40mm_preload2mm_lookback2_dynamic_gate14_preliftfix_20260916.json
status: PASS_LOCAL_DYNAMIC_ORACLE_GATE
bilateral contact / stable lift: 14/14 / 14/14
final lift: 50.8--112.0mm
max pre-lift XY displacement: 17.0mm
pre-lift XY threshold: 19.5mm
IK reject / tip / runner error: 0 / 0 / 0
```

이는 **로컬 policy-free 시뮬레이션 geometry/contact gate**만 닫는다. 2mm preload의
실물 정합성, 실제 `T_base_world`, 실물 카메라 extrinsic, 학습 정책의 성공률 또는
모터 안전을 검증하지 않는다. 학습 정책 sanity rollout을 수행하더라도 이 조건을
simulation-only 진단으로 표기하고, 공유 GPU에서는 학습만 수행한다.

### 0916 후속: v10 계약 재생성 및 첫 learned-policy image gate

기존 v9는 이미지·proprio·action 수치는 맞았지만
`observation_timestamp (N,)`인 `umi_relative_chunk/0.1.0-provisional`이었다.
보존된 canonical bundle 70편에서 v10을 다시 생성해 0.2.0 계약으로 올렸다.
v9와 v10의 `image`, `proprio`, `action`, `action_timestamp`, `source_row`를
70편 전수 비교했으며 모두 byte-level array equality를 통과했다. 바뀐 것은
`observation_timestamp`가 history 순서 `[t-1,t]`인 `(N,2)`가 된 것과 메타뿐이다.

```text
dataset: datasets/umi_real_relative_20260911_v10
schema: umi_relative_chunk/0.2.0-provisional
episodes / anchors / contract problems: 70 / 1,248 / 0
tar bytes: 305,116,160
tar sha256: c45e2dedf91ec6b067adcf36ab1e3079e93fa78208d52211b4c3201d2625a420
```

V100에서는 학습만 수행했다. seed 0, 30 epoch 결과의 best validation은 epoch 27의
`0.39769`였고, 체크포인트는 최신 0.2.0 schema를 기록한다.

```text
last sha256:
  72ddb492d6f30b51daf065b96a043b81f4c511a2e1118ba3b6b0758b1d240199
best-val sha256:
  5bc8a25de054bc29967ebe387d808d5add0e7ad8f791c60e40b20f3440e6adbb
```

로컬 CPU에서 같은 best-val 체크포인트를 사용해 고정된 validation 14편을 비교했다.
모든 조건은 simulation camera closed loop, K=2의 사전 선언 시작행/IK 해,
simulation-only 2mm preload, `validation_horizon=1`, `execute_steps=1`이다.
학습 정책도 oracle과 같은 사전 선언 시작점을 쓸 수 있도록 rollout 도구를 확장했고,
교란은 RGB 정책 입력에만 적용하며 MuJoCo 상태와 scorer는 바꾸지 않는다.

```text
normal:   stable side-grasp lift 14/14, Wilson 95% [78.5%, 100%]
blackout: stable side-grasp lift  0/14, Wilson 95% [ 0.0%, 21.5%]
normal final lift range: 50.3--87.3mm
normal IK reject / tip: 0 / 0
normal per-episode max required time scale: 2.85--4.91x
blackout geometry reject: 14/14
```

같은 체크포인트와 14개 에피소드에 대해 추론 시점 `noise`와 `freeze`도 실행했다.
교란은 policy RGB에만 적용했고 simulator state와 scorer는 바꾸지 않았다.

```text
condition  success  bilateral  geometry error  max lift min/median/max
normal     14/14    14/14      0/14            50.3 / 76.8 / 87.3mm
blackout    0/14     1/14     14/14             1.5 /  2.0 /  3.2mm
noise      14/14    14/14      0/14            50.0 / 52.6 / 55.3mm
freeze     14/14    14/14      0/14            50.0 / 74.9 / 86.7mm
```

14/14 조건의 Wilson 95% 구간은 모두 `[78.5%,100%]`, blackout 0/14는
`[0%,21.5%]`다. 그러나 이 결과는 실시간 시각 피드백 사용을 입증하지 않는다.
첫 simulation observation stack을 고정한 `freeze`가 정상과 같은 14/14였으므로,
현재 near-contact 진단은 시간에 따라 갱신되는 RGB 없이도 풀린다. 매 tick Gaussian
noise를 넣은 조건도 14/14였으므로 blackout 실패만으로 올바른 물체 인식 또는
시각 의존성을 주장할 수 없다. 상수 검정 입력에서의 0/14는 특정 OOD activation
반응일 수 있다.

따라서 다음 검증은 K=2 성공률을 더 반복하는 것이 아니라, seed 1/2와 더 먼 공통
시작점 또는 물체 위치 교란에서 정상과 `freeze/noise`를 비교해야 한다. 현재 결과는
단일 seed, near-contact K=2 시작, 1-step validation/execution, 미실측 collision
proxy와 simulation-only 2mm preload에 한정되며 full 8-step 실행도 미검증이다.

대표 영상과 보고서는 다음과 같다.

```text
video: out/relative_chunk_v10_bestval_150943_simcam_k2_policy_20260916.gif
report: out/relative_chunk_v10_bestval_150943_simcam_k2_policy_20260916.json
normal14: out/relative_chunk_v10_bestval_simcam_k2_normal14_20260916/
blackout14: out/relative_chunk_v10_bestval_simcam_k2_blackout14_20260916/
noise14: out/relative_chunk_v10_bestval_simcam_k2_noise14_20260916/
freeze14: out/relative_chunk_v10_bestval_simcam_k2_freeze14_20260916/
```

### 0916 추가 진단: object-only 교란과 K=4 고정 시작

K=2에서 로봇 시작 자세는 고정한 채 시뮬레이션 물체만 X/Y로 이동하는 진단을
추가했다. 대표 편 `rec_20260911_150943`에서 ±15mm 네 방향은
`normal/noise/freeze` 모두 성공했고, ±25mm에서는 12조건 중 10조건이 성공했다.
`+X 25mm`의 normal과 freeze만 첫 예측 목표가 관절 한계를 넘어 실패했지만 noise는
성공했다. 따라서 이 검사는 물체 위치를 영상으로 재탐색했다는 증거가 아니다. K=2의
근접 시작에서는 넓은 접촉 포획 범위와 밀기만으로도 성공할 수 있고, Gaussian noise가
정상 영상보다 보수적인 평균 출력을 유도할 수 있다. 이 이유로 14편 전수 확장은
중단했다.

K=4는 기존 등록 보고서에 고정 시작 관절값이 없어 발생한 runner error를 성능 실패로
세지 않고, 동일한 검색 인자와 최신 코드로 보고서를 다시 생성했다. 전체 14편 중 정적
scene-valid subset은 8편이므로 등록 자체는 계속
`REJECTED_DIAGNOSTIC_CANONICAL_REPLAY`다. 성공 편만 골라 정본으로 승격하지 않기 위해
게이트는 명시적 episode 목록과 `--allow-rejected-diagnostic-subset`이 있을 때만 이
부분집합 진단을 허용하고, 결과에 source status와 부분집합 평가 사실을 기록한다.

```text
registration:
  out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json
policy-free oracle subset: 8/8 contact, 8/8 stable lift
oracle report:
  out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_subset8_dynamic_gate_20260916.json

condition  success  bilateral  geometry error  max lift min/median/max
normal      5/8      5/8        3/8             0.0 / 53.6 / 87.8mm
freeze      5/8      5/8        3/8             0.0 / 53.3 / 87.3mm
noise       8/8      8/8        0/8            51.5 / 52.4 / 55.3mm
mean_fill   7/8      7/8        1/8             4.3 / 55.1 / 65.4mm
blackout    0/8      0/8        8/8             1.5 /  2.2 /  3.0mm
```

normal과 freeze는 동일한 세 편(`150753`, `150943`, `151043`)에서 첫 예측 명령이
위치·접근축 허용오차 안에는 들지만 관절 한계를 넘어 실패했다. 이 결과 역시 noise가
시각 인식을 개선했다는 뜻이 아니다. 실물 JPEG로 학습한 정책에 MuJoCo RGB를 넣은
도메인 불일치 때문에 정상 영상이 공격적인 OOD 출력을 만들고, noise가 평균 정책에
가까운 보수적 출력을 만든다는 해석이 현재 증거에 가장 잘 맞는다. normal과 freeze가
같다는 점도 시간에 따라 갱신되는 시각 피드백 사용을 지지하지 않는다.

순수 검정색의 OOD 효과를 분리하기 위해, 각 history frame의 RGB 채널 평균은 유지하되
모든 픽셀을 그 평균으로 채워 공간 정보를 완전히 지우는 `mean_fill`을 별도 조건으로
추가했다. 이 조건이 7/8을 성공했고 실패는 `150943` 한 편뿐이었다. 동일 K4 8편의
blackout은 0/8, geometry error 8/8이었다. 즉 현재 K4 진단은 물체 위치·형태가 없는
입력에서도 대부분 성공하며, blackout 실패는 검정색이라는 특정 OOD activation의
영향이 크다. `mean_fill` 역시 성공 조건이 아니라 no-spatial-information 대조군이며,
이 7/8을 정책 성능으로 인용하지 않는다.

따라서 `blackout`은 성공하도록 개선하지 않는다. 검정 영상은 정보 제거 대조군이며,
그 성능을 올리면 검사의 의미가 사라진다. 다음 우선순위는 실물 학습 프레임과 MuJoCo
손목 카메라 프레임의 시야·광학·색/조명 분포를 비교해 입력 도메인을 정렬하고, 필요하면
시뮬 사전학습/도메인 랜덤화 후 실물 파인튜닝을 적용한 뒤 동일한
`normal/blackout/mean_fill/noise/freeze` 조건을 다시 측정하는 것이다.
