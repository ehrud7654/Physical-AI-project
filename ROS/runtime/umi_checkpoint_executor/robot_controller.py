"""SO-101 state reads, hold behavior, and checked waypoint execution.

LOCAL PATCH (S15P21A103-46): execute_chunk 에 실행 중 안전 게이트 추가
(robot_hardware.check_diagnostics 호출). upstream(stanford_umi_demo)에는 없음 —
은찬 반영 요청 중. 반영되면 이 주석과 게이트 호출을 제거하고 upstream 을 그대로 받는다.
그 외에는 stanford_umi_demo 원본과 동일하게 유지한다."""
from __future__ import annotations

import sys
import time

# 안전 게이트는 주입된 common(테스트 fake 포함)이 아니라 항상 실제 구현을 쓴다.
from .robot_hardware import check_diagnostics


def read_ticks_with_retry(common, bus, attempts=5):
    ticks = common.read_ticks(bus)
    for _ in range(attempts - 1):
        missing = [servo_id for servo_id in common.ALL_IDS if ticks[servo_id] is None]
        if not missing:
            break
        time.sleep(0.05)
        ticks.update(common.read_ticks(bus, missing))
    return ticks


def read_robot(common, grasp, kinematics, port, required_torque_ids=()):
    with common.ST3215Bus(port) as bus:
        ticks = read_ticks_with_retry(common, bus)
        if any(ticks[i] is None for i in common.ALL_IDS):
            raise RuntimeError(f"servo reply missing: {ticks}")
        if required_torque_ids:
            off = [i for i in required_torque_ids if not bus.read_diagnostics(i)["torque_enabled"]]
            if off:
                raise RuntimeError(f"torque is OFF for servo(s) {off}; refusing checkpoint motion")
    arm_ticks = [int(ticks[i]) for i in common.ARM_IDS]
    q = grasp.tick_to_rad(arm_ticks)
    return {
        "ticks": {int(i): int(ticks[i]) for i in common.ALL_IDS},
        "q": q,
        "pose": kinematics.fk(q),
        "gripper_width_m": float(common.gripper_tick_to_width_m(ticks[common.GRIPPER_ID])),
    }


def motion_ids(common, args):
    return common.ALL_IDS if args.enable_gripper else common.ARM_IDS


def hold_current(common, port, ids):
    try:
        with common.ST3215Bus(port) as bus:
            held = common.hold_current(bus, ids)
        print(f"holding current ticks {held}", flush=True)
    except Exception as error:
        print(f"WARNING: could not issue hold-current: {error}", file=sys.stderr, flush=True)


def clamp_targets(common, targets):
    """Apply calibrated limits at the motor-control boundary."""
    result = {}
    for servo_id, tick in targets.items():
        lower, upper = common.tick_limits(servo_id)
        result[servo_id] = min(max(int(tick), lower), upper)
    return result


def execute_chunk(candidates, common, args, report):
    """Stream four checked goals at the configured action interval."""
    ids = motion_ids(common, args)
    period_s = args.action_interval_ms / 1000
    started = time.monotonic()
    deadline = started
    trace = []
    with common.ST3215Bus(args.motor_port) as bus:
        consecutive_hot = report.get("consecutive_hot", 0)
        for candidate in candidates:
            common.refuse_if_estopped()
            diagnostics = {
                servo_id: common.retry_eintr(lambda servo_id=servo_id: bus.read_diagnostics(servo_id))
                for servo_id in ids
            }
            # 실행 중 안전 게이트 (S15P21A103-46): 과열·과부하·저전압·서보플래그·토크꺼짐.
            # 진단은 위에서 이미 읽었으므로 버스 read 0. 온도만 3연속 확인.
            stop_reasons, consecutive_hot = check_diagnostics(diagnostics, consecutive_hot)
            report["consecutive_hot"] = consecutive_hot
            if stop_reasons:
                raise RuntimeError(f"servo safety gate tripped {stop_reasons}; refusing checkpoint motion")
            live_ticks = {servo_id: int(value["tick"]) for servo_id, value in diagnostics.items()}
            planned_targets = {
                servo_id: candidate["arm_ticks"][servo_id - 1]
                for servo_id in common.ARM_IDS
            }
            if args.enable_gripper:
                planned_targets[common.GRIPPER_ID] = candidate["gripper_tick"]
            targets = clamp_targets(common, planned_targets)
            report["motor_commands_attempted"] += 1
            common.retry_eintr(
                lambda: bus.write_positions(targets, speed=args.speed, acceleration=args.acceleration)
            )
            report["motor_commands_sent"] += 1
            report["executed_waypoints"] = report["motor_commands_sent"]
            trace.append({
                "action_index": candidate["action_index"],
                "sent_elapsed_ms": (time.monotonic() - started) * 1000,
                "precommand_ticks": live_ticks,
                "planned_targets": planned_targets,
                "targets": targets,
                "clamped_targets": {
                    servo_id: {"planned": planned_targets[servo_id], "commanded": tick}
                    for servo_id, tick in targets.items()
                    if tick != planned_targets[servo_id]
                },
            })
            deadline += period_s
            time.sleep(max(0.0, deadline - time.monotonic()))

        final_targets = trace[-1]["targets"]
        settle_started = time.monotonic()
        stopped_since = None
        while args.settle_timeout > 0:
            common.refuse_if_estopped()
            diagnostics = {
                servo_id: common.retry_eintr(lambda servo_id=servo_id: bus.read_diagnostics(servo_id))
                for servo_id in ids
            }
            live_ticks = {servo_id: int(value["tick"]) for servo_id, value in diagnostics.items()}
            remaining = {servo_id: final_targets[servo_id] - live_ticks[servo_id] for servo_id in ids}
            worst = max(map(abs, remaining.values()))
            if worst <= args.settle_tolerance:
                settle_status = "arrived"
                break
            elapsed = time.monotonic() - settle_started
            if not any(value.get("moving", False) for value in diagnostics.values()):
                stopped_since = stopped_since or time.monotonic()
                if time.monotonic() - stopped_since >= 1:
                    if worst <= args.settle_residual:
                        settle_status = "settled_with_residual"
                        break
                    raise RuntimeError(f"servos stopped short of final target: {remaining}")
            else:
                stopped_since = None
            if elapsed > args.settle_timeout:
                raise RuntimeError(f"final target timeout: remaining {remaining}")
            time.sleep(0.1)

        if args.settle_timeout == 0:
            diagnostics = {
                servo_id: common.retry_eintr(lambda servo_id=servo_id: bus.read_diagnostics(servo_id))
                for servo_id in ids
            }
            live_ticks = {servo_id: int(value["tick"]) for servo_id, value in diagnostics.items()}
            remaining = {servo_id: final_targets[servo_id] - live_ticks[servo_id] for servo_id in ids}
            settle_status = "skipped"
        report["settle_status"] = settle_status
        report["settle_elapsed_ms"] = (time.monotonic() - settle_started) * 1000
        report["final_ticks"] = live_ticks
        report["final_error_ticks"] = remaining
    report["execution_trace"] = trace
