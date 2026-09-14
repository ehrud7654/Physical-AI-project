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
MuJoCo learned-policy 진단을 해야 한다. 공유 GPU 서버는 이 **학습만** 수행하고,
추론·probe·preflight·등록 탐색·MuJoCo는 계속 로컬 CPU에서만 실행한다.

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
