"""Safety Controller (S15P21A103-49).

관절 리밋·워치독·수동 트리거로 /estop 발행. 드라이버가 /estop 받아 토크 off.
리밋은 so101.yaml 단일 소스에서 읽는다(하드코딩 X). 리밋 없으면 기동 거부(fail-closed).

# ponytail: estop은 한 번 걸리면 유지(래치), 리셋은 노드 재시작. 자동 리셋 위험해서 생략.
"""

from __future__ import annotations

import yaml
import rclpy
from rclpy.node import Node
from rclpy.qos import QoSProfile, DurabilityPolicy
from sensor_msgs.msg import JointState
from std_msgs.msg import Bool


def load_limits(path: str) -> dict[str, tuple[float, float]]:
    """so101.yaml → {joint_name: (lo, hi)} (rad)."""
    with open(path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    return {j["name"]: tuple(j["range_rad"]) for j in cfg["joints"]}


def violations(names, positions, limits, margin: float = 0.0) -> list[str]:
    """범위를 벗어난 관절 이름. margin(rad)만큼 여유."""
    bad = []
    for n, p in zip(names, positions):
        lo, hi = limits.get(n, (float("-inf"), float("inf")))
        if p < lo - margin or p > hi + margin:
            bad.append(n)
    return bad


class SafetyNode(Node):
    def __init__(self):
        super().__init__("so101_safety")
        self.declare_parameter("config", "")          # so101.yaml 경로 (필수)
        self.declare_parameter("watchdog_sec", 0.5)   # /joint_command 끊김 허용

        path = self.get_parameter("config").value
        if not path:
            raise RuntimeError("param 'config' (so101.yaml 경로) 필요 — 리밋 없이 안전 노드 못 돎")
        self._limits = load_limits(path)
        self._wd = float(self.get_parameter("watchdog_sec").value)

        latched = QoSProfile(depth=1, durability=DurabilityPolicy.TRANSIENT_LOCAL)
        self._estop = self.create_publisher(Bool, "/estop", latched)
        self.create_subscription(JointState, "/joint_states", self._on_state, 10)
        self.create_subscription(JointState, "/joint_command", self._on_cmd, 10)
        self.create_subscription(Bool, "/estop_request", self._on_request, 10)

        self._last_cmd = None
        self._tripped = False
        self.create_timer(0.1, self._watchdog)
        self.get_logger().info(f"so101_safety 시작 — {len(self._limits)}관절 리밋, 워치독 {self._wd}s")

    def _trip(self, why: str):
        if self._tripped:
            return  # 이미 걸림 — 래치 QoS가 전달, 재발행 안 함
        self.get_logger().error(f"ESTOP: {why}")
        self._tripped = True
        self._estop.publish(Bool(data=True))  # 한 번만, 래치로 유지

    def _on_state(self, msg: JointState):        # 엔코더 범위 감시
        bad = violations(msg.name, msg.position, self._limits)
        if bad:
            self._trip(f"관절 범위 초과(실측) {bad}")

    def _on_cmd(self, msg: JointState):          # 명령 범위 + 워치독 갱신
        self._last_cmd = self.get_clock().now()
        bad = violations(msg.name, msg.position, self._limits)
        if bad:
            self._trip(f"명령 범위 초과 {bad}")

    def _on_request(self, msg: Bool):            # 수동/버튼 estop
        if msg.data:
            self._trip("수동 estop")

    def _watchdog(self):                         # 명령 끊기면(정책 죽음) 정지
        if self._last_cmd is None:
            return  # 아직 명령 시작 전 = 유휴, 감시 안 함
        gap = (self.get_clock().now() - self._last_cmd).nanoseconds / 1e9
        if gap > self._wd:
            self._trip(f"명령 끊김 {gap:.2f}s > {self._wd}s")


def demo():
    lims = {"a": (-1.0, 1.0), "b": (-2.0, 2.0)}
    assert violations(["a", "b"], [0.0, 0.0], lims) == []
    assert violations(["a", "b"], [1.5, 0.0], lims) == ["a"]
    assert violations(["a", "b"], [0.0, -3.0], lims) == ["b"]
    assert violations(["a"], [1.05], lims, margin=0.1) == []  # 여유 안
    print("ok")


def main(args=None):
    rclpy.init(args=args)
    node = SafetyNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    import sys
    demo() if "--demo" in sys.argv else main()
