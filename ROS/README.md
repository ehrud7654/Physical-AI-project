# ROS — so101 ROS2 워크스페이스

SO-101 로봇 팔 제어를 위한 ROS2 (Humble) 워크스페이스.

## 구조

```
ros2_ws/src/
├── so101_msgs    # 커스텀 메시지/인터페이스 (ament_cmake) — .msg 내용은 이슈 108
├── so101_bringup # launch 파일 (sim/real)
├── so101_policy  # 정책/제어 노드 (이슈 46)
├── so101_driver  # 하드웨어 드라이버 (이슈 50)
└── so101_safety  # 세이프티 노드 (이슈 49)
```

## 빌드

```bash
cd ROS/ros2_ws
source /opt/ros/humble/setup.bash
colcon build
source install/setup.bash
```

## 참고

- ROS2 Humble / Ubuntu 22.04
- `build/`, `install/`, `log/` 는 아키텍처별 산출물이라 커밋하지 않음 (.gitignore)
- 현재 브랜치(S15P21A103-44)는 **빈 패키지 골격만**. 실제 메시지/노드 구현은 각 이슈 브랜치에서.
