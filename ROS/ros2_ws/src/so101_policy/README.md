# so101_policy

정책 노드입니다. 모델 추론으로 EE pose를 만들고, IK로 관절각을 구해서 30Hz로 `/forward_position_controller/commands`에 보내는 역할입니다. 관련 이슈는 46입니다.

지금은 뼈대만 있습니다. 관측, 추론, IK 부분이 비어 있고, 모델이 없으면 사인파로 팔을 흔들면서 전체 연결이 제대로 되는지만 확인합니다.

이 노드는 시뮬 트랙에서만 씁니다. 오케스트레이터 `executor:=ros2_policy`일 때 동작하고, 젯슨 실물 추론은 UMI 런타임(`ROS/runtime/`)이 따로 합니다.

## 파일

| 파일 | 역할 |
|---|---|
| `policy_node.py` | lifecycle 노드 `/so101_policy`. configure에서 설정을 읽고, activate하면 30Hz로 명령을 보내고, deactivate하면 멈춥니다. `/joint_states`는 관절 이름으로 매핑해서 순서가 섞여도 괜찮습니다 |
| `dummy.py` | 사인파 더미. 관절 범위 안에서만 움직이도록 진폭을 범위의 1/4로 잡았습니다 |
| `config.py` | `AI/configs/so101.yaml`에서 관절 순서와 범위를 읽습니다 |
| `scripts/verify_fakebus_loop.sh` | FakeBus로 전체 루프를 자동 확인하는 스크립트 |
| `test/test_dummy.py` | 더미 출력이 범위 안에 있는지 확인 (ROS 없이 실행 가능) |

## 파라미터

| 이름 | 기본값 | 설명 |
|---|---|---|
| `config_path` | 없음 | so101.yaml 경로. 비워두면 `~/S15P21A103/AI/configs/so101.yaml`, `/opt/so101/so101.yaml` 순으로 찾습니다 |
| `dummy` | `true` | 모델을 붙이기 전까지 사인파 모드 |

## 실행과 확인

```bash
colcon build --packages-select so101_policy
colcon test --packages-select so101_policy && colcon test-result --verbose

# bringup, 정책 노드, lifecycle 전이, /joint_states 확인까지 한 번에
bash src/so101_policy/scripts/verify_fakebus_loop.sh
```

직접 돌려볼 때는 터미널 두 개로 합니다.

```bash
ros2 launch so101_bringup bringup.launch.py                       # 터미널 1
ros2 run so101_policy policy_node --ros-args -p config_path:=$HOME/S15P21A103/AI/configs/so101.yaml &
ros2 lifecycle set /so101_policy configure
ros2 lifecycle set /so101_policy activate
ros2 topic echo /joint_states                                     # 관절 값이 사인파로 바뀌면 정상
```

실제로는 오케스트레이터(`so101_orchestrator/policy_control.py`)가 같은 서비스(`/so101_policy/change_state`)를 호출합니다. 위 명령은 그걸 손으로 해보는 것입니다.

## 채워야 할 부분

| 메서드 | 내용 | 담당 |
|---|---|---|
| `_build_obs()` | `/joint_states`와 카메라로 관측 만들기. 정규화는 여기서만 합니다 | AI |
| `_infer(obs)` | 모델 추론으로 EE pose(상대값)와 그리퍼 값 구하기. 모델 메타데이터 확인 포함 | AI |
| `_ik(ee_pose)` | EE pose를 관절각으로 변환 | ROS |

`_ckpt`가 비어 있으면 더미로 돌고, 이 세 부분을 채우고 모델을 불러오면 실제 경로로 돕니다.
