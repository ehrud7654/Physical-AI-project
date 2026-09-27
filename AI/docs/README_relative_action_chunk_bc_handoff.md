# Relative Action Chunk Behavior Cloning 인수인계

> 대상: ORB-SLAM 기반 Track A v10 데이터셋을 전달받아 학습·평가·실행 경계를 연결하는 팀원
> 기준일: 2026-09-22
> 상태: `umi_relative_chunk/0.2.0-provisional` 진단 경로. 실물 배포 승인 상태가 아니다.

## 1. 한 문장 정의

Relative Action Chunk Behavior Cloning은 최근 2개 시점의 RGB 영상과 로봇 상태를 입력받아, 현재 TCP를 기준으로 앞으로 약 0.8초 동안의 상대 EEF 목표 8개와 각 미래 시점의 절대 gripper gap을 한 번에 예측하는 모방학습 정책이다.

```text
최근 관측 2개
RGB + 상대 TCP 상태 + gripper gap
                 |
                 v
      Relative Chunk BC Policy
                 |
                 v
미래 목표 8개
상대 위치 + 상대 회전 + 절대 gripper gap
```

이 정책은 강화학습이 아니다. 사람이 UMI로 실제 수행한 미래 궤적을 정답으로 사용해 예측과 시연 사이의 L1 오차를 줄이는 지도학습 기반 Behavior Cloning이다.

## 2. 전체 파이프라인과 책임 경계

```text
Raw UMI
  |
  v
telemetry · gripper 전처리
  |
  v
ORB-SLAM3 camera pose
  |
  v
ArUco ID 13 공통 작업대 좌표 정렬
  |
  v
Camera-to-TCP 변환
  |
  v
Image · Pose · Gripper 시간 동기화
  |
  v
Relative Action Chunk v10 Dataset
  |
  v
RelativeChunkBCNet 학습
  |
  v
상대 EEF chunk 복원 · IK · 동역학 검사
  |
  v
앞부분 N개 실행 · 재관측 · 재추론
```

`AI/notebooks/build_private_orbslam_v10.ipynb`는 원본 전처리부터 v10 데이터셋과 reference bundle 생성·검증까지 수행한다. 정책 학습, 체크포인트 추론, MuJoCo rollout과 실물 명령 송신은 수행하지 않는다.

## 3. 고정 데이터 계약

| 항목 | 값 |
|---|---|
| schema | `umi_relative_chunk/0.2.0-provisional` |
| sampling rate | 10 Hz |
| observation horizon | 2 |
| action horizon | 8 |
| image | `(N, 2, 3, 224, 224)`, `uint8`, RGB |
| proprio | `(N, 2, 10)`, `float32` |
| action | `(N, 8, 10)`, `float32` |
| observation timestamp | `(N, 2)`, `[t-1, t]` |
| action timestamp | `(N, 8)`, 모두 현재 관측보다 미래 |

### 3.1 10차원 벡터

`proprio`와 `action`은 같은 10차원 순서를 사용한다.

```text
[x_m, y_m, z_m,
 r0x, r0y, r0z,
 r1x, r1y, r1z,
 gap_m]
```

- `x_m, y_m, z_m`: 현재 pinch pose 기준 상대 평행이동, 단위 m
- `r0*`, `r1*`: 현재 pinch pose 기준 상대 회전행렬의 첫 두 행
- `gap_m`: 미래 시점의 절대 접촉면 간격, 단위 m
- 허용 gap 범위: `[0.0, 0.09] m`

위치와 회전은 상대값이지만 gap은 상대 변화량이 아닌 절대 폭이다.

### 3.2 관측 2개

```text
image     : (2, 3, 224, 224)
proprio   : (2, 10)
timestamp : [t-1, t]
```

- 첫 행은 이전 관측 `t-1`이다.
- 두 번째 행은 현재 관측 `t`이다.
- 현재 pose는 자기 자신을 기준으로 하므로 위치·회전이 항등 상태다.
- 두 이미지 축은 카메라 두 대가 아니라 시간 history 두 개를 의미한다.

### 3.3 미래 목표 8개

```text
action : (8, 10)

index 0 : t + 약 0.1 s
index 1 : t + 약 0.2 s
...
index 7 : t + 약 0.8 s
```

8개 미래 목표는 직전 미래 목표에 대한 delta가 아니다. 모두 같은 현재 TCP를 anchor로 사용한다.

```text
T_relative[k] = inverse(T_current_pinch) @ T_future_pinch[k]
T_robot_target[k] = T_robot_current_pinch @ T_relative[k]
```

다음 해석은 잘못됐다.

```text
target[1] = target[0]에서 추가 이동
target[2] = target[1]에서 추가 이동
```

정확한 해석은 다음과 같다.

```text
target[0] = 현재에서 첫 미래 pose까지
target[1] = 현재에서 둘째 미래 pose까지
target[2] = 현재에서 셋째 미래 pose까지
```

이를 누적 delta로 잘못 적용하면 청크가 진행될수록 목표가 크게 벗어난다.

## 4. `action` 필드의 의미

NPZ 배열 이름은 구현 호환 때문에 `action`이지만 의미는 controller command가 아니다. 외부 의미는 `relative_target_trajectory`다.

UMI raw에는 로봇 controller의 `ctrl`, EEF controller target 또는 IK 입력 command가 없다. 따라서 다음처럼 해석하면 안 된다.

```text
action[t] = joint_state[t+1]
```

현재 계약의 의미는 다음과 같다.

```text
action[t, k] = inverse(current_tcp[t]) @ demonstrated_future_tcp[t+k]
```

즉 사람이 실제로 이동한 미래 pinch 궤적을 현재 pose 기준으로 표현한 학습 target이다.

## 5. 모델 구조

구현 파일: `AI/policy/relative_chunk_bc.py`

```text
image[B, 2, 3, 224, 224]
             |
             v
각 이미지를 공유 CNN Encoder로 인코딩
             |
             v
image_feature[B, 2, feature_dim]
             +
proprio[B, 2, 10]
             |
             v
시점별 결합 후 flatten
             |
             v
MLP Head
             |
             v
prediction[B, 8, 10]
```

기본 모델은 마지막 CNN feature map을 `1x1`로 전역 평균화한다. Spatial4 설정은 `4x4` 공간 격자를 유지한 뒤 projection에 전달한다.

```text
baseline : global 1x1 feature
spatial4 : spatial 4x4 feature
```

Spatial4는 별도 정책 종류가 아니라 같은 Relative Action Chunk BC의 image encoder 개선 버전이다.

설정 파일:

- baseline: `AI/configs/train/relative_chunk_bc.yaml`
- spatial4: `AI/configs/train/relative_chunk_bc_spatial4.yaml`

## 6. 학습 방식

구현 파일: `AI/tools/train_relative_chunk_bc.py`

### 6.1 Episode 단위 분할

train/validation은 개별 frame이 아니라 episode 단위로 분리한다. 같은 시연의 인접 frame이 train과 validation에 동시에 들어가는 누수를 막기 위해서다.

### 6.2 Target 표준화

train split에서 `(8,10)` target의 평균과 표준편차를 계산한다.

```text
target_normalized = (action - target_mean) / target_std
```

추론 시 checkpoint에 저장된 mean/std로 물리 단위를 복원한다.

```text
action_physical = prediction * target_std + target_mean
```

### 6.3 Loss와 optimizer

- loss: `nn.L1Loss()`
- optimizer: AdamW
- spatial4 기본 설정: 30 epochs, batch 32, lr `3e-4`
- best validation checkpoint와 last checkpoint를 모두 저장

Validation L1이 낮다는 사실만으로 로봇 성공을 주장하지 않는다. 이미지 위치 반응, IK, geometry, timing과 closed-loop 검증을 별도로 수행해야 한다.

### 6.4 학습 예시

```powershell
python AI/tools/train_relative_chunk_bc.py `
  --data AI/datasets/<V10_DATASET> `
  --config AI/configs/train/relative_chunk_bc_spatial4.yaml `
  --device cuda `
  --out AI/checkpoints/relative_chunk/<NAME>.pt
```

노트북은 위 명령을 대신 실행하지 않는다. 노트북 산출물의 `dataset.json`과 verifier 결과를 확인한 뒤 별도 학습 작업으로 실행한다.

## 7. 왜 8개를 예측하는가

단일 step만 예측하면 정책은 바로 다음 움직임만 학습하고 frame마다 출력이 흔들릴 수 있다. 8-step chunk는 접근, 파지, 리프트처럼 이어지는 짧은 미래 구조를 함께 표현한다.

```text
관측 2개
   |
   v
미래 계획 8개
접근 · 접근 · 닫기 · 리프트 ...
```

하지만 8개를 예측한다고 8개를 항상 전부 open-loop로 실행하는 것은 아니다.

## 8. 8개 예측과 N-step 실행

현재 실행 경계는 receding-horizon 형태다. 정책은 8개를 예측하고 실행기는 전체 chunk를 검사한 뒤 앞부분 N개만 실행한다. 남은 목표는 버리고 새 관측으로 다시 추론한다.

```text
새 관측 2개
   |
   v
미래 목표 8개 예측
   |
   v
8개 전체 검사
   |
   v
앞부분 N개 실행
   |
   v
나머지 폐기
   |
   v
재관측 · 재추론
```

이 구조는 최적화 기반 MPC는 아니다. 학습 정책이 매 cycle 새로운 chunk를 출력하는 폐루프 재계획 방식이다.

### 8.1 `execute_steps=1`

```text
action[0] 실행
action[1:8] 폐기
새 관측 후 재추론
```

현 Track A MuJoCo 진단 기본값이다.

### 8.2 `execute_steps=4`

```text
action[0:4] 실행
원시간 기준 약 t+0.1 .. t+0.4 s
action[4:8] 폐기
새 관측 후 재추론
```

오프라인 prefix 선택과 MuJoCo 진단 경로가 있다. 실물 로봇의 최종 재관측 주기로 승인된 값은 아니다.

### 8.3 `execute_steps=8`

전체 0.8초 목표를 실행한 뒤 재관측하는 방식이다. 관측 없이 실행하는 시간이 길어지므로 환경 변화와 정책 오차에 더 취약하다. 현 실물 기본값으로 확정되지 않았다.

### 8.4 Track B 인덱스 규약과 혼동 금지

v10의 `action[0]`은 현재 pose가 아니라 첫 미래 목표 `t+0.1s`다. 따라서 v10의 첫 네 미래 목표는 `[0:4]`다.

기존 Track B 평가 코드의 `absolute[1:5]`는 출력 index 0을 현재 pose로 보는 별도 정책 규약이다. 해당 slice를 v10에 그대로 복사하면 첫 미래 목표를 버리고 다섯 번째 목표를 포함하는 off-by-one 오류가 발생한다.

## 9. 실행 전 전체 chunk 검증

N개만 실행하더라도 원칙적으로 8개 전체를 먼저 검사한다.

- 상대 목표와 timestamp가 finite인지
- 모든 action timestamp가 현재 관측보다 미래인지
- 6D rotation을 유효한 회전행렬로 복원할 수 있는지
- gap이 `[0.0, 0.09] m` 범위인지
- 각 미래 EEF 목표에 대한 IK가 성공하는지
- 관절 범위와 설치별 wrist 제한을 만족하는지
- 목표 간 jump가 제한을 넘지 않는지
- 속도·가속도와 필요한 time scale이 허용 범위인지
- 관측과 명령이 stale 상태가 아닌지

후속 waypoint 하나라도 실패하면 전체 chunk를 거부하는 fail-closed 경계를 사용한다. 검사하지 않은 뒤쪽 목표를 남긴 채 앞부분만 임의 실행하지 않는다.

## 10. 정책 출력부터 로봇 실행까지

```text
RGB · proprio history
        |
        v
RelativeChunkBCPolicy.predict_action
        |
        v
정규화 해제, physical `(8,10)`
        |
        v
현재 TCP 기준 absolute EEF target 복원
        |
        v
6D rotation 직교화 · rotation matrix 복원
        |
        v
5축 IK + absolute gap
        |
        v
관절·jump·timing 검사
        |
        v
앞부분 N개 선택
        |
        v
ROS/driver 경계
```

정책 출력은 최종 모터 command가 아니다. 현재 로봇 pose에 합성하고 IK와 실행 가능성 검사를 거친 뒤 관절 목표로 변환해야 한다.

## 11. 현재 구현 상태와 미완료 경계

### 구현됨

- `(8,10)` relative EEF chunk 데이터 생성·검증
- RGB/proprio loader와 BC 학습기
- baseline `1x1` 및 spatial4 image encoder
- checkpoint target 정규화·역정규화
- 현재 TCP 기준 미래 target 복원
- 6D rotation 복원
- 8개 target IK·관절·timing 사전검사
- 앞부분 N개 선택
- MuJoCo에서 실행 후 재관측·재추론 진단

### 실물 경로에서 미완료 또는 미승인

- 8개 관절 목표를 하나의 ROS trajectory goal로 송신하는 완성 경로
- arm과 gripper의 다점 동기 실행
- 연속 충돌 검사와 실제 tracking 검증
- 실물 환경에서의 `execute_steps` 확정
- camera-to-TCP와 gap scale의 물리 배포 승인
- 실제 하드웨어 안전 한계 승인

현재 v10 상태가 `PROVISIONAL_CALIBRATION_NOT_FOR_PHYSICAL_DEPLOYMENT`라면 데이터 계약이 통과했더라도 실물 명령 승인으로 해석하지 않는다.

## 12. 확인된 진단 결과와 해석 제한

- spatial4 held-out local-x MAE: baseline `12.71mm`에서 `4.15mm`
- fixed-pose object-only 방향: baseline `14/28`, spatial4 best/last `24/28`, `26/28`
- 두 cycle 진단: 첫 prefix는 lift했지만 두 번째 예측이 gripper를 `47.3--50.8mm`로 다시 열어 stable grasp `0/4`
- privileged simulator contact clamp 진단에서 gap reopening이 좁은 조건의 주요 실패 원인임을 분리했지만 배포 가능한 해결책은 아님

이 결과는 simulation 또는 held-out data 진단이다. 실물 로봇 성공률, 새로운 물체 위치 일반화 또는 배포 승인으로 인용하지 않는다.

## 13. 인수인계 체크리스트

### 데이터 수령 후

- [ ] `dataset.json` schema가 `umi_relative_chunk/0.2.0-provisional`인지 확인
- [ ] observation horizon 2, action horizon 8, rate 10Hz 확인
- [ ] verifier의 `problems`가 빈 배열인지 확인
- [ ] dataset status와 calibration limitation 확인
- [ ] reference bundle checksum 확인
- [ ] RGB와 source provenance의 프라이버시 확인

### 학습 전

- [ ] episode 단위 train/validation split 유지
- [ ] v10 index 0이 첫 미래 목표라는 규약 유지
- [ ] action을 controller command나 `state[t+1]`로 재해석하지 않음
- [ ] 위치·회전은 상대값, gap은 절대값임을 유지
- [ ] baseline과 spatial4 결과를 같은 split·seed·loss 조건으로 비교

### 실행 전

- [ ] checkpoint의 schema·shape·mean/std 확인
- [ ] 현재 TCP 기준으로 각 미래 목표를 독립 복원
- [ ] 8개 전체에 대해 IK와 관절·timing 검사
- [ ] `execute_steps`와 index slice를 명시
- [ ] N개 실행 후 남은 chunk를 버리고 새 관측으로 재추론
- [ ] simulation 결과를 hardware 성공으로 표현하지 않음

## 14. 주요 파일

| 목적 | 파일 |
|---|---|
| 로컬 raw→ORB→v10 notebook | `AI/notebooks/build_private_orbslam_v10.ipynb` |
| notebook 실행 안내 | `AI/docs/HOWTO_private_orbslam_v10_notebook.md` |
| 상대 궤적 계약 | `AI/docs/SPEC_umi_relative_target_trajectory_0914.md` |
| 데이터셋 loader | `AI/data/relative_chunk_dataset.py` |
| 정책 네트워크·추론 wrapper | `AI/policy/relative_chunk_bc.py` |
| 학습기 | `AI/tools/train_relative_chunk_bc.py` |
| spatial4 config | `AI/configs/train/relative_chunk_bc_spatial4.yaml` |
| 데이터 계약 verifier | `AI/tools/verify_umi_relative_dataset.py` |
| EEF chunk 사전검사 | `AI/umi/relative_robot_preflight.py` |
| joint chunk handoff | `AI/umi/joint_chunk_handoff.py` |
| N-step runtime 경계 | `AI/umi/chunk_runtime.py` |
| 실행 경계 설명 | `AI/docs/UMI_arm_command_boundary.md` |

## 15. 팀 공유용 초단기 요약

```text
입력
최근 RGB 2장 + 상대 TCP/gripper state 2개

정책
CNN image encoder + proprio fusion + MLP

출력
현재 TCP 기준 미래 EEF target 8개
각 target = xyz 3 + rotation6d 6 + absolute gap 1

학습
episode split + target standardisation + L1 Behavior Cloning

실행
8개 전체 검사 → 앞 N개 실행 → 나머지 폐기 → 재관측·재추론

주의
v10 action[0]은 현재 pose가 아니라 첫 미래 목표다.
정책 출력은 motor command가 아니라 IK 전 EEF target이다.
현재 경로는 provisional이며 실물 배포 승인이 아니다.
```
