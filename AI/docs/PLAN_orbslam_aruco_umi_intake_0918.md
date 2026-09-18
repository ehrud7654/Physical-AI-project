# ORB-SLAM3 + ArUco 실물 UMI 92편 수령·변환 검증

관련 Jira: `S15P21A103-154`(UMI 상대 청크 데이터 경로),
`S15P21A103-156`(실물 EEF·그리퍼 handoff 경계)

2026-09-18 기준 `raw.zip`으로 시연 92편과 매핑 영상 1편을 수령했다.
시연 outcome은 success 89편, failure 1편, 미평가 2편이다. 매핑 영상은
72.237초/2,148프레임이다.

**최신 상태:** 92편 전수 처리와 신규 v10 잠정본 생성이 완료됐다. 성공
89편 중 엄격한 pose·clock gate와 gap 품질 검토를 통과한 74편, 4,714 anchors가
`umi_relative_chunk/0.2.0-provisional`로 생성됐고 계약 문제는 0건이다.
이 중 2편은 17프레임 gap 미검출이 각각 파지 시작 전과 작업 종료 후에만
있음을 RGB·시계열로 수동 확인해 에피소드별 예외로 채택했다. 전역
15프레임 기준은 유지했다.
상세 수치·제외 사유·tar SHA-256은
`AI/docs/REPORT_orbslam_aruco_v10_0918.md`를 정본으로 본다. 아래의 대표편,
독립-map, 18편/14편 수치는 공통 Atlas 경로 확립 전의 진단 이력이다.

HW 공유문에는 작업대 고정 마커가 ID 11이라고 적혀 있었지만, 수령 영상에서
`DICT_4X4_50` ID 11은 대표 시연과 매핑 영상 모두 0프레임이었다. 실제로
검출되는 고정 마커는 ID 13(대표 시연 95/214, 매핑 877/2,148프레임)이다.
HW handoff의 `assets/workshop/README.md`와 공식 UMI ArUco 설정도 ID 13,
검정 사각형 160mm(외곽 200mm)를 명시한다. 따라서 이 수집분은 ID 13을
정본으로 해석하고, 공유문의 ID 11 표기는 정정 대상으로 남긴다.

## 2026-09-18 실제 ORB-SLAM3 관통 결과

전달된 S22 0.6배율 보정값과 IMU 시프트를 사용했다. `final.zip`에는 실제
sensor timestamp를 쓰는 patch가 포함돼 있었지만 `handoff_20260916.zip`의
ORB source에는 아직 적용되지 않은 상태였다. 이 patch를 적용하고, 다음 세
항목을 진단용 사본에서 추가 수정했다.

- Pinhole 카메라의 ArUco 초기화 함수가 항상 `false`를 반환하던 stub이었다.
- 저장 map을 읽어도 localization-only 모드로 전환하지 않았다.
- 프레임별 tracking/IMU 초기화 상태를 남기지 않았다.

수정분은 `AI/third_party/orbslam3_umi_intake_0918.patch`로 보존했다.
전달된 `third_party/ORB_SLAM3` 루트에서 `git apply --check` 후 적용한다.
patch의 `--localization_only`는 명시적으로 지정할 때만 atlas를 고정하며,
아래 전수 결과는 atlas 없이 에피소드별로 실행한 값이다.

또한 `final.zip`의 Jetson wrapper는 고정 ID 13을 사용하면서
`INIT_TAG_SIZE_M=0.018`을 기본값으로 둔다. 이는 같은 handoff의 mapping board
설계값 `0.16m`와 불일치하며 jaw marker 16mm와 혼동된 값으로 보인다. 이
wrapper 기본값으로 신규 수집분을 처리하면 안 된다.

Pinhole tag 초기화를 구현하고 ORB feature 수를 1,250에서 2,500으로 늘렸으며,
원본 `frames.csv` 시각을 telemetry에 보존해 runner가 사용하도록 했다. 대표
3편의 결과는 다음과 같다. 고정 마커 크기 `0.16m`는 handoff의 asset 문서,
공식 UMI 설정과 runner 기본값이 모두 일치하는 설계값이다. 다만 같은 asset
문서가 실제 장착 치수의 실측을 요구하므로 **실물 검증 전 잠정값**으로 둔다.

| episode | pose | 최초 pose | 이후 손실 | gap 최대 연속 미검출 | 현재 판정 |
|---|---:|---:|---:|---:|---|
| `rec_1789706018893_93286881` | 194/214 (90.7%) | 0.700s | 0 | 13 | REJECT |
| `rec_1789702124547_6cc3b75c` | 281/300 (93.7%) | 0.667s | 0 | 10 | REJECT |
| `rec_1789706190135_72adee56` | 199/213 (93.4%) | 0.500s | 0 | 3 | REJECT |

세 편 모두 tracking sidecar와 원본 카메라 시각의 최대 오차가 약
`0.000005ms`, 유효 pose timestamp 오차가 약 `0.0005ms`로 2ms 기준 안이다.
첫 두 편은 gap 연속 미검출 7프레임 기준을 넘는다. 세 편 모두 manifest의
`marker_black_square_size_verified`가 false이고 고정 마커 실측 크기도 없으므로,
metric-scale 데이터셋에는 아직 한 편도 채택하지 않는다.

대표편에서 `T_world_camera @ T_camera_marker`로 작업대 마커 자세를
추정하면 pose와 마커가 함께 보이는 프레임은 89개, 잠정 outlier 기준을
통과한 프레임은 79개였다. 잠정 `0.16m` 크기에서 translation residual은
중앙 3.89mm/p95 32.43mm, rotation residual은 중앙 0.37도/p95 1.16도,
재투영 오차는 중앙 1.07px/p95 1.50px였다. 회전과 재투영은 안정적이지만
translation p95가 잠정 30mm gate를 2.43mm 넘고 실측 marker 크기도 없으므로
상태는 `REJECT_ARUCO_ALIGNMENT`이다. `tools/align_orbslam_to_aruco.py`는
`T_world_marker`와 역변환을 모두 출력하지만, `--marker-size-verified` 없이
생성한 수치는 배포 가능한 calibration으로 취급하지 않는다.

ORB의 기존 `camera_trajectory.csv`는 lost row에서 과거 timestamp를 반복한다.
유효 pose row의 timestamp는 검사하되, 최종 프레임 대응은 반드시 `frame_idx`와
원본 `frames.csv.sensor_timestamp_ns`로 결합한다. 새
`tools/validate_orbslam_intake.py`는 outcome, 전체 추적률, 최초 초기화 시각,
초기화 후 손실, IMU 초기화, 원본 clock 오차, quaternion, gap 누락 run과 두
마커의 scale 검증 상태를 함께 검사한다. 늦게 초기화된 일부 구간만 보고 PASS로
판정하던 기존 validator의 허점을 회귀검사로 막았다.

## 수령 자료 inventory 상태

`tools/inspect_umi_delivery.py`는 ZIP/TAR/폴더를 **풀거나 수정하지 않고**
파일별 SHA-256, 크기, 확장자, 작은 JSON의 최상위 키와 CSV 헤더를
기록한다. 경로 순회·중복 이름·링크 파일은 거부한다. 상태는 항상
`INVENTORY_ONLY_SCHEMA_UNVERIFIED`이고 좌표계·시각·gap·tracking의
품질 통과를 의미하지 않는다. 합성 파일 테스트 4건과 기존 ARCore
정규화 ZIP 1편의 읽기 테스트를 통과했다. 위 실제 ORB 검증은 별도
도구의 결과이며 inventory 통과를 품질 통과로 승격하지 않는다.

```bash
cd AI
python tools/inspect_umi_delivery.py <새_에피소드.zip> \
  --out out/orbslam_first_episode_inventory.json
```

임의 필드 추측으로 기존 ARCore converter에 연결하지 않는다. 현재는
검증기까지 마련됐고, 정확한 고정 마커 크기와 jaw scale이 확인된 뒤
ArUco 작업대 정렬 adapter와 89개 성공편 일괄 처리를 진행한다.

## 전체 변환 전 남은 실측 입력

92편 전체 ORB 실행은 고정 마커 크기를 바꾸면 metric scale과 초기화가 함께
바뀌어 재실행해야 한다. 따라서 다음 세 항목을 먼저 HW에 확인한다.

1. 작업대 고정 ID 13 보드의 **실물 검정 사각형 한 변**이 handoff 설계값
   160mm와 일치하는지만 실측 확인한다. jaw의 16mm 마커와 혼동하지 않는다.
2. 빈 그리퍼를 완전 개방→완전 폐쇄하는 5초 clip으로 jaw marker 16mm의
   scale을 90mm 전행정에 대조한다. 검증 전 `gap_m`는 잠정값이다.

`raw.zip` SHA-256은
`f91d8184bca34e93e160a916053fbb76d426ac01e03c205f069c23775847b2ea`이다.
success가 아닌 아래 세 편은 전체 처리에서도 학습 후보에 넣지 않는다.

- `rec_1789705791122_e5270661`: unrated, 1.50s
- `rec_1789701998775_2695f6ff`: failure, 1.72s
- `rec_1789705614340_0fe1eb6e`: unrated, 3.11s

## 89개 success 편의 pose 이전 전처리 결과

89편 모두 telemetry와 gripper report 생성은 오류 없이 완료됐다. 총
19,178프레임이며, 양쪽 jaw 마커 동시 검출률은 최소 73.85%, p05 76.74%,
중앙 85.82%, 최대 95.0%였다. 연속 미검출 run은 최소 1, 중앙 5,
p95 13, 최대 17프레임이다.

- 합의된 `maximum_missing_run_frames <= 7`을 만족하는 pose 처리 후보: 70편
- gap 누락 기준으로 먼저 제외되는 편: 19편
- jaw marker metric scale이 검증된 편: 0편

따라서 70편은 **gap 전처리 후보**일 뿐 최종 채택편이 아니다. 빈 그리퍼
전행정 scale 검정, ORB pose, ArUco 정렬과 전체 timestamp gate를 추가로
통과해야 한다. 결과와 에피소드별 사유는 로컬 생성물
`tmp/raw_0918_full/prepare_summary.json`에 있으며, 재현 도구는
`tools/summarize_umi_prepare_reports.py`이다.

## 70개 gap 후보의 ORB 전수 결과

실제 camera sensor timestamp, 고정 마커 ID 13, 잠정 검정 사각형 160mm,
ORB feature 2,500 조건으로 70편을 전부 처리했다. 최초 병렬 실행에서 출력이
없던 3편도 같은 명령으로 개별 재실행했으며 runner는 정상 종료했다. 누락 원인은
실행 오류가 아니라 반복 초기화 실패였다.

- 처리 완료: 70/70편
- 엄격 pose/clock/gap 구조 품질 통과: 18편
- 품질 탈락: 52편
- 탈락 52편 모두 pose coverage 70% 미만
- 51/52편은 첫 유효 pose가 3초 이후
- 27편은 IMU 초기화가 한 번도 완료되지 않았고, 11편은 마지막 프레임에서
  IMU가 초기화 상태가 아니었다.
- 통과 18편 coverage는 87.1--97.5%(중앙 93.5%), 첫 pose는
  0.20--1.07초(중앙 0.52초), pose timestamp 최대 오차는 0.0005ms였다.

따라서 지배적인 탈락 원인은 gap이나 clock이 아니라 **에피소드별
visual-inertial 초기화 지연**이다. 늦게 초기화된 뒤의 짧은 구간만으로
시연을 채택하지 않는다. 전수 결과는 로컬 생성물
`tmp/raw_0918_full/orb_batch_summary.json`에 있으며 재현 도구는
`tools/summarize_orbslam_intake_batch.py`이다. 이 18편도 두 metric scale이
검증되기 전에는 최종 학습 데이터가 아니다.

72초 mapping 영상으로 같은 조건의 atlas(86 keyframes)를 만들고 대표
탈락편에 불러오는 대조도 수행했다. localization-only 모드는 대표 탈락편의
첫 pose를 4.90초에서 0.27초로 앞당기고 coverage를 21.5%에서 67.2%로
올렸지만, 초기화 후 추적 손실이 11회 발생했다. 극단 탈락·중간 탈락·기존
통과편을 포함한 네 편 모두 8--14회의 추적 손실로 엄격 gate를 통과하지
못했다. atlas를 로드한 채 local mapping을 계속 허용하는 모드는 반복
재지역화와 비정상 IMU 속도가 발생해 채택하지 않았다. 현재 atlas 경로는
초기화 가능성 진단일 뿐 데이터 생성 경로가 아니다.

고정 ID 13 가시성도 70편 전수 검사했다. 모든 편에서 한 번 이상
검출됐고 첫 검출 frame 중앙값은 14였지만, 검출률 중앙값은 40.6%, 최대
연속 미검출 중앙값은 78.5프레임이었다. ArUco와 기존 ORB pose의 합집합
coverage가 70% 이상인 편은 22/70뿐이고, 두 소스의 겹침이 전혀 없는 편은
14/70, 둘 다 없는 최대 run이 7프레임 이하인 편은 1/70뿐이었다. 따라서
ArUco pose 단독 또는 단순 splice/interpolation으로 70편을 살리는 것도
근거가 부족하다. 로컬 결과는
`tmp/raw_0918_full/fixed_aruco_orb_overlap.json`, 재현 도구는
`tools/audit_fixed_aruco_visibility.py`이다.

엄격 ORB 품질을 통과한 18편에 대해 잠정 160mm 기준 정렬을 전수
적용하면 14편이 `PROVISIONAL_ARUCO_ALIGNMENT`, 4편이 reject다. reject는
joint pose/marker 관측 또는 robust inlier 부족 2편, translation residual
p95 30mm 초과 2편이다. 잠정 통과 14편의 episode별 translation residual
p95는 5.13--29.39mm, rotation residual p95는 0.48--1.58도다. 따라서
현재 최대 후보는 **14편**이며 물리 스케일 확인 뒤 동일 입력으로 다시
판정해야 한다. 로컬 요약은
`tmp/raw_0918_full/aruco_alignment_batch_summary.json`, 재현 도구는
`tools/summarize_aruco_alignment_batch.py`이다.

다음 수집에서는 동작 시작 전에 고정 마커가 보이는 상태로 충분한 시차를
만드는 초기화 구간을 두고, pose/IMU 초기화 완료를 확인한 뒤 시연 구간을
시작해야 한다. 기존 92편은 gate를 낮추거나 장시간 보간해 편수를 늘리지
않는다.

물리 실측이 도착하면 raw manifest를 수정하지 않는다. 같은 장착·마커에 대한
캘리브레이션 근거를 별도 보존한 뒤 배치 검증에
`--fixed-marker-size-verified --jaw-marker-size-verified`를 명시해 재실행한다.
두 플래그 중 하나라도 없으면 배치 상태는
`QUALITY_COMPLETE_SCALE_UNVERIFIED`로 유지된다.

정렬을 통과한 trajectory는 `tools/apply_aruco_alignment.py`가
`T_marker_camera = T_marker_world @ T_world_camera`로 변환한다. 기본값은
`PASS_ARUCO_ALIGNMENT`만 허용한다. `--allow-provisional`은 계산 경로 확인용이며
그 출력 report의 `training_input_ready`는 false다. lost row는 원본 그대로
보존되고 downstream은 유효한 최장 연속 tracked segment만 선택한다.

## 첫 에피소드에 함께 필요한 원본 근거

- 영상 파일/프레임, 각 프레임의 원래 촬영 시각 및 S22 0.6배율 카메라
  내참수·왜곡 계수·저장 영상 회전 방향.
- ORB-SLAM3의 프레임별 pose, 변환 방향(`T_slam_camera`인지 역인지),
  축 규약, 길이 단위/스케일 검증, tracking 상태·손실/재위치추정 구간.
- 실제 부착된 ID 13(공유문에는 ID 11)의 검정 사각형 한 변 길이·마커 좌표축과, 매핑/수집에 사용한
  `T_marker_slam` 또는 역변환의 명시적 수치 및 map/session ID.
- gripper 측정 원본, gap의 **양쪽 접촉면 사이 전체 폭(m)** 정의,
  프레임/시각 연결 키, 미검출·보간 상태. 영상/pose/IMU/gap의 원본
  clock 정의와 동기화 방법.
- 카메라→파지점 변환의 프레임 규약과 보정 ID. 기존 ARCore/S22
  잠정 마커중점 보정값을 새 ORB optical frame에 무조건 재사용하지 않는다.
- 같은 40mm 케이스의 수평 양옆 파지를 안전 도달 범위 안에서 물체
  위치를 달리하여 반복 수집하고, 회차별 배치 위치와 결과를 남긴다.
  현재 v10의 드문 측면 교정 사례를 단순 재표집해도 물체 이동 크기와
  홀드아웃 측면 오차가 충분히 개선되지 않았기 때문이다.

## 1편 수령 후 통과 순서

1. 원본은 별도 디렉터리에 보존하고 해시·파일 목록을 기록한다.
2. 모든 프레임과 pose의 시각 증가, 영상↔pose↔gap 대응, 추적 손실·
   map 재시작과 잔차 분포를 측정한다. 기준 초과 구간은 버리고
   유효 세그먼트만 남긴다. 성공 라벨을 자동으로 true로 만들지 않는다.
3. 카메라 pose의 방향/축/metric scale과 마커 기준 변환을 수치 및
   영상 재투영으로 확인한다. `T_marker_slam @ T_slam_camera`는 **그
   이름의 방향이 실제 파일에서 확인된 경우에만** 사용한다.
4. 검증된 결과를 기존 정규화 번들(`poses.csv`, `frames.csv`,
   `gripper.csv`, 영상, quality metadata)의 의미로 명시적으로 변환한다.
   현재 `build_real_umi_relative_dataset.py`는 ARCore/OpenGL 카메라
   pose와 잠정 extrinsic을 전제하므로, ORB 출력을 이름만 바꿔 직접
   넣지 않는다. 새 adapter와 calibration ID로 구별한다.
5. 별도 버전 데이터셋을 만든 뒤 `verify_umi_relative_dataset.py`와
   소스 행/시각/좌표계 회귀검사를 통과시킨다. 기존 v10을 덮어쓰거나
   출처 표기 없이 섞지 않는다.

ArUco 기준은 에피소드 간 작업대 좌표 정렬에 도움이 될 수 있지만,
물체 위치를 바꾼 시연이나 보지 않은 배치에 대한 정책 반응을
자동으로 만들어 주지는 않는다. 실제 물체 배치와 결과도 에피소드별
메타데이터로 남겨야 한다.

## 새 배치의 시연 단위 홀드아웃 평가 준비

기존 v10은 물체의 실제 배치 좌표가 없어서 영상 속 bbox 중심으로만
분류했다. 2026-09-18 재검사에서 시뮬 handoff 크기·열린 gap에 맞는
홀드아웃은 10편 28행이었고, `|측면 목표|>10mm`는 3편 5행뿐이었다.
재표집 모델의 편별 평균 오차 개선 구간은 0을 포함했다. 이 숫자를
새 배치 성능으로 간주하지 않는다.

새 수집에서는 일상 사용자에게 좌표 입력을 요구하는 것이 아니라,
**진단용 소수 시연**에만 다음 기록을 붙인다.

- `episode_id`, `map_id`, 실제 고정 마커 ID와 변환·축 규약.
- 물체 중심의 마커 좌표계상 `object_xy_m` 또는 기준 위치 대비
  `object_offset_xy_m`. 실제 계측값인지 지그의 명목 배치값인지
  `placement_source`로 구분하고 불확도/반복 배치 오차를 남긴다.
- 동일한 40mm 과자 케이스, 시작 카메라/파지점 pose, 성공 여부를
  에피소드별로 기록한다. 새 배치는 좌·우와 앞·뒤를 모두 포함하되
  SO-101 도달가능·충돌 안전 범위 밖의 위치를 평가 성공 조건으로
  넣지 않는다.
- 그리퍼 원본은 합의된 **접촉면 전체 gap [m]**로 받는다.
  예: 40mm 물체 폭은 `gap_m=0.040`에 해당한다. 카메라·pose·gap의
  timestamp와 유효/미검출 상태를 함께 유지한다.

배치별 에피소드를 먼저 고정해 학습과 평가에 섞지 않는다. 체크포인트,
영상 전처리, 선택 조건을 평가 전에 정하고, 첫 4개 미래 목표(index
`0..3`)의 물체 이동 방향·이동 크기·기록 타깃 오차를 **편별**로
보고한다. 같은 체크포인트에서 정상/평균색/노이즈 영상의 추론 시점
교란을 비교한다. 이 검사를 통과하더라도 별도의 IK·접촉·폐루프
롤아웃과 실물 안전 승인이 필요하다.
