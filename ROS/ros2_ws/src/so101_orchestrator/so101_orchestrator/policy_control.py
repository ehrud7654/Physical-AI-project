"""정책 노드(so101_policy, S15P21A103-46) lifecycle 제어.

오케스트레이터가 BE 명령을 ROS 동작으로 번역하는 부분.
정책 노드는 managed(lifecycle) 노드다:
    unconfigured → (configure) → inactive → (activate) → active
현재 상태를 GetState로 읽어 **유효한 전이만** 보낸다. 잘못된 전이는 정책 노드를
크래시시키므로(예: unconfigured에서 activate) 절대 막보내지 않는다.
"""
from __future__ import annotations

import time

from lifecycle_msgs.srv import ChangeState, GetState
from lifecycle_msgs.msg import Transition
from rcl_interfaces.msg import Parameter, ParameterType, ParameterValue
from rcl_interfaces.srv import SetParameters


class PolicyControl:
    POLICY_NODE = "so101_policy"

    def __init__(self, node):
        self._node = node
        self._log = node.get_logger()
        self._change = node.create_client(ChangeState, f"/{self.POLICY_NODE}/change_state")
        self._get = node.create_client(GetState, f"/{self.POLICY_NODE}/get_state")
        self._param = node.create_client(SetParameters, f"/{self.POLICY_NODE}/set_parameters")

    # ── 서비스 호출 헬퍼 (워커 스레드에서 호출; executor가 future 완료시킴) ──
    @staticmethod
    def _wait(fut, timeout: float) -> bool:
        end = time.time() + timeout
        while time.time() < end:
            if fut.done():
                return True
            time.sleep(0.02)
        return False

    def _state(self, timeout: float = 3.0) -> str | None:
        """정책 노드 lifecycle 상태 라벨. 노드 없으면 None."""
        if not self._get.wait_for_service(timeout_sec=timeout):
            return None
        fut = self._get.call_async(GetState.Request())
        if not self._wait(fut, timeout) or fut.result() is None:
            return None
        return fut.result().current_state.label

    def _transition(self, tid: int, label: str, timeout: float = 5.0) -> bool:
        if not self._change.wait_for_service(timeout_sec=timeout):
            self._log.warn(f"정책 change_state 미준비 — '{label}' 스킵")
            return False
        req = ChangeState.Request()
        req.transition.id = tid
        fut = self._change.call_async(req)
        if not self._wait(fut, timeout) or fut.result() is None:
            self._log.warn(f"정책 '{label}' 응답 없음")
            return False
        ok = fut.result().success
        self._log.info(f"정책 전이 {label}: {'OK' if ok else '실패'}")
        return ok

    def _set_ckpt(self, ckpt_path: str) -> None:
        if not self._param.wait_for_service(timeout_sec=3.0):
            self._log.warn("정책 set_parameters 미준비 — ckpt 경로 전달 스킵")
            return
        req = SetParameters.Request(parameters=[Parameter(
            name="ckpt_path",
            value=ParameterValue(type=ParameterType.PARAMETER_STRING, string_value=ckpt_path))])
        self._wait(self._param.call_async(req), 3.0)
        self._log.info(f"정책 ckpt 경로 전달: {ckpt_path}")

    # ── BE 명령 → lifecycle 구동 ────────────────────────────────────────
    def start_skill(self, ckpt_path: str | None) -> bool:
        """정책을 active 로 만든다. 현재 상태에서 configure→activate 까지 안전하게 구동.
        이미 active/inactive 면 cleanup 후 새 ckpt 로 재설정(스킬 교체 대응)."""
        st = self._state()
        if st is None:
            self._log.warn("정책 노드 미기동 — SKILL 실행 불가")
            return False
        if st == "active":
            self._transition(Transition.TRANSITION_DEACTIVATE, "deactivate")
            st = "inactive"
        if st == "inactive":
            self._transition(Transition.TRANSITION_CLEANUP, "cleanup")
            st = "unconfigured"
        if st == "unconfigured":
            if ckpt_path:
                self._set_ckpt(ckpt_path)
            if not self._transition(Transition.TRANSITION_CONFIGURE, "configure"):
                return False
        return self._transition(Transition.TRANSITION_ACTIVATE, "activate")

    def stop_skill(self) -> None:
        """active 면 deactivate (아니면 아무것도 안 함 — 잘못된 전이 방지)."""
        if self._state() == "active":
            self._transition(Transition.TRANSITION_DEACTIVATE, "deactivate")
