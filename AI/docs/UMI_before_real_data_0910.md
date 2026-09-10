# 실데이터 도착 전 구현 상태와 실행 방법

2026-09-10. `155` 미커밋 작업에 UMI 입력 검증·궤적 검사·출처 해시·ARCore 파서를 추가했다.
이 문서는 앞선 REVIEW_umi_handover_0910.md의 실패 기록 이후 수정 상태다.

## 구현한 것

- raw status를 자르기 전에 검증. DEAD를 D로 처리하지 않는다.
- 변환기에서 NaN/Inf/음수 IK 오차, 잘못된 관절값·범위 검사.
- 명시적 TrajectoryLimits로 팔 5 rad + 전체 gap m의 step/velocity/acceleration 검사.
  실제 timestamp 간격을 사용하며 실패 시 연속 구간을 분리한다. 보간/리샘플링은 하지 않는다.
- arcore/arcore_imu 실기록은 trajectory limits를 생략하면 변환 거부.
  기존 합성 진단은 생략 가능하되 metadata에 trajectory_checked=false를 기록.
  검사 예산도 metadata에 저장한다. 검사 통과는 충돌 안전이나 모터 도달 보장이 아니다.
- 변환 전용 64자리 SHA-256. 공용 CODE_GLOBS를 변경하지 않았다.
  umi/track_a/tools와 관련 contract/sim/config/tracking 소스가 포함된다.
  런타임 외부 라이브러리·모든 외부 mesh까지 자동 추적하는 완전 환경 잠금은 아니다.
- ARCore ZIP/디렉터리 → RawEpisode 파서. JPEG는 디코딩만 하며 원본은 덮어쓰지 않는다.
  T_base_world @ T_world_camera @ T_camera_pinch, xyzw→wxyz 변환.
  timestamp exact join, 초기 안정화 구간 제외, tracking whitelist, 구간 분리,
  gripper D/M 유효값과 T/X 결측, 스키마·카메라 설정·경로 검사.
- ROS 메시지 재사용 시 derivative 필드 초기화, stamp=0 정책 명시 적용,
  gripper 검증을 mutation 전에 수행, 0ns로 반올림되는 duration 거부.

## 로컬 실행

Python 환경에 numpy와 Pillow가 필요하다. 전체 회귀에는 MuJoCo 의존성도 필요하다.
공유 GPU 서버에서는 실행하지 않는다.

```bash
cd AI
python tools/check_umi_handover_edges.py
python tools/check_umi_ingestion.py
python tools/run_umi_regression.py
python tools/umi_ingest_arcore.py --help
```

파서 CLI 기본값은 검증만 수행한다. 저장하려면 존재하지 않는 새 디렉터리를 지정한다.

```bash
python tools/umi_ingest_arcore.py --bundle /path/recording.zip \
  --calibration /path/measured_calibration.json --quality /path/reviewed_quality.json
```

calibration JSON 필드:

- calibration_id: 실제 측정 기록 ID
- t_cam_to_pinch: camera 좌표에서 pinch를 표현하는 4x4 변환
- t_arcore_world_to_base: ARCore world 좌표를 base 좌표로 보내는 4x4 변환

quality JSON 필드:

- tracking_valid_values: recorder에서 확인한 정상 tracking 문자열 목록
- warmup_s: 최초 pose부터 제외할 초기 시간. 최소 3초; 현장 프로토콜에서 선택
- usable_segments: 원본 poses.csv 데이터 행의 [start,end) 정수 구간 목록
- frames_dropped: pose에 대응 이미지가 없는 실제 개수

summary.txt 문법은 샘플 확인 전 자동 파싱하지 않는다. 위 구간·drop 수를 명시적으로
입력받고 원문은 보존한다. 디렉터리/ZIP 루트에 episode.json 및 SPEC 파일이 있어야 한다.
이미지 없는 pose, PAUSED, 사용 불가 구간을 넘어 행을 이어붙이지 않는다.
현재는 최장 연속 구간 하나를 반환하며 원본 행 번호·캘리브레이션·소비한 파일 SHA를 기록한다.

`convert_umi.py --trajectory-limits /path/budgets.json`의 JSON에는
max_step/max_velocity/max_acceleration 각각 6개 양수 값이 필요하다.
순서는 J1~J5, gap. 단위는 각각 [rad,m], [rad/s,m/s], [rad/s²,m/s²].
실물 허용값은 상위 제어 정책에서 공급해야 하며 테스트 fixture 값을 사용하면 안 된다.

## 남은 외부 입력과 별도 통합

- 실제 번들로 컬럼·tracking enum·summary 구간 의미·카메라 설정의 일치 확인.
- 두 캘리브레이션의 측정값. 로봇 URDF TCP 값만으로 핸드헬드 캘리브레이션을 대체할 수 없다.
- 카메라 계약은 `cam_wrist` 단일 논리 채널이다. 같은 실제 카메라 장치를 UMI 시연 후
  로봇으로 옮겨 사용하며 `cam_front`는 UMI 학습·배포 계약에 포함하지 않는다.
- 공유 계약은 `0.3.0-provisional`로 상승했다. 6번째 채널은 전체 gap `0~0.09m`의
  선형 정규화값이며 `action[t]=state[t+1]`이다. 다음 상태가 없는 마지막 raw tick은
  학습 행에서 삭제한다. 구 hinge 계약은 진단용 읽기만 가능하고 신규 학습 검증은 실패한다.
- 실물 solver, 최종 명령 한계 강제, 상태 clock jump 관리, controller/QoS 및
  watchdog/E-stop은 별도 ROS/sim2real 통합 항목이다. 이번 데이터 파서 완료와 구분한다.

실데이터 준비 전 가능한 입력 처리와 합성 검증은 구현했지만, 실제 학습·실물 실행 준비가
모두 끝났다는 뜻은 아니다. 기존 자료의 L68/L70을 전면 해소로 기록하지 않는다:
실제 trajectory budget·실기록 수용률·전체 외부 자산 provenance는 추가 대조가 필요하다.
