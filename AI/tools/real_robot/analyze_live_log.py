"""Judge a SO-101 live-run cycles.jsonl: what was planned, sent, and actually reached.
실물 구동 로그를 판정한다 — 계획·전송·실제 도달을 따로 센다.

왜 — "명령을 보냈다"와 "팔이 갔다"는 다른 사건이다. 둘을 같은 출력으로 찍으면
실패가 성공으로 보인다. 이 도구는 세 단계를 각각 모수와 함께 센다:
    checkpoint_waypoints  정책이 낸 것
    executable_waypoints  안전 검사를 통과한 것
    executed_waypoints    실제로 나간 것
그리고 final_error_ticks 로 **간 결과**를 본다.
"""
from __future__ import annotations

import argparse, json, statistics as st, sys, tempfile
from pathlib import Path

# 게이트 — 근거 없는 값은 쓰지 않는다. 전부 로그 자체가 들고 있는 기준을 쓴다.
GATE = {
    "executed_ratio_min": 1.0,   # executable 중 실제 나간 비율
    "ik_ok_ratio_min":    1.0,   # candidates 의 ik_ok
    "clamped_max":        0,     # 관절한계 클램프는 0 이어야 한다
    "drift_abs_max":      5,     # 추론 중 팔이 움직인 틱
}
ARM_IDS = ("1", "2", "3", "4", "5")
GRIPPER_ID = "6"


def load(path: Path) -> list[dict]:
    """Read cycles.jsonl, skipping unparsable lines but counting them.
    cycles.jsonl 을 읽는다. 깨진 줄은 건너뛰되 센다."""
    rows, bad = [], 0
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            bad += 1
    if bad:
        print(f"  !! 파싱 실패 {bad} 줄 — 무시하지 않고 센다")
    return rows


def summarize(rows: list[dict]) -> dict:
    """Aggregate one run. Missing fields stay None, never 0.
    한 런을 집계한다. 없는 필드는 0 이 아니라 None 으로 남긴다."""
    n = len(rows)
    out: dict = {"cycles": n, "missing": []}
    if not n:
        return out

    def col(key, src=None):
        vals = [(src or r).get(key) for r in rows for src in [r]]
        vals = [v for v in vals if v is not None]
        if len(vals) < n:
            out["missing"].append(f"{key} {len(vals)}/{n}")
        return vals

    out["phase"] = {p: sum(1 for r in rows if r.get("phase") == p)
                    for p in sorted({r.get("phase") for r in rows})}
    out["status"] = {s: sum(1 for r in rows if r.get("status") == s)
                     for s in sorted({r.get("status") for r in rows})}

    for k in ("checkpoint_waypoints", "executable_waypoints", "executed_waypoints",
              "motor_commands_sent", "motor_commands_attempted"):
        v = col(k)
        out[k] = sum(v) if v else None

    lat = col("latency_ms")
    ai = col("action_interval_ms")
    out["latency_ms"] = {"중앙": round(st.median(lat), 1), "최대": round(max(lat), 1)} if lat else None
    out["action_interval_ms"] = ai[0] if ai else None
    if lat and ai:
        out["latency_over_interval"] = round(st.median(lat) / ai[0], 1)

    oi, ot, tol = col("observation_interval_ms"), col("observation_interval_target_ms"), col("observation_interval_tolerance_ms")
    if oi and ot and tol:
        bad = sum(1 for v in oi if abs(v - ot[0]) > tol[0])
        out["observation_interval"] = {"중앙": round(st.median(oi), 2), "목표": ot[0],
                                       "허용": tol[0], "벗어남": f"{bad}/{len(oi)}"}

    drift = [max(abs(x) for x in r["post_inference_drift_ticks"])
             for r in rows if r.get("post_inference_drift_ticks")]
    out["drift_ticks_max"] = max(drift) if drift else None
    if len(drift) < n:
        out["missing"].append(f"post_inference_drift_ticks {len(drift)}/{n}")

    marg = [min(r["observation_joint_margin_ticks"])
            for r in rows if r.get("observation_joint_margin_ticks")]
    out["joint_margin_min"] = min(marg) if marg else None

    # candidates — 웨이포인트 단위
    cands = [c for r in rows for c in r.get("candidates", [])]
    out["candidates"] = len(cands)
    if cands:
        okc = sum(1 for c in cands if c.get("ik_ok") is True)
        out["ik_ok"] = f"{okc}/{len(cands)}"
        res = [c["ik_residual_mm"] for c in cands if c.get("ik_residual_mm") is not None]
        out["ik_residual_mm"] = {"중앙": round(st.median(res), 4), "최대": round(max(res), 4)} if res else None
        app = [c["approach_error_deg"] for c in cands if c.get("approach_error_deg") is not None]
        out["approach_error_deg"] = {"중앙": round(st.median(app), 3), "최대": round(max(app), 3)} if app else None
        out["workspace_ok"] = f"{sum(1 for c in cands if c.get('workspace_ok') is True)}/{len(cands)}"
        out["limits_ok"] = f"{sum(1 for c in cands if c.get('limits_ok') is True)}/{len(cands)}"
        out["safe"] = f"{sum(1 for c in cands if c.get('safe') is True)}/{len(cands)}"
        zs = [c["path_min_z_mm"] for c in cands if c.get("path_min_z_mm") is not None]
        out["path_min_z_mm"] = round(min(zs), 2) if zs else None

    # execution_trace — 실제로 나간 명령
    traces = [t for r in rows for t in r.get("execution_trace", [])]
    out["trace_steps"] = len(traces)
    clamped = sum(1 for t in traces if t.get("clamped_targets"))
    out["clamped_steps"] = clamped
    if traces:
        gaps = []
        for r in rows:
            tr = r.get("execution_trace") or []
            for a, b in zip(tr, tr[1:]):
                if a.get("sent_elapsed_ms") is not None and b.get("sent_elapsed_ms") is not None:
                    gaps.append(b["sent_elapsed_ms"] - a["sent_elapsed_ms"])
        out["sent_gap_ms"] = {"중앙": round(st.median(gaps), 1)} if gaps else None

    # 최종 추종 오차 — 간 결과
    fe = [r["final_error_ticks"] for r in rows if r.get("final_error_ticks")]
    if fe:
        arm = [max(abs(int(d.get(i, 0))) for i in ARM_IDS) for d in fe]
        grip = [abs(int(d.get(GRIPPER_ID, 0))) for d in fe if GRIPPER_ID in d]
        out["final_error_arm_ticks"] = {"중앙": int(st.median(arm)), "최대": max(arm)}
        out["final_error_gripper_ticks"] = {"중앙": int(st.median(grip)), "최대": max(grip)} if grip else None
    else:
        out["missing"].append(f"final_error_ticks 0/{n}")

    latch = [r["gripper_latch"] for r in rows if r.get("gripper_latch")]
    out["gripper_latch_cycles"] = f"{len(latch)}/{n}"
    return out


def judge(s: dict) -> tuple[str, list[str]]:
    """PASS / FAIL / INCOMPLETE with reasons. 판정과 사유."""
    if not s.get("cycles"):
        return "INCOMPLETE", ["사이클 0 — 판정 불가"]
    bad, unknown = [], []
    ex, exe = s.get("executable_waypoints"), s.get("executed_waypoints")
    if ex is None or exe is None:
        unknown.append("실행 웨이포인트 필드 없음")
    elif ex > 0 and exe / ex < GATE["executed_ratio_min"]:
        bad.append(f"실행률 {exe}/{ex}")
    elif ex == 0:
        bad.append("실행 가능 웨이포인트 0 — 전부 거부됨")
    sent, att = s.get("motor_commands_sent"), s.get("motor_commands_attempted")
    if sent is not None and att is not None and sent != att:
        bad.append(f"명령 전송 {sent}/{att}")
    if s.get("ik_ok"):
        a, b = s["ik_ok"].split("/")
        if int(a) < int(b):
            bad.append(f"IK {s['ik_ok']}")
    else:
        unknown.append("ik_ok 없음")
    if s.get("clamped_steps") is None:
        unknown.append("clamped 없음")
    elif s["clamped_steps"] > GATE["clamped_max"]:
        bad.append(f"관절한계 클램프 {s['clamped_steps']} 스텝")
    d = s.get("drift_ticks_max")
    if d is None:
        unknown.append("drift 없음")
    elif d > GATE["drift_abs_max"]:
        bad.append(f"추론 중 드리프트 {d} 틱")
    if s.get("observation_interval") and s["observation_interval"]["벗어남"].split("/")[0] != "0":
        bad.append(f"관측 간격 벗어남 {s['observation_interval']['벗어남']}")
    if bad:
        return "FAIL", bad
    if unknown:
        return "INCOMPLETE", unknown
    return "PASS", []


def selftest() -> int:
    """Self-check including deliberately broken input.
    자체검증 — 고의로 망가뜨린 입력을 잡는 행 포함."""
    ok = tot = 0
    def row(name, cond, got=""):
        nonlocal ok, tot
        tot += 1; ok += bool(cond)
        print(f"  {'OK ' if cond else 'NG '} {name}   {got}")

    def mk(**kw):
        base = {"cycle": 0, "attempt": 0, "phase": "completed", "status": "PASS",
                "motor_commands_sent": 4, "motor_commands_attempted": 4,
                "checkpoint_waypoints": 4, "executable_waypoints": 4, "executed_waypoints": 4,
                "latency_ms": 4000.0, "action_interval_ms": 100,
                "observation_interval_ms": 100.0, "observation_interval_target_ms": 100,
                "observation_interval_tolerance_ms": 15,
                "observation_joint_margin_ticks": [1000, 800, 500, 400, 1700],
                "post_inference_drift_ticks": [0, 0, 0, 0, 0],
                "final_error_ticks": {"1": 1, "2": 5, "3": 2, "4": 3, "5": 1, "6": 10},
                "gripper_latch": {"policy_tick": 750, "target_tick": 800},
                "candidates": [{"action_index": i, "ik_ok": True, "ik_residual_mm": 0.001,
                                "approach_error_deg": 0.2, "workspace_ok": True,
                                "limits_ok": True, "safe": True, "path_min_z_mm": 60.0}
                               for i in range(1, 5)],
                "execution_trace": [{"action_index": i, "sent_elapsed_ms": 73.0 + 100 * i,
                                     "clamped_targets": {}} for i in range(1, 5)]}
        base.update(kw); return base

    with tempfile.TemporaryDirectory() as td:
        t = Path(td)
        def write(name, rows):
            p = t / name; p.write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8"); return p

        v, why = judge(summarize(load(write("a.jsonl", [mk(), mk(cycle=1)]))))
        row("1 정상 런 -> PASS", v == "PASS", f"{v} {why}")

        s = summarize(load(write("b.jsonl", [mk(executed_waypoints=2)])))
        v, why = judge(s)
        row("2 실행률 미달 -> FAIL (판별행)", v == "FAIL" and "실행률" in why[0], why[0] if why else "")

        s = summarize(load(write("c.jsonl", [mk(motor_commands_sent=0, executed_waypoints=0)])))
        v, why = judge(s)
        row("3 명령 0 인데 executable 4 -> FAIL (판별행)", v == "FAIL", str(why))

        r = mk(); r["candidates"][2]["ik_ok"] = False
        v, why = judge(summarize(load(write("d.jsonl", [r]))))
        row("4 IK 실패 1건 -> FAIL (판별행)", v == "FAIL" and "IK" in " ".join(why), " ".join(why))

        r = mk(); r["execution_trace"][1]["clamped_targets"] = {"3": 2960}
        v, why = judge(summarize(load(write("e.jsonl", [r]))))
        row("5 관절한계 클램프 -> FAIL (판별행)", v == "FAIL" and "클램프" in " ".join(why), " ".join(why))

        v, why = judge(summarize(load(write("f.jsonl", [mk(post_inference_drift_ticks=[0, 0, 40, 0, 0])]))))
        row("6 추론 중 드리프트 40틱 -> FAIL (판별행)", v == "FAIL" and "드리프트" in " ".join(why), " ".join(why))

        r = mk(); del r["post_inference_drift_ticks"]
        s = summarize(load(write("g.jsonl", [r]))); v, why = judge(s)
        row("7 필드 없음 -> INCOMPLETE (통과 아님)", v == "INCOMPLETE", f"{v} · 결측 {s['missing']}")

        s = summarize(load(write("h.jsonl", [])))
        v, why = judge(s)
        row("8 빈 로그 -> INCOMPLETE (통과 아님)", v == "INCOMPLETE" and s["cycles"] == 0, v)

        s = summarize(load(write("i.jsonl", [mk(), mk(cycle=1)])))
        row("9 모수 보고", s["candidates"] == 8 and s["trace_steps"] == 8 and s["cycles"] == 2,
            f"사이클 {s['cycles']} · 웨이포인트 {s['candidates']} · 트레이스 {s['trace_steps']}")

        s = summarize(load(write("j.jsonl", [mk(latency_ms=4400.0)])))
        row("10 지연/주기 비 계산 (정답 아는 행)", s.get("latency_over_interval") == 44.0,
            f"{s.get('latency_over_interval')} 배")

        s = summarize(load(write("k.jsonl", [mk(observation_interval_ms=140.0)])))
        v, why = judge(s)
        row("11 관측 간격 허용 밖 -> FAIL (판별행)", v == "FAIL" and "관측" in " ".join(why),
            s["observation_interval"]["벗어남"])

    print(f"\n자체검증 {ok}/{tot}")
    return 0 if ok == tot else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--run", type=Path, nargs="*", help="runs/<name> 또는 cycles.jsonl")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--selftest-only", action="store_true")
    a = ap.parse_args()

    print("계측기 자체검증 먼저 —")
    if selftest():
        print("자체검증 실패 — 수치를 내지 않는다"); return 1
    if a.selftest_only or not a.run:
        return 0

    results, tally = [], {"PASS": 0, "FAIL": 0, "INCOMPLETE": 0}
    for p in a.run:
        p = p if p.name == "cycles.jsonl" else p / "cycles.jsonl"
        if not p.exists():
            print(f"\n[미판정 ] {p.parent.name:44} cycles.jsonl 없음")
            tally["INCOMPLETE"] += 1
            results.append({"run": p.parent.name, "verdict": "INCOMPLETE", "why": ["로그 없음"]})
            continue
        s = summarize(load(p))
        v, why = judge(s)
        tally[v] += 1
        results.append({"run": p.parent.name, "verdict": v, "why": why, **s})
        tag = {"PASS": " 통과 ", "FAIL": "불합격 ", "INCOMPLETE": "미판정 "}[v]
        print(f"\n[{tag}] {p.parent.name}")
        print(f"  사이클 {s['cycles']} · phase {s.get('phase')} · status {s.get('status')}")
        print(f"  웨이포인트  정책 {s.get('checkpoint_waypoints')} / 실행가능 {s.get('executable_waypoints')} / 실행 {s.get('executed_waypoints')}")
        print(f"  명령        전송 {s.get('motor_commands_sent')} / 시도 {s.get('motor_commands_attempted')}")
        print(f"  IK          {s.get('ik_ok')} · 잔차 {s.get('ik_residual_mm')} · 접근각오차 {s.get('approach_error_deg')}")
        print(f"  안전        workspace {s.get('workspace_ok')} · limits {s.get('limits_ok')} · safe {s.get('safe')} · 클램프 {s.get('clamped_steps')}/{s.get('trace_steps')}")
        print(f"  지연        중앙 {s.get('latency_ms')} ms · 액션주기 {s.get('action_interval_ms')} ms · 비 {s.get('latency_over_interval')} 배")
        print(f"  관측 간격   {s.get('observation_interval')}")
        print(f"  드리프트    최대 {s.get('drift_ticks_max')} 틱 · 관절여유 최소 {s.get('joint_margin_min')} 틱")
        print(f"  최종 오차   팔 {s.get('final_error_arm_ticks')} · 그리퍼 {s.get('final_error_gripper_ticks')}")
        print(f"  경로 최저 z {s.get('path_min_z_mm')} mm · latch {s.get('gripper_latch_cycles')}")
        if s.get("missing"):
            print(f"  ** 결측 필드: {s['missing']}")
        if why:
            print(f"  사유: {' · '.join(why)}")

    tot = sum(tally.values())
    print(f"\n통과 {tally['PASS']} · 불합격 {tally['FAIL']} · 미판정 {tally['INCOMPLETE']} / 전체 {tot}")
    print("** 미판정은 통과가 아니다. 명령을 보낸 것과 팔이 간 것은 다른 사건이다 **")
    if a.out:
        a.out.parent.mkdir(parents=True, exist_ok=True)
        a.out.write_text(json.dumps({"gate": GATE, "tally": tally, "total": tot, "runs": results},
                                    ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"→ {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
