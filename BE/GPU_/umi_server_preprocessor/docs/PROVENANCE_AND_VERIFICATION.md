# 출처와 검증 범위

## 고정 버전

- 패키지: `1.0.0`
- 기반 OS: Ubuntu 24.04, `linux/amd64`
- ORB-SLAM3 UMI fork: `cheng-chi/ORB_SLAM3`
- ORB-SLAM3 commit: `b741dca39015330ef4bcc3a85f89493503ade04b`
- 적용 패치:
  - `0001-use-recorded-sensor-timestamps.patch`
  - `0002-relax-post-relocalization-inliers.patch`

ORB-SLAM3 소스와 vocabulary는 이 압축에 복사하지 않았다. Docker 빌드가 고정 commit을 clone하고
패치를 적용한다. 폐쇄망에서는 서버 담당자가 승인된 mirror URL로 Dockerfile을 바꿔야 한다.

## 기본 보정 프로필 SHA-256

```text
s22_camera_tcp.json  4e16c0a2db7e3a4c3f71246201af13be19512560bfa6eb8ec9105f567852104a
s22_camera_imu.json  ee8f06d625b2160d1950b0cc18c32a125e9d111294a9565118838cdcfd9cdee2
s22_gripper.json     5cb18c229151a64a6ccf24453af74d8cb66752c76fc8359433d19c61b5100b16
s22_slam_tag.json    1f541674af882f34f7c502d92ef1c1c22cc752140de87be7ae98c0ae0005cbe7
dataset.json         0e4208e2065260211a58dfaded09fdd005e97989b3eac62782f80f9e6f805ccc
```

- camera-TCP 상태: `physically_validated`
- `physical_deployment_allowed`: `true`
- 테이블 마커: ArUco ID 13, 0.16 m

보정 파일을 수정하면 기존 이름을 유지하지 말고 새 profile ID를 만들고 해시를 다시 기록한다.

## 2026-09-21 패키지 검사

통과:

```text
PACKAGE_SELF_TEST_OK
PIPELINE_SELF_TEST_OK
SLAM_TAG_SELF_TEST_OK position_error_mm=10.31 rotation_error_deg=0.319
PIPELINE_IMPORT_OK
OPENAPI_YAML_OK
```

검사한 범위:

- 경로 검증과 안전한 TGZ 추출
- Zarr/report GPU 입력 포장
- 합성 영상 → TCP label → Zarr 저장·재로딩
- 160 mm 테이블 마커 좌표 복원
- 서버용 파이프라인 import와 기본 gate

이 Windows 환경에는 Docker CLI와 FastAPI가 설치되어 있지 않아 Docker image의 실제 build/start는 수행하지
못했다. 서버 인수 시 첫 검사는 `docker compose build`, `/health`, 실제 mapping 1편 + 성공 시연 2편의
end-to-end 실행이다.

ORB-SLAM3의 배포·수정 시 해당 프로젝트 라이선스도 서버 담당자가 확인한다.
