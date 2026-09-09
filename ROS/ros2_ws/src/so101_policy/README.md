# so101_policy (S15P21A103-46)

정책 노드. **lifecycle 노드**로, ckpt 추론 → EE pose → IK → 관절각[rad] 6개를
30Hz 로 `/forward_position_controller/commands` 에 발행한다.
지금은 **골격 + 더미 모드**: ckpt·관측·IK 는 슬롯(`NotImplementedError`)만 비워 두었고,
ckpt 가 없으면 사인파로 팔을 흔들어 배관을 검증한다.

## 구성

| 파일 | 역할 |
|---|---|
| `policy_node.py` | LifecycleNode `/so101_policy`. configure(설정 로드·통신 생성) / activate(30Hz 타이머) / deactivate(정지). `/joint_states` 구독은 **이름 매핑**(순서 섞여도 안전), 발행 순서 = so101.yaml |
| `dummy.py` | 사인파 더미. 캘리브 중앙(0) 기준, 진폭 = `min(|lo|,|hi|)×0.25` 라 비대칭 범위(그리퍼)도 안 벗어남 |
| `config.py` | `AI/configs/so101.yaml` 로더 — 관절 순서·범위 단일 출처 |
| `scripts/verify_fakebus_loop.sh` | FakeBus 루프 헤드리스 자동 검증 |
| `test/test_dummy.py` | 더미 출력이 항상 범위 안인지 (ROS 없이 실행) |

## 파라미터

| 이름 | 기본 | 설명 |
|---|---|---|
| `config_path` | `""` | so101.yaml 경로. 빈값이면 `~/S15P21A103/AI/configs/so101.yaml` → `/opt/so101/so101.yaml` 순 탐색 |
| `dummy` | `true` | ckpt 붙기 전 사인파 모드 |

## 실행 · 검증

```bash
colcon build --packages-select so101_policy
colcon test  --packages-select so101_policy && colcon test-result --verbose   # test_dummy·flake8·pep257

# FakeBus 루프 (bringup + 정책 노드 + lifecycle + /joint_states 샘플, 자동 정리)
bash src/so101_policy/scripts/verify_fakebus_loop.sh
```

수동으로 돌릴 때(터미널 2개):

```bash
ros2 launch so101_bringup bringup.launch.py                       # 터미널 1
ros2 run so101_policy policy_node --ros-args -p config_path:=$HOME/S15P21A103/AI/configs/so101.yaml &
ros2 lifecycle set /so101_policy configure
ros2 lifecycle set /so101_policy activate
ros2 topic echo /joint_states                                     # 6관절 사인파로 움직이면 OK
```

lifecycle 전이는 오케스트레이터(`so101_orchestrator/policy_control.py`)가 같은 서비스
(`/so101_policy/change_state`)로 부른다 — 위 수동 명령은 그걸 손으로 대신하는 것.

## 슬롯 (채울 것)

| 메서드 | 내용 | 담당 |
|---|---|---|
| `_build_obs()` | /joint_states(+카메라) → 관측 텐서. 정규화는 여기 한 곳 | AI (관측 스펙 합의 후) |
| `_infer(obs)` | ckpt 추론 → EE pose(상대) + 그리퍼. ckpt 메타(`contract_version`·`action_space`) 검증 | AI |
| `_ik(ee_pose)` | EE pose → 관절각[rad]. `solve_pose_ik` → pinocchio 포팅 | ROS |

`_ckpt` 가 `None` 이면 자동으로 더미 경로를 탄다. 슬롯을 채우고 ckpt 를 로드하면 그대로 실전 경로.
