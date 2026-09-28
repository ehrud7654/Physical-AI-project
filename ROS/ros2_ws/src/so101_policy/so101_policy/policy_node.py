"""
정책 노드 (S15P21A103-46) — lifecycle 노드 골격.

역할: ckpt 추론 → EE pose → IK → 관절각(rad) 을 30Hz 로
      /forward_position_controller/commands 에 발행.

지금은 **골격**이다:
  - ckpt 추론 슬롯(_infer)·IK 슬롯(_ik) 은 아직 빈다 (AI ckpt·pinocchio IK 붙을 자리).
  - ckpt 없으면 **더미 모드**(사인파)로 돈다 → 실물·ckpt 없이 배관·데모 검증.

lifecycle (오케스트레이터 policy_control.py 계약과 일치):
  configure  = ckpt 로드·IK 준비   activate = 30Hz 루프 시작   deactivate = 정지
"""
from __future__ import annotations

import os

import rclpy
from rclpy.lifecycle import LifecycleNode, State, TransitionCallbackReturn
from sensor_msgs.msg import JointState
from std_msgs.msg import Float64MultiArray

from so101_policy.config import load_joints
from so101_policy.dummy import dummy_command

COMMAND_TOPIC = "/forward_position_controller/commands"
RATE_HZ = 30.0


class PolicyNode(LifecycleNode):
    def __init__(self):
        super().__init__("so101_policy")
        self.declare_parameter("config_path", "")  # so101.yaml. 빈값이면 패키지 기본 탐색
        self.declare_parameter("dummy", True)       # ckpt 붙기 전 사인파 모드
        self.declare_parameter("ckpt_path", "")     # 오케스트레이터가 configure 직전 세팅(배포 ckpt)
        self._joints = None       # [(name, lo, hi)] so101.yaml 순서
        self._latest = {}         # name -> 현재 관절각(rad), /joint_states 에서
        self._pub = None
        self._sub = None
        self._timer = None
        self._t0 = None
        self._ckpt = None         # 추론 모델 (AI 붙을 자리)

    # ── lifecycle ──────────────────────────────────────────────────────
    def on_configure(self, state: State) -> TransitionCallbackReturn:
        path = self.get_parameter("config_path").value or None
        try:
            self._joints = load_joints(path)
        except Exception as e:  # noqa: BLE001 — 설정 못 읽으면 configure 실패가 맞다
            self.get_logger().error(f"so101.yaml 로드 실패: {e}")
            return TransitionCallbackReturn.FAILURE

        # ckpt 로드 슬롯. 오케스트레이터가 넘긴 경로를 여기서 로드해야 함.
        ckpt = self.get_parameter("ckpt_path").value
        if ckpt:
            if not os.path.exists(ckpt):
                self.get_logger().error(f"ckpt 없음: {ckpt}")
                return TransitionCallbackReturn.FAILURE
            # TODO(46): 실제 로드 = AI 추론 함수(load_ckpt). 지금은 경로만 인지, 추론은 미구현.
            #   self._ckpt 를 truthy 로 만들면 _infer(미구현)로 빠지므로 더미 유지.
            self.get_logger().info(f"ckpt 경로 수신: {ckpt} — 추론 미구현이라 더미로 실행")
        else:
            self.get_logger().info("ckpt_path 없음 — 더미(사인파) 모드")

        self._pub = self.create_lifecycle_publisher(Float64MultiArray, COMMAND_TOPIC, 10)
        self._sub = self.create_subscription(
            JointState, "/joint_states", self._on_joint_state, 10)
        self.get_logger().info(f"configure 완료: 관절 {len(self._joints)}개")
        return TransitionCallbackReturn.SUCCESS

    def on_activate(self, state: State) -> TransitionCallbackReturn:
        self._t0 = self.get_clock().now()
        self._timer = self.create_timer(1.0 / RATE_HZ, self._control_step)
        self.get_logger().info(f"activate: {RATE_HZ}Hz 루프 시작")
        return super().on_activate(state)

    def on_deactivate(self, state: State) -> TransitionCallbackReturn:
        if self._timer is not None:
            self._timer.cancel()
            self._timer = None
        self.get_logger().info("deactivate: 루프 정지")
        return super().on_deactivate(state)

    def on_cleanup(self, state: State) -> TransitionCallbackReturn:
        self._teardown()
        return TransitionCallbackReturn.SUCCESS

    def on_shutdown(self, state: State) -> TransitionCallbackReturn:
        self._teardown()
        return TransitionCallbackReturn.SUCCESS

    def _teardown(self):
        for h in (self._timer,):
            if h is not None:
                h.cancel()
        if self._pub is not None:
            self.destroy_publisher(self._pub)
        if self._sub is not None:
            self.destroy_subscription(self._sub)
        self._timer = self._pub = self._sub = None

    # ── 콜백 ───────────────────────────────────────────────────────────
    def _on_joint_state(self, msg: JointState):
        for name, pos in zip(msg.name, msg.position):
            self._latest[name] = pos

    def _control_step(self):
        cmd = self._compute_command()
        out = Float64MultiArray()
        out.data = cmd
        self._pub.publish(out)

    # ── 명령 계산 ──────────────────────────────────────────────────────
    def _compute_command(self) -> list[float]:
        """so101.yaml 순서로 관절각 6개[rad] 반환."""
        if self._ckpt is None:  # 더미 모드
            t = (self.get_clock().now() - self._t0).nanoseconds * 1e-9
            return dummy_command(t, self._joints)
        # 실제 파이프라인 (AI ckpt·IK 붙으면 여기 채움)
        obs = self._build_obs()          # noqa: F841 — 슬롯
        ee_pose = self._infer(obs)
        return self._ik(ee_pose)

    # ── 슬롯 (미구현) ──────────────────────────────────────────────────
    def _build_obs(self):
        # 관측 벡터 구성 (관절각 + 카메라). AI 관측 스펙 확정 후.
        raise NotImplementedError("관측 스펙 미확정 (AI)")

    def _infer(self, obs):
        # ckpt 추론 → EE pose [T,10] (UMI). AI 추론 코드 붙을 자리.
        raise NotImplementedError("ckpt 추론 미구현 (AI)")

    def _ik(self, ee_pose):
        # EE pose → 관절각[rad]. solve_pose_ik 알고리즘을 pinocchio 백엔드로 포팅해 붙일 자리.
        raise NotImplementedError("IK 미구현 (pinocchio 포팅 예정)")


def main(args=None):
    rclpy.init(args=args)
    node = PolicyNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.try_shutdown()


if __name__ == "__main__":
    main()
