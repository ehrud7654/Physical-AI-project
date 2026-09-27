# 0922 실물 정책 그리퍼 조기 폐쇄 진단

## 결론

조기 폐쇄는 한 원인이 아니다.

1. **원 정책 출력 자체가 접근 중 36--44mm까지 감소한다.** 학습/일반화 문제다.
2. **실행부 latch가 첫 사이클부터 켜진다.** 잘못된 결정을 되돌리지 못하게 하고
   25 또는 50 tick을 더 닫는다.
3. 로그에서는 그리퍼 clamp가 0건이고 실제 tick이 명령을 따라간다. 하드웨어가
   임의로 38mm를 만든다는 근거는 없다. servo2 통신 오류는 별도 결함이다.

이 문서는 진단 결과이며 실물 실행 승인이나 안전 승인이 아니다.

로봇의 초기 개구 `90mm`를 시연 데이터의 최대 개구 약 `79mm`로 맞추는 첫 보정은
의도된 동작이며 결함으로 세지 않는다. 이 문서의 해결 대상은 그 이후, 물체에
충분히 접근하기 전에 원 정책 출력이 `79mm -> 약 38mm`로 감소하는 현상이다.

## 입력과 재현

- `runs_0922_logs.tgz`
  - SHA-256: `3ba42ea4cb220739dc8992814f080e34171be604580b5a7b1de373b2292192a5`
- 실행:

```bash
python AI/tools/analyze_realtime_gripper_logs.py --selftest
python AI/tools/analyze_realtime_gripper_logs.py \
  --logs runs_0922_logs.tgz \
  --out-dir AI/out/gripper_early_close_0922
```

도구는 로봇 드라이버를 import하지 않고 명령을 보내지 않는다. TAR도 풀지 않고
`cycles.jsonl`만 읽는다.

## 측정 결과

| 항목 | 결과 |
|---|---:|
| 전체 실행 | 10 |
| 실제 모터 실행 | 7 |
| 첫 사이클 60mm 초과에서 latch | 7/7 |
| 원 정책 출력이 45mm 이하까지 감소 | 6/7 |
| gap/tick 비교 | 592점 |
| 추정 변환 | `tick = 1763.0225 - 18.14524 * gap_mm` |
| 변환 최대 잔차 | 0.528 tick |
| 관절 clamp | 0건 |

첫 latch는 실물 7회 모두 cycle 0, action index 4에서 발생했다. 그때 원 정책 gap은
`77.38--78.97mm`였다. 6회는 이후 원 정책 gap 자체가 `36.01--43.21mm`까지
내려갔다. 따라서 latch/offset을 제거하면 추가 폐쇄 `25tick=약 1.38mm` 또는
`50tick=약 2.76mm`는 없어지지만, 접근 중 38mm를 출력하는 정책 문제는 남는다.

`live_far_124638`은 특히 판별력이 있다. 팔은 물체 쪽으로 충분히 전진하지 않고
하강했지만 원 정책 gap은 `78.97 -> 36.76mm`로 감소했다. 현재 정책에는
"물체에 도착한 뒤 닫기"가 보장되지 않는다.

예측 전용 E/F 모두 첫 gap의 상한이 정확히 `78.9718479mm`에 걸린다. 실제 시작
관측 tick `102--129`를 같은 선형 변환으로 환산하면 약 `90.1--91.5mm`다. 이는
로봇의 90mm 초기 상태를 수집 시연의 최대 개구 약 79mm에 맞추는 의도된 초기
정렬이다. 따라서 이 차이는 조기 폐쇄의 원인으로 판정하지 않는다.

로컬에 보존된 축수정 74편 v10 원본도 직접 열어 대조했다. 관측과 action gap의
최대는 모두 `75.68685mm`, 에피소드 첫 학습 앵커 최대는 `75.42170mm`였다. 즉 이
74편 원본의 개구 범위는 9/21 신규 수집분과 동일하지 않다. 90mm가 학습 라벨에
없다는 사실 자체는 결함이 아니다. 배포 체크포인트가 참조한 공식 Zarr의 gap
배열과 normalizer를 직접 열어, 초기 79mm 이후 언제 어떤 관측에서 38mm로 감소하는지
대조해야 최종 귀속할 수 있다.

별도의 캘리브레이션 미확정 사항도 확인했다. 이 v10의 `dataset.json`은
`gripper_scale_verified: false`이고, 생성 당시 문서도 빈 그리퍼 0--90mm 전행정
검증 전에는 실물 배포 근거로 쓰지 말라고 명시한다. 9/21의 캘리브레이션 후보
`rec_1789985675821_81c59aa8`을 기존 계측기로 읽으면 다음과 같다.

- 두 마커 동시 검출: `128/260` 프레임
- 비율법 전행정: `30.15mm`
- PnP 전행정: `30.36mm`
- 기존 계측기가 가정한 전행정: `90mm`

두 방법은 서로 일치하지만 이 클립은 약 30mm만 움직였다. 영상에서 실제 usable
범위인 약 79mm의 완전 개방부터 완전 폐쇄까지 수행됐는지와 마커 black-square
실측 크기가 모두 미확정이므로, 이 숫자로 현 gap을 재스케일하면 안 된다. 이 항목은
gap 절대값 캘리브레이션의 별도 미확정 사항이며 `79 -> 38mm` 조기 폐쇄의 직접
원인으로 사용하지 않는다.

9/21 신규 수집의 로컬 `gripper_report.json` 100편도 집계했다. 편별 최대 gap은
중앙 `71.664mm`, p95 `76.647mm`, 전체 최대 `78.971838mm`다. 실물 로그에서 E/F
정책 출력이 걸린 상한 `78.971848mm`와 `0.00001mm` 수준으로 일치한다. 따라서
정책의 첫 79mm 명령은 우연한 값이 아니라 **학습 데이터의 관측 상한을 그대로
재현한 정상적인 초기 목표**로 판정한다. 문제는 이후 접근 완료 전에 38mm 부근까지
감소하는 시점이다.

같은 100편에서 각 편 시작 10%의 gap 중앙을 open 기준으로 삼고 5mm 이상 감소한
최초 시점을 진단했다. 시작 open 중앙은 `70.873mm`(범위 `61.230--78.508`), 닫힘
시작 비율 중앙은 편 길이의 `0.536`(범위 `0.346--0.668`)이었다. 시연은 접근 전반을
포함하지만 "open" 자체가 로봇의 90mm 시작과 다르고, 정책에는 물체 도착을 강제하는
별도 phase/contact 조건이 없다. 실행 로그의 닫힘 시작 비율 `0.14--0.465`는 일부
시연보다 더 이르다. 특히 `live_far`에서도 같은 감소가 나타나므로, 정책이 물체까지의
실제 남은 거리보다 평균 시간·자세 진행도에 의존해 닫힘 phase를 앞당기는 현상과
일치한다.

제공된 `03_handoff_to_jetson.ipynb`도 확인했다. A 갈래는 9/21 데이터 96편 중
파지 최소 gap이 `28.3/27.6mm`인 두 편만 제외하고 94편을 Zarr 대상으로 삼았다.
그 게이트는 `width_max <= 90mm`와 `width_min in [30,55]mm`만 검사한다. 수집 장치의
usable 최대가 약 79mm인 것은 이번 조건에서 정상이다. 다만 접근 중 open 유지와
물체 도달 뒤 닫힘을 검사하는 phase/timing 게이트는 없다.

또한 지정 캘리브레이션 편의 빌더 산출값도 `40.543--70.471mm`, 관측 이동량 약
`29.93mm`다. 이 클립만으로 usable 최대 약 79mm의 전 구간을 검증할 수 없다. 노트북의 저장
상태에서는 Zarr 빌드 시작까지만 출력이 있고 감사·학습·패키징 셀의 실행 출력은
없으므로, 실제 배포 패키지의 값은 서버 산출물로 별도 대조해야 한다.

## 아직 로그만으로 할 수 없는 것

로그에는 추론에 사용한 RGB/H=2 관측 텐서가 없다. 따라서 동일 관측을 체크포인트에
다시 넣는 진짜 latch-off 폐루프 추론은 이 TAR만으로 재실행할 수 없다. 대신
`gripper_curves.csv`에 다음 세 값을 복원한다.

- `raw_policy_gap_mm`: latch 전 정책 출력
- `command_gap_mm`: latch/offset 적용 후 명령
- `observed_cycle_start/end_gap_mm`: 실제 tick을 같은 변환으로 환산한 값

원 정책 출력만으로도 6/7회가 45mm 이하이므로 학습 원인의 존재는 이미 분리된다.

## 서버에서 남은 확인

학습에 실제 사용한 **공식 UMI Zarr와 배포 체크포인트**를 같은 실행에서 검사한다.

```bash
python AI/tools/analyze_realtime_gripper_logs.py \
  --logs /path/to/runs_0922_logs.tgz \
  --dataset /path/to/training_dataset.zarr.zip \
  --checkpoint /path/to/so101_pick_v1.ckpt \
  --out-dir /tmp/gripper_0922_audit
```

확인할 값:

- `gap_observation_m`, `gap_action_m`, `episode_initial_gap_m`의 max/p99
- 접근 전(`preclose_gap_m`) 분포
- 체크포인트의 normalizer/scale/offset 텐서와 action gap 채널
- 데이터 또는 normalizer가 `0.0789718m`에서 포화하는지
- 수집 시연의 usable 최대 개구 약 `0.079m`가 Zarr와 역정규화까지 보존되는지
- 접근 구간에서 GT gap은 유지되는데 정책 gap만 먼저 감소하는 프레임이 어디인지

로컬 v10 원본을 함께 검사한 실행은 다음과 같다.

```bash
python AI/tools/analyze_realtime_gripper_logs.py \
  --logs runs_0922_logs.tgz \
  --dataset AI/datasets/umi_real_relative_20260918_v10_orbslam_cadtcp_video_aligned_provisional_74ep \
  --out-dir AI/out/gripper_early_close_0922
```

## 수정 순서

1. **정상 초기 보정과 조기 폐쇄 분리:** 로봇 시작 `90mm -> 시연 최대 약 79mm`는
   정상으로 유지한다. 이후 동일 관측을 재생해 GT gap, 원 정책 gap, latch 이후 명령,
   실제 관측 gap을 비교하고 `79 -> 38mm` 감소가 시작되는 프레임과 물체까지 남은
   거리를 측정한다. gap 절대값 캘리브레이션은 별도로 usable 전 구간 클립으로 닫는다.
2. **latch 수정:** 단순히 현재 gap보다 작은 명령이 나왔다는 이유로 latch하지 않는다.
   현재 코드는 첫 사이클 77--79mm에서 latch하므로 거부 대상이다. 사용된 최신 러너가
   아직 Git에 없어 해당 커밋을 받은 뒤 수정한다.
3. **정책 수정:** 물체 전후 거리 변주와 접근 구간을 보강하고, 파지 위치 도달 전 gap
   유지 손실/phase 표현을 추가한다. 현재처럼 평균 진행 순서만으로 닫는 정책은 먼
   물체에서 실패한다.
4. **오프라인 게이트:** 새로운 배치 홀드아웃에서 `거리별 닫힘 시작 오차`, 정상/교란
   이미지 차이, 먼 물체에서 open 유지 여부를 먼저 통과시킨다.
5. **실물 재시험:** 위 결과와 실행부 fail-closed 검증 후 감독하에서 진행한다.

45--50mm threshold만 하드코딩하는 것은 임시 진단일 뿐 최종 해결이 아니다. 먼 물체
실행에서도 정책은 결국 그 아래로 내려갔으므로, 거리/phase 조건 없이 threshold만
두면 실패 시점만 늦어진다.

## 2026-09-22 실행부 1차 수정

실물 러너와 분리해 재사용할 수 있는 fail-closed gap phase guard를 추가했다.

- 구현: `AI/umi/gripper_runtime_guard.py`
- 데이터별 설정: `AI/configs/real/gripper_guard_onsite260921_pick.json`
- 회귀 검사: `AI/tools/check_gripper_runtime_guard.py` (`11/11 PASS`)

현재 설정은 로봇의 `90mm -> 78.971848mm` 초기 정렬을 close 신호로 사용하지 않는다.
실행할 네 점 모두가 `50mm` 이하인 사이클이 2회 연속 확인될 때까지
`78.971848mm`를 유지하며, 확인 뒤에만 25tick 상당의 offset을 적용한다. pick/lift
전용 설정은 이후 재개방을 막고, place 데이터는 `task_mode=policy_reopen`과 해당
데이터에서 검증한 reopen 기준을 사용해야 한다.

기존 `live_far` 로그의 원 정책 출력만 통과시킨 반사실 계산에서는 cycle 0--10이
open 유지, cycle 11에서 close 확인으로 바뀐다. 이 계산은 기존 실행의 관측을 그대로
재사용했으므로 진짜 폐루프 결과가 아니며 성공 증거로 사용하지 않는다.

동봉된 Jetson 러너 사본에도 같은 규칙을 연결했지만, 그 사본은 IK·작업영역·관절
여유 진단을 `safe=True`로 실행 차단에 연결하지 않았던 코드다. 잘못된 안전 경로가
다시 배포되지 않도록 수정 묶음은 `--move`를 강제로 거부한다. 산출물은
`AI/out/umi_checkpoint_executor_gripper_guard_prediction_only_20260922.tgz`, SHA-256
`73da45b5103c9c748ab5f3d49e0ebf66dc638da7cb12214c5dd12d8d3a89ffeb`다. 실물 명령은
이 guard를 최신 fail-closed `so101_infer` 프리플라이트와 publisher 경계에 통합하고,
동일 입력 dry-run과 HW 승인까지 통과한 뒤에만 허용한다.

코드는 다른 실물 시연 데이터에도 재사용할 수 있지만 설정 수치는 재사용하면 안
된다. 새 데이터셋마다 최소한 open support, grasp/close 분포, 물체 폭, release 단계
유무를 manifest에 기록하고 guard JSON을 다시 생성·검증해야 한다. 또한 이 guard는
먼 물체를 향한 팔 접근 실패를 고치지 않으므로 거리 변주 시연과 정책 홀드아웃은
별도로 필요하다.

## 2026-09-22 실행팀 인계 묶음

정본 `origin/ai` 프리플라이트와 publisher 경계에 guard를 연결한 dry-run 후보,
원본 pv_E 해시 검증기, camera-TCP 설정, 반사실 로그, 한글 실행 절차 및 전체 파일
체크섬을 다음 묶음으로 고정했다.

- 파일: `AI/out/pv_E_guard_v1_handoff_20260922.tgz`
- SHA-256: `5c013e57a7dede245ab7cef7e013a03278f07861bb1ed6858027560fe25f54b1`
- 검사: guard `11/11`, 통합 러너 `18/18`, `so101_infer` `37/37`, 묶음 체크섬
  `41/41`
- 실제 모터 명령: `0회`

이 묶음의 상태는 `DRY_RUN_READY_ROBOT_MOVE_BLOCKED`다. 정본 안전 설정의
`max_joint_speed_rad_s`, `min_joint_margin_deg`, `wrist_roll_min_rad`,
`wrist_roll_max_rad`가 HW 승인값으로 채워지기 전에는 실제 trajectory를 내보내면
안 된다.
