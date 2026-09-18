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
from rcl_interfaces.msg import Parameter, ParameterType, ParameterValue
from rcl_interfaces.srv import SetParameters


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
        # ckpt 경로 주입용 파라미터 서비스: /<node>/set_parameters
        self._param_cli = node.create_client(
            SetParameters, f"/{self.POLICY_NODE}/set_parameters"
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

    def _set_ckpt_path(self, ckpt_path: str) -> bool:
        """정책 노드의 ckpt_path 파라미터 세팅. configure 직전 호출 → on_configure 가 읽어 로드."""
        if not self._param_cli.service_is_ready():
            self._log.warn("정책 노드 set_parameters 미준비 — ckpt 경로 전달 스킵")
            return False
        req = SetParameters.Request(parameters=[Parameter(
            name="ckpt_path",
            value=ParameterValue(type=ParameterType.PARAMETER_STRING, string_value=ckpt_path))])
        self._param_cli.call_async(req)
        self._log.info(f"정책 ckpt 경로 전달: {ckpt_path}")
        return True

    # --- BE 명령 → lifecycle 매핑 ------------------------------------------
    def configure(self, ckpt_path: str | None = None) -> bool:
        """ckpt 경로를 파라미터로 넣고(있으면) configure 전이.
        ⚠️ call_async 순서 의존(param → transition). 단일스레드 정책 노드에선 순서 보장.
        엄밀한 await 는 46 확정 후."""
        if ckpt_path:
            self._set_ckpt_path(ckpt_path)
        return self._change_state(Transition.TRANSITION_CONFIGURE, "configure")

    def activate(self) -> bool:
        """start_skill → activate (추론 루프 시작)."""
        return self._change_state(Transition.TRANSITION_ACTIVATE, "activate")

    def deactivate(self) -> bool:
        """stop → deactivate (루프 정지, 모터 홀드)."""
        return self._change_state(Transition.TRANSITION_DEACTIVATE, "deactivate")
