# S22–MuJoCo 손목 영상 시각 정렬 계측 (2026-09-17)

관련 Jira: `S15P21A103-154`(UMI 상대 청크 경로),
`S15P21A103-171`(8-step 시뮬레이션 검증).

## 목적

실물 v10 상대궤적 정책을 MuJoCo에서 평가하거나 시뮬레이션 사전학습에 사용하기
전에, S22 학습 영상과 `cam_wrist` 영상의 물체 중심·화면 점유율·카메라 방향을
같은 기준으로 비교한다.

이 계측은 시각 도메인 프록시를 찾는 작업이다. 결과값은 **실측 IMX708 optical
extrinsic이 아니며**, 로봇 하드웨어 캘리브레이션으로 사용할 수 없다.

## 입력과 고정 조건

- 데이터: `umi_real_relative_20260911_v10`
- 등록: `relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json`
- 대상: 고정 접근 시작이 scene gate를 통과한 8편
- 적합/홀드아웃: 에피소드 단위 6편/2편 고정 분할
- 실물 관측: canonical v10 이미지의 최대 yellow/orange 연결 성분 bbox
- 시뮬 관측: 등록된 40 × 40 × 76.7 mm 박스의 카메라 투영 bbox
- 추가 게이트:
  - 대상 내부 5개 ray × 에피소드에서 첫 visible geom이 대상물인 비율 = 100%
  - world `+Z`가 화면 위쪽으로 투영되는 오차의 p95 ≤ 20°

## 기존 카메라 진단

`configs/so101.yaml`의 잠정 `cam_wrist`는 S22 학습 영상과 맞지 않았다.

| 지표 | 실물 중앙값 | 기존 MuJoCo 중앙값 |
|---|---:|---:|
| bbox 중심 x/y | 0.512 / 0.624 | 0.496 / 0.438 |
| bbox 너비/높이 | 0.230 / 0.627 | 0.271 / 0.173 |
| bbox 면적 비율 | 14.22% | 4.69% |
| world +Z 화면 위 오차 | - | 중앙/p95 87.31° / 87.31° |
| 대상 visible ray 비율 | - | 2.5% |
| 홀드아웃 bbox loss | - | 0.9531 |

즉 기존 카메라는 물체를 약 3배 작게 보고, 화면 방향이 약 90° 돌아가 있으며,
로봇 구조물이 대상물을 대부분 가린다. 이 조건의 정책 rollout은 실물 v10 정책의
시각 일반화 성능으로 해석할 수 없다.

## 탐색 과정에서 제거한 잘못된 후보

1. bbox 투영만 최소화한 후보는 수치 loss를 `0.9518 → 0.1551`로 낮췄지만,
   실제 렌더에서 물체가 로봇 구조물 뒤에 가렸다. 투영 사각형은 가림을 검출하지
   못하므로 채택하지 않았다.
2. visible ray 60%를 허용한 후보는 방향을 세웠으나 물체 아래 절반이 가렸다.
   실제 S22 프레임과 다르므로 가시성 게이트를 90%로 올렸다.

## 최종 후보와 판정

초기 상위 256개만 검사했을 때 90% 가시성 후보를 놓칠 수 있어, 상위 2,048개까지
ray 검사를 확대했다. 이어서 가림이 조금이라도 남은 후보를 제거하기 위해 최종
가시성 기준을 100%로 올려 30,000개 국소 후보를 다시 탐색했다.

| 지표 | 결과 |
|---|---:|
| bbox 전체 loss | 0.08495 |
| 홀드아웃 bbox loss | 0.06749 |
| bbox 중심 x/y 중앙값 | 0.528 / 0.633 |
| bbox 너비/높이 중앙값 | 0.300 / 0.510 |
| bbox 면적 비율 중앙값 | 15.29% |
| world +Z 화면 위 오차 중앙/p95 | 1.30° / 1.30° |
| 대상 visible ray 비율 | **100% (40/40)** |
| 방향 게이트 | PASS |
| 100% 가시성 게이트 | **PASS** |

초기 탐색기는 `qpos`를 바꾼 뒤 ver1 손가락 몸체를 현재 gap으로 동기화하지 않아
실행 시 가림을 `40/40`으로 과대평가했다. 실제 실행 경로와 동일하게
`sync_gripper_collision_proxy()`를 호출하도록 고친 뒤 다시 탐색했고, 위 수치는
수정된 탐색기의 결과다. 따라서 카메라 중심·크기·FOV·방향의 1차 정렬 게이트를
통과했다. 이 값은
`configs/real/umi_s22_mujoco_visual_alignment_provisional_20260917.json`에
시각 도메인 프록시로 저장한다. 실측 optical extrinsic으로 승격하지 않는다.

중간 87.5% 후보에서 가려진 ray의 첫 충돌 geom은
**`ver1_static_cover_top`** 이었다. 커버를 임의로 숨기지 않고 카메라 위치를 더
탐색해 최종 후보에서는 40개 ray 모두 `target_object_geom`을 먼저 보게 했다.

## 재현

```powershell
python AI/tools/fit_s22_mujoco_visual_alignment.py `
  --data AI/datasets/umi_real_relative_20260911_v10 `
  --registration AI/out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json `
  --out AI/out/s22_mujoco_visual_alignment_runtime_visible100_20260917 `
  --candidates 30000 `
  --refine-from AI/out/s22_mujoco_visual_alignment_visible100_20260917/s22_mujoco_visual_alignment.json `
  --max-position-delta-m 0.12 `
  --max-rotation-delta-deg 18 `
  --fovy-min-deg 25 `
  --fovy-max-deg 80 `
  --min-visible-ray-fraction 1.0 `
  --shortlist-size 2048
```

계산 검증:

```powershell
python AI/tools/check_s22_mujoco_visual_alignment.py
```

## 다음 조치

1. 노란 패키지 외형·밝은 작업대·조명/배경 랜덤화를 추가한다.
2. 같은 6/2 분할에서 RGB 통계와 대상물 가시성을 다시 확인한다.
3. 이후 시뮬 사전학습 → v10 파인튜닝을 GPU 서버에서 수행한다. 체크포인트 추론과
   5조건(`normal/noise/blackout/freeze/mean_fill`) rollout은 로컬 CPU에서 수행한다.
4. `normal`의 Wilson 95% 하한이 공간정보 없는 `mean_fill/noise`의 상한보다 높고,
   교란 대비 15%p 이상 하락하며 여러 학습 시드에서 재현될 때만 시각 의존성을
   통과로 판정한다.

## 외형·조명·배경 보강

최종 카메라 후보를 별도 visual-domain config로 연결하고 다음 공칭 외형을 적용했다.

- 대상물: 구운감자 케이스를 구분할 수 있는 yellow/gold RGBA
- 대상물 4면: 오렌지색 인쇄 영역을 대신하는 비충돌 표면 패치
- 작업대: 실물 영상에 가까운 밝은 회백색
- 바닥: 기존 checker texture를 유지하되 중성색 RGBA 적용
- 광원: RGB diffuse를 0.82로 고정
- 배경: 컵·휴대폰·용기·밝은 작업실 배경을 대신하는 primitive proxy
- 학습용 잠정 랜덤화 범위: 카메라 ±5 mm/±2°, RGBA ±0.12,
  광원 위치 ±0.10 m, diffuse ±0.12

추가 primitive는 전부 `contype=0`, `conaffinity=0`, `mass=0`이다. 테스트에서 기존
joint range, actuator control range, 대상물/그리퍼 질량, 대상물/양쪽 pad 마찰이
동일함을 확인한다. 따라서 이 보강은 접촉·관성·IK 결과를 바꾸지 않는다.

수정된 카메라와 실행 시 동기화된 ver1 손가락으로 동일 6/2 분할을 다시 렌더했다.

| 홀드아웃 영상 통계 | 실제 S22 | MuJoCo proxy |
|---|---:|---:|
| RGB mean | 141.89 / 133.00 / 122.68 | 160.05 / 155.77 / 144.45 |
| RGB std | 64.84 / 63.02 / 65.62 | 77.50 / 79.13 / 93.73 |
| gray mean / std | 134.48 / 62.12 | 155.76 / 77.26 |
| edge fraction (`|gradient| > 20`) | 0.1358 | 0.03195 |
| 대상 내부 visible ray | - | **40/40** |

물체 가시성과 화면 배치는 통과했지만, 시뮬레이션은 아직 실제 영상보다 밝고
고주파 edge가 약 4배 적다. 즉 primitive proxy는 단색 박스보다 개선됐지만 실제
패키지 인쇄·사람·케이블·도구의 다양성을 대체하지 못한다. 이 차이는 숨기지 않고
시뮬 사전학습의 visual randomization 범위와 평가 조건으로 남긴다.

재현 계측:

```powershell
python AI/tools/measure_s22_mujoco_visual_domain.py `
  --data AI/datasets/umi_real_relative_20260911_v10 `
  --registration AI/out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json `
  --visual-domain AI/configs/real/umi_s22_mujoco_visual_alignment_provisional_20260917.json `
  --out AI/out/s22_mujoco_visual_domain_final_20260917.json
```

`render_relative_chunk_rollout.py --visual-domain <config>`로 기존 물리·접촉 설정을
바꾸지 않고 이 시각 프록시만 적용할 수 있다. recorded-oracle 1-cycle 관통을 로컬
CPU에서 확인했으며, 학습 정책 추론은 사용하지 않았다.

- 실물 관측 비교 렌더:
  `out/relative_chunk_v10_150943_visual_domain_oracle_20260917.gif`
- MuJoCo 손목 관측 렌더:
  `out/relative_chunk_v10_150943_visual_domain_simcam_oracle_20260917.gif`

현재 보강은 primitive 수준이다. 실제 패키지 인쇄 텍스처와 작업실 RGB 다양성을
완전히 재현하지 않으므로, 시뮬 사전학습 성능을 실물 전이 성능으로 해석하면 안 된다.

## 정렬 후 기존 v10 정책 5조건 재평가

기존 seed-0 best-val 체크포인트를 그대로 두고 K=4 scene-valid 8편에서
`normal/noise/blackout/freeze/mean_fill`을 로컬 CPU로 다시 측정했다. 모든 조건은
동일 체크포인트·등록·물리·score를 사용하고 추론 RGB만 바꾼다.

첫 실행에서 `--no-video`가 MuJoCo observation renderer까지 끄는 결함을 발견했다.
그 실행에서는 정책 RGB가 0 배열이어서 normal/blackout/freeze/mean-fill이 완전히
같은 명령을 냈다. 그 결과는 무효다. `--no-video`는 GIF 저장만 끄고, learned policy가
simulation RGB를 쓸 때는 renderer를 유지하도록 수정하고 회귀검사를 추가했다.

수정 후 유효 결과:

| 조건 | stable lift | Wilson 95% | bilateral contact | 대표 실패 |
|---|---:|---:|---:|---|
| normal | 0/8 | [0.0%, 32.4%] | 5/8 | tip 5, displacement 2 |
| noise | 0/8 | [0.0%, 32.4%] | 7/8 | displacement 6, tip 2 |
| blackout | 0/8 | [0.0%, 32.4%] | 1/8 | displacement 6, geometry 2 |
| freeze | 4/8 | [21.5%, 78.5%] | 5/8 | no bilateral 3, tip 1 |
| mean_fill | 0/8 | [0.0%, 32.4%] | 1/8 | tip 5, geometry 2 |

`normal`이 공간정보 없는 대조군보다 좋아야 한다는 사전 판정 기준을 통과하지 못했다.
오히려 첫 simulation frame을 고정한 freeze만 4/8이었다. 이는 첫 화면의 물체 배치와
가시성은 맞췄지만, 동작 중 새 simulation RGB가 기존 real-only 체크포인트에 유익한
폐루프 관측으로 작동하지 않는다는 증거다. 원인은 궤적 전체 카메라/배경 분포 불일치,
정적 첫 화면 의존, 또는 둘의 결합일 수 있으며 현재 결과만으로 하나를 단정하지 않는다.

```text
valid report:
out/relative_chunk_v10_visual_aligned_suite8_rgbfix_20260917.json

invalid zero-RGB report (do not cite):
out/relative_chunk_v10_visual_aligned_suite8_20260917.json
```

비교 영상은 같은 `145912` 에피소드다. normal은 물체를 밀고 전도시키며, freeze는
양측 접촉을 유지해 약 50mm 들어 올린다.

- `out/relative_chunk_v10_visual_aligned_145912_normal_20260917.gif`
- `out/relative_chunk_v10_visual_aligned_145912_freeze_20260917.gif`

따라서 다음 단계는 기존 체크포인트를 더 평가하는 것이 아니라, 실제 시연의 전체
시간축에서 물체 bbox·카메라 움직임을 비교하고 그 범위를 시뮬 사전학습 데이터에
반영한 뒤 real v10으로 파인튜닝하는 것이다.

## 후속 실험: 이미지 유래 높이 프록시와 렌더 기준 카메라 정렬

위 `normal 0/8`은 당시의 작은 시각 프록시 조건이다. **그 결과를 최신 렌더
정렬 조건의 성능으로 인용하면 안 된다.** 실물 S22의 40mm 파지폭을 화면 척도
기준으로 삼아 물체 *보이는 높이*를 123mm로 추정했고, 이를 충돌·질량과 분리된
렌더 전용 상자로 구현했다. 실제 과자 케이스 높이를 캘리퍼로 잰 값은 아니다.
MuJoCo 충돌 물체 높이 76.7mm도 여전히 잠정값이다. 카메라는 실제 MuJoCo RGB
렌더를 목적함수로 다시 맞췄다.

| 계측 | 이전 시각 프록시 | 렌더 정렬 후 |
|---|---:|---:|
| recorded-oracle 8편, 대응 50프레임 bbox IoU 중앙 | 0.581 | 0.698 |
| 대응 bbox 높이 절대 오차 중앙 (정규화) | 0.174 | 0.0335 |

고정 seed-0 best-val v10 체크포인트를 동일한 K=4 근접 시작 8편에서 재평가한
결과는 `normal 8/8`, `noise 8/8`, `blackout 0/8`, `freeze 8/8`,
`mean_fill 7/8`이다. 화면 크기·배치 보정으로 파지 성공이 복구됐지만,
정상 이미지가 공간정보 없는 대조군보다 유의하게 좋지 않다. `blackout`만 실패한
것은 극단적으로 어두운 분포 밖 입력의 영향일 수 있으므로 시각적 위치 추론의
증거로 쓰지 않는다. 물체만 X/Y로 ±10mm 옮긴 3편×5위치에서는
`normal 12/15`, `noise 15/15`, `mean_fill 12/15`였다. 모두 시뮬레이션
진단이며, 실제 물체 위치 일반화나 하드웨어 파지 성공을 뜻하지 않는다.

근거 파일:

- `out/s22_mujoco_render_alignment_proxy123_20260917.json`
- `out/s22_mujoco_dynamic_alignment_proxy123_renderfit_suite8_20260917.json`
- `out/relative_chunk_v10_visual_proxy123_renderfit_suite8_20260917.json`
- `out/relative_chunk_v10_visual_proxy123_offset10mm_suite3x5_20260917.json`

## 다음 경로의 파일럿: 물체 위치와 목표를 함께 바꾸기

기존 v10 근접 시작에서는 시작 로봇 자세가 물체 위치의 힌트가 될 수 있다.
`tools/build_sim_relative_localization_pilot.py`는 한 기록 시연의 **같은 로봇 시작**을
고정하고 물체 XY만 변경한다. 기록된 8개 미래 TCP 위치에 동일한 세계 좌표
변위를 정확히 한 번 더해 시작 TCP 기준 상대 목표로 다시 표현한다. 회전과 절대
gap은 유지한다. 카메라는 변경된 물체를 MuJoCo에서 새로 렌더한다. 두 관측
프레임과 proprio는 정적 복제이며 타임스탬프는 합성 시계이므로, 원본 사람
시연의 시간 연속 데이터나 실측 시연으로 해석하면 안 된다.

2편×3개 X 오프셋(-10/0/+10mm) 스모크에서 6/6 샘플이 생성됐다. 같은 시작
proprio를 유지하며 물체 이동 10/20mm에 목표 청크의 이동 크기도 10/20mm였고,
RGB 및 물체 bbox 중심이 모두 변했다. `prepare_arm_commands`의 8-step 전체
IK·관절 범위·점프·위치 5mm·축/roll 각 5° 검사는 6/6 통과했다. 이 결과는
**기하 검사만** 의미한다. 속도·가속도, 충돌, 양측 패드 접촉, 전도와 안정 리프트는
미검증이다. 같은 원본 에피소드에서 파생된 오프셋 변형을 train/val 양쪽에
넣으면 누출이 생기므로 이후에는 원본 에피소드 단위로 분할해야 한다.

파일럿 인덱스/에피소드에는 `CANDIDATE_NOT_TRAINING_READY`를 명시했고,
`RelativeChunkDataset` 로더는 이를 거부한다. **아직 시뮬 사전학습을 시작하지
않는다.** 다음 게이트는 이동된 목표 청크를 정책 없이 물리 재생해 양측 접촉·전도·
안정 리프트를 비교하고, 시간 재조정과 원본 에피소드 단위 분할을 검증하는 것이다.

첫 8-step 청크만 정책 없이 재생한 소규모 물리 진단도 완료했다. 기존 시뮬 전용
2mm preload를 명시적으로 적용하고 로봇 속도·가속도 한계에 맞춰 청크 실행
시간을 2.59~3.17배 늘렸다. 6/6에서 양측 패드 접촉이 있었고 최종에도 유지됐다.
물체 최대 기울기는 2.5~3.9°, 들기 전 수평 이동은 2.6~4.1mm였다.
`rec_20260911_150943`의 세 변형은 50mm 이상 들어 올렸지만,
`rec_20260911_145912`의 세 변형은 첫 청크 끝에서 약 38mm였다. 따라서
**첫 청크 기준 3/6**이지 전체 시연 성공률이 아니다. 나머지 시연 구간을
재생하거나 새 합성 전문가 궤적을 검증하기 전까지 학습 후보를 승인하지 않는다.
측정 파일은 `out/sim_relative_localisation_candidate_iksmoke_physics_20260917.json`,
실행기는 `tools/check_sim_relative_localization_physics.py`다.

- 생성기: `tools/build_sim_relative_localization_pilot.py`
- 결합 검사: `tools/check_sim_relative_localization_pilot.py`
- 스모크 샘플: `datasets/sim_relative_localisation_candidate_iksmoke_20260917/`

## 물체만 이동한 전체 기록 궤적의 정책 없는 재생

첫 청크 진단에 이어, **동일한 로봇 K=4 시작 자세**에서 물체만 이동하고
기록된 TCP 목표의 세계 좌표에도 같은 이동량을 적용해 전체 궤적을 재생했다.
`umi/counterfactual.py`는 각 기록 행의 기준 TCP 앵커를 첫 미래 스텝에서
복원한다. 매 제어 주기에는 기록된 미래 세계 목표에 물체 변위를 **한 번만**
더한 뒤 현재 실제 TCP 기준 상대 청크로 표현한다. 따라서 재계획할 때마다
10mm가 누적되는 오류를 피한다. 이 앵커 복원은 v10의 첫 미래 스텝이 다음
기록 앵커라는 전제에 따른 것이며 별도 실측 목표 명령을 복원한 것은 아니다.

선택된 scene-valid 8편(`145625`, `145912`, `150034`, `150753`, `150833`,
`150943`, `150948`, `151043`)에서 기준 위치와 세계 X/Y 각각 ±10mm를
시험했다. 총 40개 조건 모두 정책 없이 전체 8-step IK 검사를 시도하며 한
스텝씩 실행했고, 기록 종단의 tail도 처리했다. 기존 시뮬레이션 전용 2mm preload,
잠정 양측 패드 collision proxy, 속도·가속도에 따른 실행 시간 확대를 유지했다.

| 진단 항목 | 40조건 결과 |
|---|---:|
| 양측 접촉을 유지한 안정 리프트 | 40/40 |
| 전체 궤적 기하/IK 오류 없이 안정 리프트 | 39/40 |
| 기록 종단 tail의 기하/IK 오류 | 1/40 |
| 최종 들어올림 높이 | 58.8–103.6mm |
| 들어올리기 전 물체 수평 이동 최대값 | 조건별 1.4–11.5mm |
| 물체 기울기 최대값 | 조건별 1.9–9.2° |

근거는 `out/counterfactual_oracle_k4_x10_suite8_20260917.json` 및
`out/counterfactual_oracle_k4_y10_suite8_20260917.json`이고, 조건별 상세
리포트는 각각의 `_episodes/` 폴더에 있다. `tools/eval_counterfactual_oracle_suite.py`
는 조건별 결과를 즉시 저장하고 같은 설정으로 재개할 수 있다. 시작 스텝과
접촉·리프트 판정은 기존 `render_relative_chunk_rollout.py`를 그대로 사용한다.
예외 1건은 `145625`의 X=-10mm에서 리프트가 성립한 **뒤** 종단 tail의
5번째 목표가 관절 한계를 벗어난 경우다. 진단용 재생기는 유효한 앞부분만
실행했으므로 물리 리프트는 성공으로 남았지만, 전체 기록 목표를 모두 실행할
수 있다는 판정은 실패다. 위의 40/40 물리 리프트와 39/40 전 궤적 기하 통과를
구분한다. 학습 정책의 fail-closed 전체 청크 검사에는 이 tail 완화가 적용되지
않는다.

초기 `150943` 시험에서 tail을 실행하지 않아 약 20mm에서 끝난 값은
**부분 궤적**의 결과였으며 위 전체 궤적 실험으로 대체한다. 이 40/40은
선택된 8편과 제한된 ±10mm 물체 이동에 대한 *recorded-oracle 물리 진단*이다.
학습된 정책의 위치 일반화, 실제 구운감자 케이스 파지 또는 전체 70편 성공률이
아니다. 첫 화면의 물체 위치 정보만 바꾸고 목표도 정답에 맞춰 이동시켰으므로
시각 정책이 그 변위를 알아냈다는 뜻도 아니다. 물체 형상·질량·마찰과
collision proxy, 카메라 보정, 2mm preload는 여전히 잠정이다.

다음에는 전체 궤적의 시간·영상·행동 스트림을 합성 시연으로 만들 수 있는지
검증해야 한다. 현재 `sim_relative_localisation_candidate_iksmoke_20260917`은
첫 정적 관측과 한 청크의 **학습 불가 후보**이고, 이 40건을 그대로 학습 데이터로
간주하지 않는다. 합성 스트림을 만든다면 원본 에피소드 단위 train/val 분리,
실제 실행 시간과 관측 시각의 일치, 양측 접촉·전도·리프트 게이트를 먼저
확인한다. 그 후의 학습 및 동일 체크포인트 이미지 교란 검사는 별도 단계다.

## 동적 MuJoCo 관측 스트림 1편 파일럿

전체 궤적 재생기에 `--candidate-stream-out`을 추가해 정책 없이 실행되는 동안
매 재계획 시점의 실제 MuJoCo 손목 RGB·TCP 상대 proprio·미래 기록 목표·시각을
NPZ에 함께 저장했다. 첫 주기는 진짜 과거 관측이 한 장뿐이므로 버리고,
서로 다른 두 시각의 관측이 있는 주기부터 저장한다. 관측 timestamp는 실제
MuJoCo 시계, 미래 action timestamp는 매 주기에 계산된 속도·가속도 제한 및
실행 시간 확대를 반영한 **예정 목표 시각**이다. 미래 8개 목표는 기록된
인간 TCP 궤적을 물체 이동만큼 옮긴 것이지 실제 관절 `ctrl`이나 실행 결과가
아니다. gap 라벨에 시뮬레이션 전용 2mm preload를 쓰지 않았다.

`rec_20260911_145912`, 물체 세계 X +10mm, K=4 고정 시작에서 1편을
생성했다. `tools/check_sim_relative_dynamic_candidate.py` 검사 결과:

| 항목 | 결과 |
|---|---:|
| 배열 `image/proprio/action` | `(8,2,3,224,224)` / `(8,2,10)` / `(8,8,10)` |
| 배열 계약 오류 | 0 |
| 영상 내 물체 검출 | 8/8 현재 관측 |
| 두 프레임 RGB 절대차 평균 | 6.43 / 255 |
| 실제 관측 간격 | 0.448초 중앙 |
| 첫 예정 목표 ↔ 다음 실제 관측 시각 차이 최대 | 9.91ms |
| 대응 recorded-oracle | 안정 리프트, 기하 오류 없음 |

후보는 `out/sim_relative_dynamic_candidate_145912_xplus10_20260917.npz`,
sidecar는 같은 이름의 `.json`, 물리 리포트는
`out/sim_relative_dynamic_oracle_145912_xplus10_20260917.json`이다.
**학습 준비 상태가 아니다.** 원본 v10은 10Hz(0.1초 간격)인데 이 실행의 관측
간격은 약 0.448초다. 이름만 10Hz로 지정해 섞으면 정책의 관측 속도와
미래 청크 시간축이 달라진다. 다음 게이트는 로봇 시간축의 일정한 제어·관측
주기를 정하거나, 실제 실행된 궤적을 그 주기로 재표본화해 액션 목표 시각과
영상 시각을 다시 검증하는 것이다. 그 전에는 여러 편으로 확대하거나 학습
데이터셋 인덱스를 만들지 않는다. 후보에는
`CANDIDATE_NOT_TRAINING_READY`, `human_demonstration=false`를 유지한다.

## 실제 10Hz 관측을 기록한 시뮬레이션 후보 2편

이전 후보의 약 0.448초 관측 간격 문제를 분리하기 위해, MuJoCo 적분
간격 2ms에 맞춰 정책 없는 oracle 실행의 제어율을 50Hz(제어 tick당
10 적분 스텝, 정확히 20ms)로 지정했다. **5번째 제어 tick마다 실제로
렌더링한 RGB와 실제 관절 FK에서 얻은 TCP·gap을 기록**했다. 이 방식은
이미지·상태를 사후 보간하거나 마지막 값을 패딩하지 않는다. 원본 v10의
인간 시연 시각을 보존한다는 뜻도 아니다. 제어율 변화는 물리 조건도
바꾸므로 접촉·리프트를 새 실행에서 다시 판정했다.

고정 K=4 시작과 2mm 시뮬레이션 전용 preload로 두 편을 로컬 CPU에서
독립 재생했다. 아래의 대표 수치는 `rec_20260911_145912`, 물체 X +10mm다.

| 항목 | 새 50Hz 물리 실행 / 10Hz 기록 |
|---|---:|
| 실제 시뮬레이션 제어 tick | 192 |
| 실제 10Hz 영상·TCP·gap 샘플 | 39개 |
| H=2, K=8 완전 미래 후보 | 30행 |
| 현재 관측 간격 최소/중앙/최대 | 0.100000 / 0.100000 / 0.100000초 |
| 영상 내 물체 검출 | 30/30 현재 영상 |
| 관측 두 장 RGB 절대차 평균 | 1.63/255 |
| 전체 8-step 기하/IK 오류 | 없음 |
| 양측 패드 접촉·안정 리프트 | 통과 / 최종 102.3mm |
| 리프트 전 최대 물체 수평 이동 / 최대 기울기 | 2.56mm / 5.65° |
| 배열·시간축·실행 TCP 연결 검사 | 오류 0건 |

근거: `out/sim_relative_10hz_oracle_145912_xplus10_20260917.json`,
`out/sim_relative_10hz_candidate_145912_xplus10_20260917.npz`와 그
sidecar `.json`. `tools/check_sim_relative_10hz_candidate.py`가 검사한다.
두 번째 `rec_20260911_150943`, 물체 Y +10mm도 112 제어 tick에서
23개 실제 10Hz 샘플과 14개 완전 미래 행을 얻었다. 14/14 현재 영상에
물체가 보였고, 시간축·실행 TCP 연결 검사 오류는 0건이었다. 새 물리
실행에서도 기하 오류 없이 양측 접촉·안정 리프트를 통과했다(최종
59.5mm 리프트, 리프트 전 수평 이동 최대 2.36mm, 기울기 최대 3.23°).
근거는 같은 접두사의
`out/sim_relative_10hz_oracle_150943_yplus10_20260917.json` 및
`out/sim_relative_10hz_candidate_150943_yplus10_20260917.npz`다.
미래 `(8,10)` 값은 **미래에 실제 도달한 시뮬레이터 TCP pose와 gap**을
현재 도달 TCP에 상대화한 것이다. 기록된 인간의 미래 명령이나 로봇
`ctrl`이 아니다. 이 값은 두 과거 상태만으로 외삽될 가능성이 있고,
RGB 차이가 작다는 사실만으로 시각 의존성을 입증할 수 없다.

따라서 sidecar의 `CANDIDATE_NOT_TRAINING_READY`, `training_ready=false`,
`human_demonstration=false`를 유지하고 데이터셋 인덱스를 만들지 않았다.
다음 게이트는 다른 시연·물체 변위로 범위를 넓혀 동일한 시간·시야·물리
검사를 반복하고, 에피소드 단위 분할과 이미지 의존성 대조군을 설계하는
것이다. 한 편의 물리 성공을 학습 정책 성공 또는 실물 검증으로 읽지 않는다.
