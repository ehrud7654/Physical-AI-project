# ORB-SLAM3 + ArUco ID 11 실물 UMI 1편 수령·변환 준비 (미검증 제안)

2026-09-18 기준 HW는 휴게실에서 ID 11을 고정한 뒤 ORB-SLAM3 매핑과
새 UMI 시연 수집을 계획한다고 공유했다. **새 에피소드 파일은 아직
트랙 A에 전달되지 않았다.** 아래는 입력 검사 계획이지 새 데이터의
품질 통과 또는 변환 완료 보고가 아니다.

## 수령 전 준비 상태

`tools/inspect_umi_delivery.py`는 ZIP/TAR/폴더를 **풀거나 수정하지 않고**
파일별 SHA-256, 크기, 확장자, 작은 JSON의 최상위 키와 CSV 헤더를
기록한다. 경로 순회·중복 이름·링크 파일은 거부한다. 상태는 항상
`INVENTORY_ONLY_SCHEMA_UNVERIFIED`이고 좌표계·시각·gap·tracking의
품질 통과를 의미하지 않는다. 합성 파일 테스트 4건과 기존 ARCore
정규화 ZIP 1편의 읽기 테스트를 통과했다. **ORB-SLAM3 수집분 자체는
아직 검사하지 않았다.**

```bash
cd AI
python tools/inspect_umi_delivery.py <새_에피소드.zip> \
  --out out/orbslam_first_episode_inventory.json
```

첫 수집분의 실제 멤버 이름·pose 행렬 방향·clock·gap 필드가 확인된
뒤에만 포맷 전용 검증기와 adapter를 작성한다. 임의 필드 추측으로
기존 ARCore converter에 연결하지 않는다.

## 첫 에피소드에 함께 필요한 원본 근거

- 영상 파일/프레임, 각 프레임의 원래 촬영 시각 및 S22 0.6배율 카메라
  내참수·왜곡 계수·저장 영상 회전 방향.
- ORB-SLAM3의 프레임별 pose, 변환 방향(`T_slam_camera`인지 역인지),
  축 규약, 길이 단위/스케일 검증, tracking 상태·손실/재위치추정 구간.
- ID 11의 실제 한 변 길이·마커 좌표축과, 매핑/수집에 사용한
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
