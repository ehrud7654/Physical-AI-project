# so101_driver (S15P21A103-50)

SO-101 하드웨어 드라이버. **ROS 경계(래퍼)** 와 **하드웨어 구현**을 분리한다.

| 파일 | 역할 | 담당 |
|---|---|---|
| `driver_node.py` | ROS 래퍼 — 토픽 ↔ HardwareInterface 연결 | ROS (인터페이스) |
| `hardware_interface.py` | 하드웨어 경계 계약 + `DummyHardware` | 계약=ROS, 구현=팀원 |
| `FeetechHardware` (예정) | Feetech 시리얼·틱변환·Sync R/W | **팀원** |

## 계약 (팀원이 구현할 것)

`HardwareInterface` 를 만족하는 클래스 하나. **단위는 전부 라디안** (틱 변환은 구현체 안에서 so101.yaml 규칙으로).

```python
def read_positions(self) -> list[float]      # 틱 → 라디안 (Sync Read)
def write_positions(self, positions_rad)     # 라디안 → 틱 (Sync Write)
def set_torque(self, enabled: bool)          # Torque Enable (estop)
def read_effort(self) -> list[float] | None  # 부하/전류 (선택, 안전용)
```

`driver_node.py` 의 `DummyHardware` 를 `FeetechHardware(...)` 로 바꾸기만 하면 됨. 노드는 안 건드림.

## ROS 인터페이스 (확정)

| 토픽 | 방향 | 타입 | 단위·규칙 |
|---|---|---|---|
| `/joint_command` | 구독 | `sensor_msgs/JointState` | position=라디안, 순서=so101.yaml |
| `/estop` | 구독 | `std_msgs/Bool` | true=토크 off |
| `/joint_states` | 발행 | `sensor_msgs/JointState` | 라디안, **30Hz, stamp=읽은 시각** |

> `/joint_command` 는 108(so101_msgs) 확정 전이라 `JointState` 사용. 커스텀 `JointCommand` 확정되면 타입만 교체.

## 실행 (더미로)

```bash
colcon build --packages-select so101_driver
source install/setup.bash
ros2 run so101_driver driver
# 확인:
ros2 topic echo /joint_states           # 30Hz로 뜸
ros2 topic pub /joint_command sensor_msgs/JointState \
  '{name: [shoulder_pan], position: [0.5]}' --once   # 더미가 추종 → joint_states에 반영
```
