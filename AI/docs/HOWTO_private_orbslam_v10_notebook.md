# 로컬·프라이버시 보존 ORB-SLAM → v10 노트북

관련 Jira: `S15P21A103-154`, `S15P21A103-156`

노트북:
`AI/notebooks/build_private_orbslam_v10.ipynb`

## 목적

집이나 개인 공간에서 수집한 원본을 외부로 업로드하지 않고,
HW builder 전처리부터 ORB-SLAM3 Atlas·episode localization, ArUco 정렬,
gripper gap, v10과 기준 묶음 생성까지 하나의 로컬 Jupyter 흐름으로 실행한다. 노트북은
네트워크 API를 호출하거나 학습·정책 추론·실물 명령을 수행하지 않는다.

## 입력 경계

노트북이 받는 입력은 다음과 같다.

- mapping 녹화 1편: `manifest.json`, `video.mp4`, `encoded.csv`, `frames.csv`,
  `accelerometer.csv`, `gyroscope.csv`
- `raw_root/rec_*/`: `manifest.json`, `frames.csv`, `video.mp4`를 포함한 원본 episode
- HW `handoff/final/02_umi_dataset_builder`: `build_dataset.py`, configs, SLAM YAML·mask
- `AI/third_party/orbslam3_umi_intake_0918.patch`가 적용된 native
  `gopro_slam` 실행 파일과 `ORBvoc.txt`
- `T_camera_tcp`를 포함한 camera→TCP 설정

Jupyter는 ORB-SLAM3를 Python으로 재구현하지 않고 C++ 실행 파일을
`subprocess` 인수 배열로 호출한다. 현재 Git 저장소에는 빌드된 binary와
vocabulary가 없으므로 사용자 환경의 정확한 경로를 설정해야 한다.
미패치 binary나 다른 Atlas를 같은 후보로 혼합하지 않는다.

## 실행 순서

1. 저장소 루트에서 Jupyter를 열고 노트북을 연다.
2. 경로 설정 셀의 `CHANGE_ME`를 로컬 절대 경로로 교체한다.
3. preflight와 telemetry·gripper prepare를 실행한다.
4. 미리보기 command와 binary·vocabulary·ID 13/0.16m 계약을 확인한 뒤
   `CONFIRM_NATIVE_ORB_RUN=True`로 바꿔 mapping Atlas와 episode localization을 실행한다.
5. ArUco 정렬 status가 `PASS_ARUCO_ALIGNMENT`인지 확인한다.
6. Atlas selection의 채택·제외 수와 제외 사유를 검토한다. 결과를 본 뒤
   기준을 완화하지 않는다.
7. selection이 맞을 때만 `CONFIRM_SELECTION_FOR_BUILD=True`로 바꾸고 v10·계약
   검증·reference 묶음을 생성한다.
8. `orb_run_manifest.json`, `LOCAL_BUILD_SUMMARY.json`, `dataset.json`, 검증
   보고서와 SHA-256을 확인한다.

prepare와 localization은 완료 파일이 모두 존재하면 건너뜁니다. 일부만 남은 출력은
자동으로 덮어쓰지 않는다. ORB 재시도는 기존 attempt를 보존하고
`ATTEMPT_NUMBER`를 늘린다. v10·reference 출력이 이미 있으면 새 `WORK_ROOT`를 사용한다.

## 생성되는 중간 산출물

- `prepared/rec_*/telemetry.json`, `gripper_width.csv`, `gripper_report.json`
- `atlas/map1.osa`, mapping `camera_trajectory.csv`, `map1_alignment.json`
- `map1_episode_runs/rec_*/attempt*/camera_trajectory.csv`
- `orb_run_manifest.json`: binary, vocabulary, builder, settings, mask, Track A patch,
  Atlas, alignment SHA-256
- `episode_selection_gate_report.json/.csv`: 입력 전체의 selection 통과·탈락과
  탈락 사유별 모수
- `episode_gate_report.json/.csv`: v10 build 결과까지 합친 최종 편별 판정,
  프레임 수, SLAM 추적률·실패 구간, gap 범위, jaw marker 동시 검출률,
  고정 ID 13 검출률·최대 연속 미검출 구간, 채택 편의 시작 TCP 6DoF. 이 최종 보고서 생성 시 영상은
  로컬에서 다시 디코드하지만 프레임이나 이미지는 보고서에 저장하지 않음
- v10 편별 metadata의 `start_tcp_pose_6d`: 선택된 연속 유효구간의 첫 TCP를
  ArUco ID 13 작업대 좌표계 `xyz[m] + rotation_vector[rad]`로 기록
- `first_training_anchor_tcp_pose_6d`: H=2 첫 학습 anchor의 현재 TCP를 같은
  좌표계로 별도 기록해 source 시작과 학습 시작을 혼동하지 않게 함
- selection, v10, verification report, Track A reference TAR. 최종 편별 gate
  JSON/CSV도 reference TAR의 `quality/`에 포함됨

탈락 사유 집계는 편 수 기준이다. 한 편이 coverage와 tracking loss를 동시에
위반하면 두 사유에 모두 집계되므로 사유별 합이 전체 탈락 편 수보다 클 수 있다.

## 고정 계약

- fixed marker: `DICT_4X4_50`, ID 13, black-square side `0.16m`
- v10 schema: `umi_relative_chunk/0.2.0-provisional`
- observation horizon: 2
- action horizon: 8
- rate: 10Hz
- action: 현재 TCP 기준 미래 상대 EEF SE(3) + 절대 gap(m)
- rotation6d: 회전행렬 첫 두 행
- robot-base alignment 및 IK: 학습 라벨에 적용하지 않음

다른 마커·카메라·리그는 설정값만 바꾸는 것으로 승인되지 않는다. 별도 보정 근거와
검증 결과가 필요하다.

## 프라이버시 주의

로컬 실행은 원본 업로드를 막지만, 생성된 v10 NPZ의 `image` 배열에도 집 내부 RGB가
포함된다. 노트북은 reference exporter의 `--redact-source-paths`를 사용해 manifest의
개인 절대 경로를 논리 이름으로 바꾸지만, 재현성을 위한 원본 해시는 유지한다. 따라서
프라이버시가 필요한 경우 다음 중 하나를 택한다.

1. v10과 reference TAR를 외부로 보내지 않고 같은 환경에서 학습한 뒤, 체크포인트와
   비식별 집계 보고서만 전달한다.
2. 데이터 공유가 꼭 필요하면 담당자 승인 아래 별도의 비식별화 데이터 계약을 먼저
   정한다. 배경 블러·마스킹을 임의로 적용하면 기존 학습 분포와 체크섬이 바뀌므로
   현재 v10과 같은 데이터로 간주하지 않는다.

노트북에는 업로드 코드가 없으며 자동으로 Git에 파일을 추가하지 않는다.

## 상태 해석

camera→TCP 또는 gap scale이 provisional이면 결과는
`PROVISIONAL_CALIBRATION_NOT_FOR_PHYSICAL_DEPLOYMENT`로 남는다. 계약 검사 통과는
학습 포맷이 일관됨을 뜻할 뿐, 실물 로봇 명령 승인이나 물리 성공을 뜻하지 않는다.
