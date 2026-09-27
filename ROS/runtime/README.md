# UMI checkpoint runtime

이 디렉터리는 작업 이름과 무관하게 같은 UMI observation/action 계약의 체크포인트를
SO-101에서 실행한다. `pick`, `place`, `push` 같은 작업명은 실행기가 아니라
`models/<checkpoint-name>`에만 둔다.

## 구조

```text
runtime/
├── umi_checkpoint_executor/
│   ├── cli.py                 # 일반 CLI 흐름과 로그
│   ├── policy_runtime.py      # TorchScript 정책과 observation
│   ├── camera_stream.py       # S22 UMI1 TCP 스트림
│   ├── calibration.py         # camera-TCP와 dataset provenance
│   ├── motion_planner.py      # 상대 action, IK, tick 계획
│   └── robot_controller.py    # SO-101 상태, 명령, hold
└── umi_checkpoint_executor_ros2/
    └── node.py                # ROS 2 start/stop process adapter
```

ROS 2 어댑터는 정책·IK·모터 코드를 복사하지 않고 공통 실행기를 자식 프로세스로
실행한다. 현재 ROS 토픽으로 관절 목표를 주고받는 인터페이스는 제공하지 않는다.

## 공통 준비

```sh
cd /home/a103/Workspace/stanford_umi_demo
bash setup/adb_local.sh shell am start -n org.umi.stream/.MainActivity
bash setup/adb_local.sh forward tcp:8765 tcp:8765
export LD_LIBRARY_PATH=.venv/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64
```

현재 Atlas 모델:

```text
models/s22_pick_v3_atlas_v1_e120
```

기본값은 체크포인트의 gripper width를 그대로 tick으로 변환한다.
`--gripper-close-offset-ticks N`을 지정하면 UMI의 닫기 동작에서 마지막 J6 목표에
`+N tick`을 적용하고 그 목표를 고정한다. 이후 UMI가 `N tick` 이상 명확하게 여는
목표를 낼 때만 고정을 해제한다. 예: `--gripper-close-offset-ticks 50`.

## 일반 CLI

Prediction-only 1회:

```sh
.venv/bin/python -m runtime.umi_checkpoint_executor \
  --model models/s22_pick_v3_atlas_v1_e120 \
  --out runs/atlas_prediction_01 \
  --cycles 1
```

계속 실행하고 `Ctrl+C`로 종료:

```sh
.venv/bin/python -m runtime.umi_checkpoint_executor \
  --model models/s22_pick_v3_atlas_v1_e120 \
  --out runs/atlas_live_01 \
  --cycles 0 --max-resamples 10 --move --enable-gripper \
  --gripper-close-offset-ticks 50 \
  --speed 100 --acceleration 30 \
  --max-arm-delta 0 --max-live-arm-delta 0 \
  --max-gripper-delta 0 --max-live-gripper-delta 0 \
  --max-state-drift 0 --settle-timeout 0 --min-joint-margin-ticks 0
```

모터 실행 전 터미널에 `MOVE`를 입력해야 한다. `Ctrl+C`는 현재 tick hold를 시도하고
종료한다. 기존 출력 디렉터리는 덮어쓰지 않는다.

IK 성공 여부, 작업공간, 경로 높이, 관절 여유, tick 변화량은 실행을 차단하지 않는다.
모터 제어 직전에 각 목표만 `common.tick_limits()` 범위로 클램프한다.

## ROS 2 Humble 어댑터

터미널 1:

```sh
cd /home/a103/Workspace/stanford_umi_demo
source /opt/ros/humble/setup.bash
python3 -m runtime.umi_checkpoint_executor_ros2 --ros-args \
  -p move:=false -p cycles:=1
```

터미널 2:

```sh
source /opt/ros/humble/setup.bash
ros2 service call /umi_checkpoint_executor/start std_srvs/srv/Trigger '{}'
ros2 service call /umi_checkpoint_executor/stop std_srvs/srv/Trigger '{}'
```

실물 동작은 노드 실행 시 `-p move:=true`를 지정하고, 실행기 자식 프로세스가 표시하는
`MOVE` 프롬프트를 노드가 실행 중인 터미널에서 확인한다. 추가 CLI 옵션은 ROS 파라미터
`extra_args` 문자열 배열로 전달한다.

## 검사

```sh
LD_LIBRARY_PATH=.venv/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64 \
  .venv/bin/python -m runtime.umi_checkpoint_executor --self-check
```
