"""정책 노드(so101_policy, S15P21A103-46) lifecycle 제어.

오케스트레이터가 BE 명령을 ROS 동작으로 번역하는 부분.
정책 노드를 managed(lifecycle) 노드로 가정한다:
    load_ckpt  -> configure  (ckpt 로드, 파라미터 세팅)
    start_skill-> activate    (30Hz 추론 루프 시작)
    stop       -> deactivate  (루프 정지, 모터 홀드)

⚠️ 정책 노드(46)가 아직 없으므로, 서비스 클라이언트 호출은 준비만 하고
   대상이 없으면 경고만 남긴다. 46 완성되면 그대로 붙는다.
"""

from __future__ import annotations

from lifecycle_msgs.msg import Transition
from lifecycle_msgs.srv import ChangeState


class PolicyControl:
    """정책 노드의 lifecycle 전이를 호출하는 얇은 래퍼."""

    # 대상 정책 노드 이름 (46에서 확정)
    POLICY_NODE = "so101_policy"

    def __init__(self, node):
        self._node = node
        self._log = node.get_logger()
        # 정책 노드의 표준 lifecycle 서비스: /<node>/change_state
        self._cli = node.create_client(
            ChangeState, f"/{self.POLICY_NODE}/change_state"
        )

    def _change_state(self, transition_id: int, label: str) -> bool:
        if not self._cli.service_is_ready():
            # 정책 노드(46)가 아직 안 떠 있으면 여기로. 골격 단계에선 정상.
            self._log.warn(
                f"정책 노드 lifecycle 서비스 미준비 — '{label}' 전이 스킵 "
                f"(so101_policy/46 미구현?)"
            )
            return False
        req = ChangeState.Request()
        req.transition.id = transition_id
        # 비동기 호출. 결과는 콜백/스핀에서 처리 (여기선 fire-and-forward 골격)
        self._cli.call_async(req)
        self._log.info(f"정책 노드 전이 요청: {label}")
        return True

    # --- BE 명령 → lifecycle 매핑 ------------------------------------------
    def configure(self) -> bool:
        """load_ckpt 후 configure (ckpt 로드 반영)."""
        return self._change_state(Transition.TRANSITION_CONFIGURE, "configure")

    def activate(self) -> bool:
        """start_skill → activate (추론 루프 시작)."""
        return self._change_state(Transition.TRANSITION_ACTIVATE, "activate")

    def deactivate(self) -> bool:
        """stop → deactivate (루프 정지, 모터 홀드)."""
        return self._change_state(Transition.TRANSITION_DEACTIVATE, "deactivate")
