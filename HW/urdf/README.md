# SO-101 + Ver1 URDF

진입 파일: [so101_ver1.urdf](so101_ver1.urdf).
`HW/urdf/` 폴더 전체를 함께 받아야 상대 경로 메시가 연결됩니다.

기존 `so101_direct_motor_pipeline/models/urdf/so101_ver1.urdf`와 그 파일이
참조하는 메시 20개를 가져왔습니다. URDF의 메시 경로만
`../assets/`에서 `meshes/`로 바꾸었고, 관절·축·원점·한계값과 메시 내용은 보존했습니다.
원본 URDF SHA-256:
`13265887c1f364340c5326d1eae4833d8f036f4cb0403573ae0d4930ed8860ba`.

- `meshes/so101/`: upstream SO-101 메시 11개.
  TheRobotStudio/SO-ARM100, commit `7629d2ad9853d10fb903093a33ef6114099d97e5`,
  [Apache-2.0 라이선스](../so-arm101/LICENSE).
- `meshes/ver1/`: 기존 Ver1 계산 모델의 커스텀 그리퍼 및 구동 모터 메시 9개.
- URDF와 메시 단위는 **m**, 각 링크 기준 좌표입니다.
  앞서 내보낸 `gripper/stl/`, `so-arm101/ver1-stl/`은 **mm/조립 world 좌표**이므로
  이 URDF의 메시 자리에 그대로 대체하면 안 됩니다.

팔 회전 관절은 `shoulder_pan`, `shoulder_lift`, `elbow_flex`, `wrist_flex`,
`wrist_roll`입니다. `gripper`는 한쪽 슬라이드 이동량(0~0.045 m)이며,
전체 개구폭은 `2 * gripper`(0~0.09 m)입니다.
`gripper_mirror`는 같은 이동량으로 반대 축을 따라 움직이고,
`pinion_passive`는 `-55.5555555556 * gripper` rad로 연동합니다.
뷰어에서 mimic joint를 지원해야 자동 연동이 표시됩니다.
TCP 링크는 `gripper_tcp`입니다.

## 검증

```powershell
python HW/urdf/check_urdf.py
```

11 links / 10 joints의 연결 트리, 46개 메시 참조와 20개 실파일,
메시 데이터·미터 좌표 범위, 0/45/90 mm 개구폭 연동 검사를 통과했습니다.
원본 URDF는 경로 치환을 되돌렸을 때 동일하며, 메시 사본은 원본과 SHA-256이 같습니다.
이번 추가에서는 ROS/RViz 로딩 또는 새 FK/IK·동역학 검증은 수행하지 않았습니다.

이 파일은 기존 시각화·기구학 모델입니다. 커스텀 링크의 측정 관성값이 없으며,
collision 메시와 관절 한계도 실물 안전성을 검증한 값이 아닙니다.
실제 토크·하중 계산이나 로봇 동작 검증 완료를 의미하지 않습니다.
