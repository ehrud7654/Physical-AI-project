# 처리 단계와 출력

## 처리 단계

1. **입력 검사**: 폴더, 필수 파일, manifest, 세션 수를 검사한다.
2. **prepare**: 카메라 시간축에 IMU를 정렬하고 ArUco ID 0·1로 그리퍼 간격을 계산한다.
3. **mapping**: 매핑 세션을 최대 3회 실행해 추적 프레임이 가장 많은 Atlas를 채택한다.
4. **relocalization**: 성공 시연을 같은 `map_atlas.osa`에 재국지화한다. 추적 문제는 한 번 재시도한다.
5. **gate**: 초기화·추적 손실·프레임 수·그리퍼 누락·timestamp 조건을 검사한다.
6. **episode assembly**: 카메라 궤적을 camera-TCP로 변환하고 세션별 pose/action을 만든다.
7. **Zarr build**: 통과한 episode를 Stanford UMI ReplayBuffer로 합친다.
8. **package**: Zarr, report, checksum manifest를 GPU 입력 TGZ로 묶는다.

세계 좌표의 TCP pose는 다음 관계를 사용한다.

```text
T_world_tcp = inv(T_slam_tag) × T_slam_camera × T_camera_tcp
```

## 작업 폴더

```text
<job-root>/
├── raw/                         수정하지 않는 원본 시연
├── raw_mapping/                 수정하지 않는 매핑 원본
├── processed_mapping/rec_*/
│   ├── telemetry.json
│   ├── tx_slam_tag.json
│   └── slam/
│       ├── map_atlas.osa
│       ├── camera_trajectory.csv
│       ├── trajectory_validation.json
│       ├── receipt.json
│       └── slam.log
├── processed/rec_*/
│   ├── telemetry.json
│   ├── gripper_width.csv
│   ├── prepare_report.json
│   └── slam/
│       ├── camera_trajectory.csv
│       ├── trajectory_validation.json
│       ├── receipt.json
│       └── slam.log
├── dataset/
│   ├── <name>.zarr.zip
│   ├── <name>.zarr.report.json
│   ├── <name>.episodes.json
│   ├── <name>_gpu_input.manifest.json
│   └── <name>_gpu_input.tgz
├── pipeline_report.json
├── job_status.json
└── worker.log
```

## Zarr 핵심 배열

| 키 | 내용 |
|---|---|
| `data/camera0_rgb` | 224×224 RGB, 중앙 crop, 마커 inpaint |
| `data/robot0_eef_pos` | 테이블 world 기준 TCP 위치(m) |
| `data/robot0_eef_rot_axis_angle` | TCP 회전축-각도(rad) |
| `data/robot0_gripper_width` | 그리퍼 전체 간격(m) |
| `data/robot0_demo_start_pose` | episode 시작 TCP pose |
| `data/robot0_demo_end_pose` | episode 종료 TCP pose |
| `meta/episode_ends` | episode 경계 |

원본은 약 30 Hz다. 현재 정책 학습은 `down_sample_steps=3`으로 약 10 Hz observation/action을 만든다.

## 완료 판정

Spring은 `job_status.json` 또는 상태 API에서 `ready`를 확인한다. `failed`이면 `error`와
`worker.log`, 세션별 `excluded_because`를 같이 노출한다. 일부 세션 제외는 작업 실패가 아니며,
최종적으로 2개 이상의 episode가 남아야 Zarr를 만든다.
