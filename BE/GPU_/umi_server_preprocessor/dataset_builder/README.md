# Internal dataset builder

이 폴더는 `app/src/umi_preprocessor/pipeline.py`가 호출하는 S22 → Stanford UMI ReplayBuffer
모듈이다. 서버 운영자는 최상위 `README.md`부터 읽는다.

- 직접 실행: `python build_dataset.py --help`
- 합성 Zarr 검사: `python tests/test_pipeline.py`
- 160 mm 테이블 마커 검사: `python tests/test_slam_tag.py`
- Linux ORB-SLAM3 wrapper: `slam/run_orbslam_linux.sh`

보정 파일은 `configs/`에 있으며, 운영 환경에서 변경할 때는 기존 파일을 덮어쓰지 않고 새
`/profiles/<profile-id>`를 만든다.
