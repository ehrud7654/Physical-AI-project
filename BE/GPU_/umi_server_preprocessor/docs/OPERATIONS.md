# 운영·수정 안내

## Docker 빌드

Dockerfile은 Ubuntu 24.04에서 ORB-SLAM3 UMI fork를 commit
`b741dca39015330ef4bcc3a85f89493503ade04b`로 고정하고, 패치 2개를 적용한다. 최초 빌드는
ORB-SLAM3와 Pangolin 컴파일 때문에 오래 걸린다. 사내 Git mirror가 있으면 Dockerfile의 clone URL만 바꾼다.

```bash
docker build --build-arg JOBS=8 -t umi-server-preprocessor:1.0.0 .
```

호스트의 `jobs`, `profiles` 볼륨은 컨테이너 UID `10001`이 쓸 수 있어야 한다.

```bash
sudo chown -R 10001:10001 jobs
```

## 로그와 상태

- `worker.log`: CLI 전체 stdout/stderr
- `pipeline_report.json`: mapping, 포함/제외 세션, 데이터셋 결과
- `processed/**/slam/slam.log`: ORB-SLAM3 세션별 로그
- `job_status.json`: Spring이 읽기 쉬운 최종 상태와 artifact SHA-256

사용자 HTTP 연결이 끊겨도 child process는 계속된다. 컨테이너 자체가 재시작된 경우 같은 job을 다시 요청하면
완료 receipt와 해시가 맞는 단계는 재사용한다.

기존 dataset 이름의 Zarr는 덮어쓰지 않는다. 세션이나 설정을 바꿔 다시 만들 때는
`dataset_name`을 `v2`, `v3`처럼 올린다.

## 설정 변경

- 카메라·그리퍼·camera-TCP 변경: 새 `/profiles/<id>` 추가
- ORB 특징점·IMU 설정 변경: 별도 `s22_mono_inertial.yaml`을 이미지에 반영하고 이미지 버전 증가
- gate 변경: `dataset.json`을 새 프로필로 복사해 변경
- 마커 크기 변경: `s22_slam_tag.json`과 실행 환경의 `INIT_TAG_SIZE_M`를 함께 변경

기본 마커 크기는 0.16 m다. 0.018 m ChArUco ID 13과 혼동하면 Atlas 스케일이 틀어진다.

## 알려진 제한

- ORB-SLAM3는 멀티스레드라 동일 영상도 결과가 조금 달라질 수 있다. 매핑 best-of-3과 시연 재시도 1회로
  흡수하지만 완전한 bit 재현성은 보장하지 않는다.
- 텍스처가 적거나 빠른 움직임·모션 블러가 심하면 재국지화가 실패한다.
- 기본 camera-TCP는 약 10 mm 고정 보드 RMS로 승인된 값이며 실제 파지 성공 보장은 아니다.
- `cancel`은 Linux process group에 SIGTERM을 보낸다. 강제 종료 후에는 같은 job을 재요청해 receipt를 검사한다.
- v1은 작업 한 개만 실행한다. 다중 worker/메시지 브로커는 포함하지 않았다.

## 자체검사

```bash
python tests/test_package.py
python dataset_builder/tests/test_pipeline.py
python dataset_builder/tests/test_slam_tag.py
```

첫 검사는 서버 계약·안전한 압축 해제·GPU 입력 포장을 확인한다. 나머지는 합성 영상에서 Zarr와
테이블 마커 변환을 확인한다. 실제 ORB-SLAM 품질은 실제 mapping/demo 영상으로 별도 확인해야 한다.
