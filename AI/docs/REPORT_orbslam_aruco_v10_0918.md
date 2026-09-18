# ORB-SLAM3 + ArUco 신규 실물 UMI v10 변환 결과

기준일: 2026-09-18

관련 Jira: `S15P21A103-154`(UMI 상대 청크 데이터 경로),
`S15P21A103-156`(실물 EEF·그리퍼 handoff 경계)

## 결론

`raw.zip`의 시연 92편(성공 89, 실패 1, 미평가 2)과 mapping 녹화 1편을
전수 처리했다. 성공 89편 가운데 엄격한 pose·clock 선택 기준과 gap 품질
검토를 통과한 74편으로 신규 상대 EEF action-chunk 데이터셋을 생성했다.

- 데이터셋: `AI/datasets/umi_real_relative_20260918_v10_orbslam_provisional_74ep`
- 스키마: `umi_relative_chunk/0.2.0-provisional`
- 구성: 74 episodes / 4,714 anchors / H=2 / K=8 / 10Hz
- 검증: `problems: []`
- 상태: `PROVISIONAL_CALIBRATION_NOT_FOR_PHYSICAL_DEPLOYMENT`

이는 89편을 모두 입력으로 처리한 결과이지, 89편 모두를 학습 데이터로
채택했다는 뜻이 아니다. 실패 run의 pose를 이어 붙이거나 장시간 보간하지
않았다.

## 공통 작업대 좌표와 Atlas 선택

- 고정 마커: `DICT_4X4_50`, ID 13
- 검정 사각형 한 변: 0.16m(HW/사용자 실측 확인)
- pose 의미: `T_world_camera`
- 공통 출력: `T_marker_camera = T_marker_world @ T_world_camera`
- TCP: `T_marker_tcp = T_marker_camera @ T_camera_tcp`

mapping 녹화에서 독립적으로 만든 Atlas 세 개를 후보로 사용했고, 각각의
`T_marker_world`를 같은 ID 13 관측에서 추정했다. 세 정렬은 모두
`PASS_ARUCO_ALIGNMENT`였다.

| Atlas | SHA-256 | translation p95 | rotation p95 | 최종 채택 |
|---|---|---:|---:|---:|
| map1 | `ca1952bf158eaf44e9bc3d771cb2979f85c3974d60e82f385a32f7daae8156dc` | 10.12mm | 0.800° | 70 |
| map2 | `740bb899c48c2d198d74fc699fa0317eddefaa632b13882fbc0e80d780d63e52` | 8.39mm | 0.789° | 4 |
| map3 | `8e5ef7875ea2d7687453fd2cad8d28017e706fcdd4191bc13c1f4ccb45ea43f4` | 6.32mm | 0.785° | 0 |

한 에피소드에는 한 번의 완전한 ORB run만 사용했다. Atlas가 다른 pose row를
한 에피소드 안에서 splice하지 않았다. 동일 시연을 map1/map2로 처리해 각
정렬을 적용한 사전 대조에서 marker-frame 차이는 translation p95 3.94mm,
rotation p95 0.220°였다.

## 채택 기준과 결과

에피소드별 run은 다음을 모두 만족할 때만 채택했다.

- pose coverage 95% 이상
- 첫 pose 0.5초 이내
- 초기화 뒤 tracking loss 0회
- 마지막 프레임 tracked
- 원본 frame timestamp와 pose 대응 오차 2ms 이하
- gap 최대 연속 미검출 15프레임 이하
- outcome `success`

15프레임 전역 기준은 전달받은 공식 builder의
`configs/dataset.json: maximum_marker_missing_run_frames=15`와 맞춘 값이다.
기존 트랙 A 진단의 7프레임 기준을 조용히 바꾼 것이 아니며, 선택 JSON에
사용 기준을 기록했다. v10 builder는 upstream `gripper_width.csv`에 이미
기록된 보간 폭을 사용하므로 selector의 missing-run gate가 품질 경계다.

17프레임으로 전역 기준을 2프레임 초과한 아래 두 편은 RGB 프레임과 gap
시계열을 수동 대조했다. 전역 기준은 15로 유지하고, 검토 근거를
`configs/real/umi_gap_missing_reviewed_exceptions_0918.json`에 남긴 뒤 두 편에만
예외를 적용했다.

- `rec_1789701986459_f5ea3eee`: frame 0--16(0.00--0.57s), 완전 개방 상태의
  시작 준비 구간. 닫힘 시작 frame 73, 최소 gap frame 145는 직접 검출됐다.
- `rec_1789705590144_eda374ba`: frame 184--200(6.17--6.70s), 파지·리프트 뒤
  약 75mm로 재개방한 종료 구간. 닫힘 시작 frame 45, 최소 gap frame 90은
  직접 검출됐다.

두 편 모두 선택된 ORB run의 pose coverage 100%, tracking loss 0회, 마지막
프레임 tracked다. 예외 파일의 SHA-256과 적용 episode 목록은 최종
`atlas_selection.json`에 기록된다.

최종 분류는 다음과 같다.

- 채택 74편: map1 attempt1 53편, map1 attempt2 17편, map2 attempt1 4편
- pose 품질 탈락 15편
- 원본 outcome 제외 3편: failure 1, unrated 2

모든 Atlas 후보와 재시도별 탈락 사유는 데이터셋 내부
`atlas_selection.json`에 보존했다.

## v10 검증 결과

- rows/episode min/median/max: 46 / 63.5 / 91
- source duration min/median/max: 5.400 / 7.150 / 9.899s
- 미래 병진 크기 p50/p95/max: 67.89 / 227.31 / 412.82mm
- 미래 회전 p50/p95/max: 5.71 / 19.25 / 39.60°
- gap 범위: 35.37–75.69mm
- 마지막 target horizon 중앙값: 0.79995s
- source row step: 전부 3 frames
- 계약 문제: 0건

`dataset.json`, `atlas_selection.json`, `camera_tcp.json`,
`verification_report.json`을 tar 내부에 함께 넣었다.

## 전달 파일과 해시

- tar: `AI/datasets/umi_real_relative_20260918_v10_orbslam_provisional_74ep.tar`
- tar size: 956,864,512 bytes
- tar SHA-256: `5d35c107bf2d9c6c7b29ae31ab33029ff1cd72beba730127ca0be049a119c36e`
- `dataset.json`: `738ad25bda9ca06382020a8abead5c0988ca5df0c8f1f9a95b8f9cec3e4bf924`
- `verification_report.json`: `9ace778f9294c13a3cab00671d64e1e38ea1ca9ad372c824b8ed25478c179248`
- `atlas_selection.json`: `5e6be12af893b8e55a013e95c310bf900ac16048d21dafc7400b06d3d7bd8e48`

기존 72편 tar는 검토 전 결과로 보존하되 본 개정본과 혼합하지 않는다.

## 남은 제한

작업대 ID 13 크기는 실측 확인됐지만 다음 두 항목은 아직 잠정이다.

1. `camera_tcp.json`은 CAD 기반이며 실제 optical camera→pad 중심 TCP의 독립
   실측 검증이 없다.
2. `gap_m`은 영상 마커 기반 추정값이며 빈 그리퍼 0–90mm 전행정 클립으로
   metric scale을 독립 검증하지 않았다.

따라서 이 데이터는 트랙 B의 변환·학습 경로 연결과 진단에는 전달할 수 있지만,
실물 명령 생성이나 배포 승인 근거로 사용할 수 없다.
