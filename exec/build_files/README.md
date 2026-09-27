# 제출용 Docker 이미지

각 TAR는 `docker image save`로 저장한 Linux amd64 이미지입니다. Docker 이미지 이름은 소문자만 허용하므로 요청한 대소문자는 파일명에 적용했습니다.

| 파일 | 포함된 이미지 태그 | 기존 Compose 태그 |
|---|---|---|
| EC2_Backend.tar | ec2_backend:submission | ssafy-ec2-api:local |
| EC2_Frontend.tar | ec2_frontend:submission | ssafy-fe:local |
| EC2_Nginx.tar | ec2_nginx:submission | ssafy-nginx:local |
| GPU_Backend_Spring.tar | gpu_backend_spring:submission | ssafy-gpu-backend:local |
| GPU_Backend_FastAPI.tar | gpu_backend_fastapi:submission | ssafy-gpu-fastapi:local |
| GPU_Backend_Preprocessor.tar | gpu_backend_preprocessor:submission | umi-server-preprocessor:2026.09.23-orbslam-v3 |

## 불러오기

각 파일에 `docker image load -i 파일명.tar`를 실행합니다. 예:

```bash
docker image load -i EC2_Backend.tar
docker image tag ec2_backend:submission ssafy-ec2-api:local
```

나머지 이미지도 표의 기존 Compose 태그로 태깅한 뒤, 저장소의 해당 `BE/EC2_` 또는 `BE/GPU_` 디렉터리에서 환경변수를 준비하고 실행합니다.

```bash
docker compose up -d --no-build
```

`images.json`에는 이미지 ID, 태그, 파일 크기, SHA-256이 기록됩니다. `SHA256SUMS.txt`는 TAR 파일 무결성 확인용입니다. `build-images.ps1`은 저장소 루트를 기준으로 동일 산출물을 다시 빌드하며 프론트 API 주소는 `/api/v1`입니다.

## 별도로 필요한 실행 구성

- 각 서버의 Compose, `.env.example`을 기반으로 작성한 환경설정, GPU Nginx 설정.
- PostgreSQL, Redis, GPU Nginx, DCGM 등 Compose가 사용하는 외부 이미지(오프라인 환경에서는 별도 준비).
- GPU 전처리기의 `profiles` 디렉터리 및 FastAPI가 마운트하는 실제 `training_bundle`과 학습 의존성. 이들은 TAR 이미지에 포함되지 않습니다.
- AWS 접근 권한, S3 데이터/모델, HTTPS 인증서 등 실행 환경별 설정.

이 이미지 묶음에는 DB 볼륨, 실행 중인 작업 데이터, 호스트의 실제 `.env`, AWS 자격증명, TLS 개인키가 포함되지 않습니다. Spring Boot 이미지 빌드는 현재 Dockerfile의 `bootJar -x test` 방식이며 이미지 빌드 성공은 전체 통합 테스트나 실제 GPU 학습 성공을 의미하지 않습니다.
