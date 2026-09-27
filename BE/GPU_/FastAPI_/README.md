# SSAFY GPU Training FastAPI

GPU Spring Boot와 실제 Python 학습 프로세스 사이의 내부 실행 API다.

## API

- `POST /internal/training-runs`
- `GET /internal/training-runs/{runId}`
- `POST /internal/training-runs/{runId}/cancel`
- `PATCH /internal/training-runs/{runId}/lease`
- `GET /health/live`
- `GET /health/ready`

내부 API는 `Authorization: Bearer <FASTAPI_SERVICE_TOKEN>`을 요구한다. 실제 AI 학습 구현은
`app/trainer/training_adapter.py`는 공식 UMI diffusion trainer를 다음 순서로 실행한다.

1. `train_policy.py check <zarr>`로 입력 데이터 검사
2. 검사를 통과한 경우에만 `train_policy.py train` 실행
3. trainer 로그의 epoch를 FastAPI 진행률 journal에 기록
4. run 디렉터리에서 무압축 `.tgz` 모델 패키지를 찾아 계약 검증
5. 검증된 모델 패키지와 `metadata.json`을 GPU Spring 완료 callback으로 전달

학습 번들은 `UMI_TRAINING_BUNDLE_ROOT`, Python은 `UMI_TRAINING_PYTHON`, 프로필은
`UMI_TRAINING_PROFILE`로 주입한다. 컨테이너에는 번들을 `/opt/umi-training`으로
읽기 전용 마운트하고 학습 결과는 공유 볼륨의 `UMI_TRAINING_RUNS_DIR`에 저장한다.

FastAPI 실행 상태는 중앙 DB나 SQLite에 저장하지 않는다. 재시작 복구에 필요한 최소 상태만
`RUN_JOURNAL_ROOT` 아래 JSON journal로 원자 저장하며 stdout/stderr는 실행별 파일 로그로 관리한다.

## Docker 실행

FastAPI 단독 Compose는 사용하지 않는다. GPU Spring Boot와 함께 상위 `BE/GPU_/compose.yaml`에서 실행한다.

```bash
cd BE/GPU_
docker compose up -d --build
```

## Worker 식별자와 취소

상위 Compose는 `.env`의 `GPU_WORKER_ID`를 GPU Spring과 FastAPI의 `WORKER_ID`에 함께 전달한다.
`STORAGE_NODE_ID`는 EC2가 해당 worker/nodeKey에 발급한 실제 UUID여야 한다. GPU Spring의
`StorageNode 등록 완료` 로그에서 확인할 수 있다. 두 식별자 중 하나라도 일치하지 않으면
학습 생성은 `403 WORKER_SCOPE_MISMATCH`로 거부되며, FastAPI 로그에 요청값과 설정값이 남는다.

이미 GPU에 할당된 학습은 EC2에서 `CANCEL_REQUESTED`를 유지하고, GPU가 heartbeat로 요청을
수신해 다운로드·전처리 또는 학습 프로세스를 종료한 뒤 `CANCELED`를 보고한다. 기본 heartbeat
간격은 15초다. 전처리 취소는 ORB-SLAM 자식 프로세스까지 종료하며, 취소된 동일 job/attempt는
다시 시작하지 않는다. 통신 실패 시 할당을 유지하고 다음 heartbeat에서 취소를 재시도한다.

취소 처리 변경을 배포할 때는 EC2 백엔드와 GPU의 `gpu-backend`, `training-api`,
`umi-preprocessor` 이미지를 모두 갱신해야 한다.
