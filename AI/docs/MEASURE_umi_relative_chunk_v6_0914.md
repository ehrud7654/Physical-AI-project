# MEASURE — 실물 UMI 상대 action chunk v6 파일럿 (2026-09-14)

관련 Jira: `S15P21A103-34`(학습 기준선), `S15P21A103-154`(UMI 상대 action preflight)

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

## V100 smoke 학습

이 데이터는 기존 `tools/train_bc.py`의 관절 `(6,)` 계약과 호환되지 않는다.
반드시 전용 학습기를 사용한다.

```bash
cd ~/S15P21A103_umi/AI
conda activate aiot_v100

export CUDA_VISIBLE_DEVICES=2
export AI_THREADS=2
export OMP_NUM_THREADS=2
export MKL_NUM_THREADS=2
export OPENBLAS_NUM_THREADS=2
export NUMEXPR_NUM_THREADS=2

python tools/verify_umi_relative_dataset.py \
  datasets/umi_real_relative_20260911_v6

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

학습 후 같은 체크포인트에서 이미지 의존성을 검사한다.

```bash
python tools/probe_relative_chunk_policy.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --policy-ckpt checkpoints/relative_chunk/umi_real_relative_v6_smoke.pt \
  --samples 1000 \
  --batch-size 64 \
  --device cuda \
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

1. V100 3-epoch smoke 학습과 checkpoint 저장 확인
2. 같은 checkpoint의 검정·셔플·노이즈 이미지 검사
3. 한 episode overfit으로 모델이 데이터 자체를 학습 가능한지 확인
4. 30-epoch 기준선 학습
5. 상대 chunk를 현재 SO-101 pose에 decode하고 IK/dynamic preflight 수행
6. 카메라 조건을 맞춘 MuJoCo 폐루프 영상과 성공률 확인

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

다음 명령은 모터를 전혀 사용하지 않는 offline 사전검사다. 체크포인트에 기록된
validation episode에서 균형 표본을 뽑고, 각 상대 action chunk를 같은 잠정 SO-101
시작 pose에 붙여 MuJoCo IK와 실물 설정의 명령 속도·가속도 한계를 검사한다. 같은
표본의 기록된 정답 chunk도 동일 조건에서 함께 검사한다. 정답도 실패하면 등록·IK·
시간 조건 문제이고, 정답은 통과하지만 예측만 실패하면 학습 정책 문제다.
기하 판정에는 IK 잔차와 실물 관절 범위만 적용한다. 속도에서 환산한 step 제한은
기하 판정에 섞지 않고, 통과한 관절열의 timestamp 기반 속도·가속도로 따로 판정한다.

```bash
cd ~/S15P21A103_umi/AI
conda activate aiot_v100

export CUDA_VISIBLE_DEVICES=2
export AI_THREADS=2
export OMP_NUM_THREADS=2
export MKL_NUM_THREADS=2
export OPENBLAS_NUM_THREADS=2
export NUMEXPR_NUM_THREADS=2

python tools/preflight_relative_chunk_policy.py \
  --data datasets/umi_real_relative_20260911_v6 \
  --policy-ckpt checkpoints/relative_chunk/umi_real_relative_v6_seed0.pt \
  --device cuda \
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

GPU 추론을 하지 않는 CPU 기하 탐색이다. 먼저 32개 후보를 validation episode
3편으로 빠르게 거른 뒤 상위 4개만 전체 14편에서 평가한다.

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
  --device cuda \
  --samples 200 \
  --out out/relative_chunk_v6_seed0_registration32_preflight.json
```

기록 정답 geometry가 충분히 통과하지 않으면 epoch를 추가하지 않는다. 후보 수를
늘려도 개선되지 않으면 시작 등록 하나의 문제가 아니라 5-DoF orientation 투영,
IK branch 또는 hand-eye 정의를 다시 검사한다. geometry 통과 후에는 보고된 시간
확대율로 실행 스케줄을 늦춘 다음, 대표 validation episode와 그 episode에서 추론한
물체 위치를 사용해 camera-matched MuJoCo 영상을 만든다.
