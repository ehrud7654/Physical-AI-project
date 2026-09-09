#!/usr/bin/env bash
# so101_policy (S15P21A103-46) — FakeBus 루프 헤드리스 검증.
#
# bringup(FakeBus) → 정책 노드(더미 사인) configure/activate → /joint_states 샘플
# → 발행률 → deactivate 까지 자동으로 돌리고 전부 정리한다.
# 성공 기준: lifecycle 전이 3개 "Transitioning successful", /commands·/joint_states ≈30Hz,
#            샘플마다 6관절 값이 변함(사인파 추종).
#
# 사용:  bash ROS/ros2_ws/src/so101_policy/scripts/verify_fakebus_loop.sh
#        (사전: colcon build, ROS_DOMAIN_ID 는 있으면 그대로, 없으면 42(PC 시뮬))
set +u   # ROS setup.bash 가 -u 와 충돌

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../.." && pwd)"            # S15P21A103/
WS="$REPO/ROS/ros2_ws"
CFG="${SO101_CONFIG:-$REPO/AI/configs/so101.yaml}"
export ROS_DOMAIN_ID="${ROS_DOMAIN_ID:-42}"
LOG="${LOG_DIR:-/tmp/so101_policy_verify}"

source /opt/ros/humble/setup.bash
source "$WS/install/setup.bash" || { echo "install/ 없음 — 먼저 colcon build"; exit 1; }
rm -rf "$LOG"; mkdir -p "$LOG"

cleanup() {
  kill "${PP:-}" "${BP:-}" 2>/dev/null; sleep 1
  pkill -f ros2_control_node; pkill -f policy_node; pkill -f spawner; pkill -f robot_state_pub
}
trap cleanup EXIT
cleanup 2>/dev/null
# 데몬이 죽어 있으면 CLI 가 "!rclpy.ok()" 로 실패한다 → 재시작
ros2 daemon stop >/dev/null 2>&1; ros2 daemon start >/dev/null 2>&1

ros2 launch so101_bringup bringup.launch.py > "$LOG/bringup.log" 2>&1 &
BP=$!
sleep 8
echo "=== controllers"; timeout 10 ros2 control list_controllers 2>&1

ros2 run so101_policy policy_node --ros-args -p "config_path:=$CFG" > "$LOG/policy.log" 2>&1 &
PP=$!
sleep 3
echo "=== lifecycle"
timeout 20 ros2 lifecycle set /so101_policy configure 2>&1 | tail -1
timeout 20 ros2 lifecycle set /so101_policy activate 2>&1 | tail -1
timeout 10 ros2 lifecycle get /so101_policy 2>&1 | tail -1
sleep 1
for i in 0 1 2 3; do
  echo "=== sample t$i"
  timeout 5 ros2 topic echo --once --field position /joint_states 2>&1 | tr '\n' ' '; echo
  sleep 1.2
done
echo "=== hz commands";     timeout 6 ros2 topic hz /forward_position_controller/commands 2>&1 | grep -m1 "average rate"
echo "=== hz joint_states"; timeout 6 ros2 topic hz /joint_states 2>&1 | grep -m1 "average rate"
timeout 20 ros2 lifecycle set /so101_policy deactivate 2>&1 | tail -1
sleep 1
echo "=== after deactivate (/commands 발행자 없어야 함)"
timeout 4 ros2 topic hz /forward_position_controller/commands 2>&1 | tail -1
echo "=== policy.log"; tail -5 "$LOG/policy.log"
echo "=== bringup errors"; grep -i "error\|fail\|traceback" "$LOG/bringup.log" | grep -v "FIFO RT scheduling" | head -5
echo "로그: $LOG"
