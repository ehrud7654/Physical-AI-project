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
