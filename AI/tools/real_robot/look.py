"""Per-cycle dump of the newest runtime_jt run. / 최신 런 사이클별 요약."""
import glob, json, os, sys
from runtime_jt.umi_checkpoint_executor import robot_calibration as g, kinematics as k, robot_hardware as hw
from runtime_jt.umi_checkpoint_executor.robot_calibration import gripper_tick_to_width_m as w

A = list(hw.ARM_IDS)
runs = sorted(glob.glob("runs/jt_*"), key=os.path.getmtime)
run = sys.argv[1] if len(sys.argv) > 1 else runs[-1]


def xz(d):
    t = k.fk(g.tick_to_rad([int(d[str(i)]) for i in A]))
    return t[0, 3] * 1000, t[2, 3] * 1000


rows = [json.loads(l) for l in open(f"{run}/cycles.jsonl")]
print("RUN", run, "· cycles", len(rows))
print("  c status  settle                  x      z   err | 개구  래치(정책→목표) 재명령 att")
bad = 0
for r in rows:
    if "final_ticks" not in r:
        bad += 1
        print(f"c{r['cycle']:2d} {r.get('status','?')} 기록 미완 · execution_error="
              f"{str(r.get('execution_error'))[:80]}")
        continue
    fx, fz = xz(r["final_ticks"])
    _, tz = xz(r["execution_trace"][-1]["targets"])
    lat = r.get("gripper_latch")
    ls = f"{lat['policy_tick']}→{lat['target_tick']}" if lat else "-"
    print(f"c{r['cycle']:2d} {r.get('status','?'):6s} {r.get('settle_status','?'):20s}"
          f" {fx:6.1f} {fz:6.1f} {fz-tz:+5.1f} | {w(int(r['final_ticks']['6']))*1000:5.1f}"
          f" {ls:>13s} {r.get('settle_recommand_attempts',0)} {r.get('attempt','?')}")
print(f"미완 기록 {bad} / 전체 {len(rows)}")
last = rows[-1]
for key in ("execution_error", "status", "candidates", "executable_waypoints",
            "motor_commands_sent", "gripper_latch"):
    if key in last:
        print(f"last.{key} = {str(last[key])[:200]}")
