# UMI Server Preprocessor

S22 수집 앱 RAW를 받아 Stanford UMI 학습 직전 입력까지 만드는 서버 인계 패키지다.

```text
RAW → IMU/그리퍼 전처리 → ORB-SLAM3 Atlas → 시연 재국지화
    → 품질 gate → UMI Zarr → GPU 입력 TGZ
```

학습, 체크포인트 export, Jetson 모터 제어는 포함하지 않는다. Spring은 업로드·권한·작업 DB를
담당하고 이 컨테이너는 영상/SLAM/Zarr 처리만 담당한다.

## 빠른 실행

Linux 서버에서 작업 데이터를 다음과 같이 둔다.

```text
jobs/demo-001/
├── raw_mapping/rec_<mapping-id>/...
└── raw/rec_<demo-id>/...
```

```bash
cd BE/EC2_
docker compose --profile preprocessing up -d --build umi-preprocessor

curl -X POST http://127.0.0.1:18181/internal/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{
    "job_id": "demo-001",
    "dataset_name": "s22_pick_v1",
    "calibration_profile": "s22_20260921",
    "require_mapping": true
  }'

curl http://127.0.0.1:18181/internal/v1/jobs/demo-001
```

완료되면 다음 파일을 GPU 학습 서버로 전달한다.

```text
jobs/demo-001/dataset/s22_pick_v1_gpu_input.tgz
```

## 구성

```text
app/                    Python CLI와 내부 HTTP API
dataset_builder/        검증된 S22 → Stanford UMI Zarr 모듈
track_a/                Atlas 선택·ArUco 정렬·Episode gate 도구
docker/                 ORB-SLAM3 빌드 스크립트
Dockerfile              Ubuntu 24.04 + ORB-SLAM3 + Python
openapi.yaml             내부 API 계약
backend_contract.json   ORB-SLAM Atlas 백엔드 계약
tests/test_package.py    빠른 자체검사
```

기본 보정 프로필은 `dataset_builder/configs`다. 다른 장비·카메라를 붙일 때는 이 값을 조용히
덮어쓰지 말고 `/profiles/<profile-id>`에 새 프로필을 추가한다.

## 범위와 판정

- `job_status.json.status == ready`
- `pipeline_report.json.dataset.training_input_status == ready`
- `<dataset>.zarr.report.json`의 해시와 calibration 상태 확인

위 조건을 통과해도 실제 로봇 파지 성공을 보장하지 않는다. 이는 GPU 학습 입력 승인까지만 뜻한다.
