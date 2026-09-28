"""Installed SO-101 hardware access built only from runtime-local modules."""
from __future__ import annotations

import time
from pathlib import Path

from .robot_calibration import (
    ALL_IDS,
    ARM_IDS,
    GRIPPER_ID,
    gripper_tick_to_width_m,
    tick_limits,
)
from .servo_bus import ST3215Bus, ServoError


ESTOP_FLAG = Path("/tmp/umi_ESTOP")

# 서보 실행 중 안전 한계 (S15P21A103-46). 값 출처: Workspace/ser0070_pid_tuning — 팀 공통.
# 진단은 execute_chunk 가 이미 매 waypoint 읽으므로 게이트 추가에 버스 read 0.
SAFETY_LIMITS = {"max_temperature_c": 60, "min_voltage_raw": 115, "max_abs_load_raw": 750}
# 온도만 3연속 확인 후 중단 — 센서가 튀는 단발값(예: 순간 70C)에 오탐 안 나게.
# 나머지(과부하·저전압·서보 자체 플래그·토크 꺼짐)는 실제 이상이라 즉시.
TEMPERATURE_CONFIRM_SAMPLES = 3


def state_reasons(state, limits=SAFETY_LIMITS) -> list[str]:
    """진단 dict 에서 위반 사유만 뽑는다(예외는 던지지 않음 — 중단 판단은 호출자).
    누락 필드는 안전값으로 본다: 실물 servo_bus 는 항상 전체를 주고, 테스트 fake 는
    최소 dict(tick/torque_enabled)만 주기 때문."""
    reasons = []
    if int(state.get("status_raw", 0)) != 0:                # 서보 자체 과열/과부하 플래그
        reasons.append(f"status_raw={state['status_raw']}")
    if int(state.get("temperature_c", 0)) >= limits["max_temperature_c"]:
        reasons.append(f"temperature_c={state['temperature_c']}")
    if int(state.get("voltage_raw", limits["min_voltage_raw"])) < limits["min_voltage_raw"]:
        reasons.append(f"voltage_raw={state['voltage_raw']}")
    if abs(int(state.get("present_load_raw", 0))) >= limits["max_abs_load_raw"]:
        reasons.append(f"load_raw={state['present_load_raw']}")
    if not bool(state.get("torque_enabled", True)):
        reasons.append("torque_disabled")
    return reasons


def check_diagnostics(diagnostics, consecutive_hot: int) -> tuple[list[str], int]:
    """여러 servo 진단을 한 번에 게이트. 온도는 3연속일 때만, 나머지는 즉시.
    반환: (중단해야 할 사유 목록, 갱신된 온도 연속카운터)."""
    reasons = []
    hot = False
    for servo_id, state in diagnostics.items():
        for r in state_reasons(state):
            reasons.append(f"J{servo_id} {r}")
            if "temperature_c=" in r:
                hot = True
    immediate = [r for r in reasons if "temperature_c=" not in r]
    consecutive_hot = consecutive_hot + 1 if hot else 0
    if consecutive_hot >= TEMPERATURE_CONFIRM_SAMPLES:
        immediate += [r for r in reasons if "temperature_c=" in r]
    return immediate, consecutive_hot


def retry_eintr(call, tries: int = 4):
    for attempt in range(tries):
        try:
            return call()
        except OSError:
            if attempt == tries - 1:
                raise
            time.sleep(0.005)
    raise AssertionError("unreachable")


def read_ticks(bus: ST3215Bus, ids=ALL_IDS) -> dict[int, int | None]:
    ticks = {}
    for servo_id in ids:
        try:
            ticks[servo_id] = retry_eintr(lambda servo_id=servo_id: bus.read_position(servo_id))
        except ServoError:
            ticks[servo_id] = None
    return ticks


def hold_current(
    bus: ST3215Bus,
    ids,
    speed: int = 100,
    acceleration: int = 10,
) -> dict[int, int]:
    ticks = {servo_id: tick for servo_id, tick in read_ticks(bus, ids).items() if tick is not None}
    if ticks:
        retry_eintr(lambda: bus.write_positions(ticks, speed=speed, acceleration=acceleration))
        for servo_id in ticks:
            retry_eintr(lambda servo_id=servo_id: bus.set_torque(servo_id, True))
    return ticks


def refuse_if_estopped() -> None:
    if ESTOP_FLAG.exists():
        raise SystemExit(f"E-STOP flag {ESTOP_FLAG} exists; remove it only after checking the arm")


def self_check() -> None:
    """안전 게이트 로직 검증 — 실물/버스 없이."""
    ok = {"status_raw": 0, "temperature_c": 40, "voltage_raw": 124,
          "present_load_raw": 100, "torque_enabled": True}

    def state(**over):
        s = dict(ok)
        s.update(over)
        return s

    # 정상: 사유 없음
    assert state_reasons(state()) == []
    # 즉시 사유들
    assert state_reasons(state(status_raw=4)) == ["status_raw=4"]
    assert state_reasons(state(voltage_raw=110)) == ["voltage_raw=110"]
    assert state_reasons(state(present_load_raw=-800)) == ["load_raw=-800"]
    assert state_reasons(state(torque_enabled=False)) == ["torque_disabled"]
    # 과열: state_reasons 는 잡지만 게이트는 3연속 요구
    hot = {1: state(temperature_c=60)}
    r1, c1 = check_diagnostics(hot, 0)
    assert r1 == [] and c1 == 1
    r2, c2 = check_diagnostics(hot, c1)
    assert r2 == [] and c2 == 2
    r3, c3 = check_diagnostics(hot, c2)
    assert any("temperature_c=" in r for r in r3) and c3 == 3
    # 한 번 식으면 카운터 리셋
    _, c4 = check_diagnostics({1: state(temperature_c=40)}, 2)
    assert c4 == 0
    # 과부하는 온도와 무관하게 즉시
    r5, _ = check_diagnostics({2: state(present_load_raw=900)}, 0)
    assert r5 == ["J2 load_raw=900"]
    print("ROBOT_HARDWARE_SAFETY_SELF_CHECK_OK")


if __name__ == "__main__":
    self_check()
