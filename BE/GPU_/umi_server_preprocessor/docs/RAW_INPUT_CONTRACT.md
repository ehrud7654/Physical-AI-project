# RAW 입력 계약

## 폴더 구조

```text
<job-root>/
├── raw_mapping/
│   └── rec_<id>/
│       ├── video.mp4
│       ├── frames.csv
│       ├── encoded.csv
│       ├── accelerometer.csv
│       ├── gyroscope.csv
│       └── manifest.json
└── raw/
    ├── rec_<id>/
    │   └── 위와 같은 6개 파일
    └── rec_<id>/
        └── 위와 같은 6개 파일
```

- `raw_mapping`: Atlas를 만들 작업 공간 매핑 영상. 기본 실행은 최소 1개를 요구하며 여러 개면 이름순
  마지막 세션을 사용한다.
- `raw`: 시연 영상. 최소 2개가 필요하다.
- 학습 후보는 `manifest.json`의 `outcome`이 `success`인 세션뿐이다.
- `failure`, `unrated` 세션은 원본에는 남지만 기본 데이터셋에서는 제외한다.
- 전처리기는 `raw`, `raw_mapping` 내부 파일을 수정하지 않는다.

## 각 파일의 용도

| 파일 | 용도 |
|---|---|
| `video.mp4` | S22 0.6배율 영상, 1920×1080, 약 30 Hz |
| `frames.csv` | 센서 timestamp, 노출, ISO, crop 등 프레임 메타데이터 |
| `encoded.csv` | MP4 sample PTS와 카메라 프레임 대응 검사 |
| `accelerometer.csv` | 가속도계 원본 |
| `gyroscope.csv` | 자이로 원본 |
| `manifest.json` | 장비·카메라·방향·작업·성공 여부 계약 |

## 압축 파일

CLI의 `--archive`는 `.tgz`, `.tar.gz`, `.zip`을 지원한다. 압축 최상위에 `raw/`와
`raw_mapping/`이 바로 있어야 한다. 경로 탈출, 링크, 장치 파일, 파일 개수 및 총 크기를 검사한다.

Spring API 방식에서는 Spring이 공유 볼륨의 `/jobs/<jobId>/`에 위 구조를 만든 다음 작업을 요청한다.
사용자가 보낸 파일명이나 경로를 그대로 명령행에 연결하지 않는다.

## 보정 프로필

기본 `s22_20260921` 프로필은 패키지의 `dataset_builder/configs`를 사용한다. 사용자 정의 프로필은
컨테이너 `/profiles/<profile-id>/`에 다음 파일을 모두 둔다.

```text
dataset.json
s22_camera_imu.json
s22_camera_tcp.json
s22_gripper.json
s22_slam_tag.json
s22_slam_mask.png
```

테이블 마커는 `DICT_4X4_50`, ID 13, 검은 테두리 한 변 160 mm다. ChArUco 보드 안의 18 mm
ID 13은 다른 마커이므로 매핑·시연 시 치운다.
