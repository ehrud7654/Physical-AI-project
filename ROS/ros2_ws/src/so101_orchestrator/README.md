# so101_orchestrator (S15P21A103-115)

BE(제어 평면) ↔ 젯슨 ROS(데이터 평면) 다리.
BE 명령을 **SSE로 받아** 정책 노드를 lifecycle 제어하고, 상태를 **HTTP POST**로 보낸다.
젯슨은 클라이언트(`httpx`) — **FastAPI 아님.**

## 구성

| 파일 | 역할 |
|---|---|
| `orchestrator_node.py` | rclpy 노드. SSE 스레드→큐→타이머로 처리, /estop 발행, heartbeat |
| `be_client.py` | SSE 수신 · 상태 POST · ckpt 다운로드 (httpx) |
| `policy_control.py` | 정책 노드(46) lifecycle 전이 (configure/activate/deactivate) |

## 실행

```bash
pip install httpx          # rosdep 키 없음
colcon build --packages-select so101_orchestrator
source install/setup.bash
ros2 run so101_orchestrator orchestrator \
  --ros-args -p base_url:=http://<EC2>:8000 -p device_id:=jetson-a103
```

## 데이터 흐름

```
BE ──SSE(load_ckpt/start_skill/stop/estop)──> [SSE 스레드] ──큐──> [타이머(메인)]
                                                                   ├─ 정책 lifecycle
                                                                   └─ /estop 발행
[타이머] ──상태(POST)──> BE   (ckpt_ready/skill_running/skill_done/estopped/heartbeat)
```

## 스텁 / 미결 (착수 시점)

- **BE 계약** (SSE/POST 엔드포인트·이벤트 필드·인증·run_id): `../../../docs/deploy_control_interface_draft.md` 제안 기준. `be_client.py`의 `TODO(BE)` 지점만 고치면 됨
- **정책 lifecycle** (46 미구현): 서비스 미준비면 경고만. 46 완성 시 자동 연결
- **ckpt 다운로드·sha256 검증**: `_on_load_ckpt`에 자리만. EC2 경유(EC2가 S3 연동)
- **heartbeat 자원 수집** (cpu/mem/gpu_mem): 이슈 42(8GB 감시)와 연동 예정
