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
`app/trainer/training_adapter.py`의 `TrainingAdapter`를 교체한다.

FastAPI 실행 상태는 중앙 DB나 SQLite에 저장하지 않는다. 재시작 복구에 필요한 최소 상태만
`RUN_JOURNAL_ROOT` 아래 JSON journal로 원자 저장하며 stdout/stderr는 실행별 파일 로그로 관리한다.

## Docker 실행

FastAPI 단독 Compose는 사용하지 않는다. GPU Spring Boot와 함께 상위 `BE/GPU_/compose.yaml`에서 실행한다.

```bash
cd BE/GPU_
docker compose up -d --build
```
