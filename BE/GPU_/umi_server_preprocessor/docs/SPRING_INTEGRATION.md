# Spring 연동

## 역할 경계

Spring이 담당할 것:

- 업로드 인증·권한·용량 제한
- `jobId` 발급과 DB 상태
- `/jobs/<jobId>/raw*` 저장
- 내부 전처리 API 호출과 상태 동기화
- 결과 TGZ 다운로드 권한

전처리 컨테이너가 담당할 것:

- 영상, IMU, ArUco, ORB-SLAM3, Atlas, Zarr
- 세션별 제외 사유와 처리 로그
- GPU 입력 TGZ와 SHA-256

Spring 컨테이너에 Docker socket을 마운트하지 않는다. 두 서비스가 같은 Docker 네트워크와
`jobs` 볼륨을 공유하게 한다.

## 호출 순서

1. Spring이 `jobId`를 생성한다. 허용 문자는 영문, 숫자, `_`, `-`이며 최대 64자다.
2. 업로드를 임시 폴더에 완전히 저장하고 검사한 뒤 `/jobs/<jobId>/raw*`로 원자적으로 이동한다.
3. `POST /internal/v1/jobs`를 호출한다.
4. DB 상태를 `PREPROCESSING`으로 바꾼다.
5. `GET /internal/v1/jobs/{jobId}`를 주기적으로 조회한다.
6. `ready`이면 artifact 경로와 SHA-256을 DB에 저장한다.
7. `failed`이면 error와 `worker.log` 위치를 저장한다.

요청 예시:

```json
{
  "job_id": "pick-20260921-001",
  "dataset_name": "s22_pick_v1",
  "calibration_profile": "s22_20260921",
  "require_mapping": true
}
```

응답 코드:

- `202`: 시작 또는 접수
- `400`: 잘못된 ID, 프로필, RAW 경로
- `409`: 다른 전처리 작업 실행 중

현재 v1 컨테이너는 ORB-SLAM 메모리 경합을 피하려고 한 번에 작업 하나만 실행한다. 여러 작업이 필요하면
Spring 큐에서 대기시킨다. 실제 병렬 처리 요구가 생길 때 컨테이너 복제와 job affinity를 추가한다.

## Java 예제

`spring-adapter`는 Spring Boot 3.2+의 `RestClient` 예제다. 기존 프로젝트로 세 Java 파일을 복사하고
패키지명을 바꾼다. 필요한 의존성은 일반적인 `spring-boot-starter-web`과 Jackson뿐이다.

```yaml
umi:
  preprocessor:
    base-url: http://umi-preprocessor:8080
```

업로드 Controller, 사용자 도메인, JPA entity는 서버 프로젝트마다 달라 이 패키지에서 가정하지 않았다.
