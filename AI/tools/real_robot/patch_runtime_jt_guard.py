"""Port 황도경's gripper phase guard into the runtime_jt fork.
황도경의 그리퍼 위상 guard 를 runtime_jt 포크에 이식한다.

Problem / 문제:
  The policy closes the gripper gradually during approach. By the time the TCP
  reaches the object the opening is already narrower than the object, so the
  gripper knocks it over instead of grasping it. Measured 2026-09-22.
  정책이 접근 중 그리퍼를 서서히 닫는다. 도착 시점에 이미 물체보다 좁아서
  잡지 못하고 넘어뜨린다.

Fix / 해법:
  Hold the commanded gap at the pre-grasp opening regardless of what the policy
  asks, until the policy's raw gap command stays below the close threshold for
  N consecutive chunks. Then latch and close in one step.
  정책이 뭐라 하든 사전파지 개구로 유지하고, 정책의 raw 명령이 임계 아래로
  N청크 연속 유지될 때만 래치를 걸어 한 번에 닫는다.

Source / 출처:
  guard module and config are 황도경's pv_E_guard_v1_handoff_20260922, copied
  verbatim. Only the wiring into runtime_jt is new.
  guard 모듈과 설정은 황도경 인계 묶음 원문 그대로다. 배선만 새로 쓴다.

Idempotent. Touches runtime_jt only, never 현석's runtime.
멱등. runtime_jt 만 건드린다.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

MARK_CTRL = "[JT-PATCH gripper-guard]"
MARK_CLI = "[JT-PATCH gripper-guard-args]"
CTRL_ANCHOR = "with common.ST3215Bus(args.motor_port) as bus:"
CONFIG_NAME = "gripper_guard_onsite260921_pick.json"

GUARD_SOURCE = '''"""Dataset-configured gripper phase guard; standard-library only.

Copied verbatim from 황도경 pv_E_guard_v1_handoff_20260922 (2026-09-22).
황도경 인계 묶음 원문 그대로. 수정 금지 - 수정이 필요하면 상대 트랙과 합의한다.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
import math
from pathlib import Path


SCHEMA = "umi_gripper_phase_guard/0.1.0"


@dataclass(frozen=True)
class Config:
    command_min: float
    command_max: float
    observation_min: float
    observation_max: float
    open_gap: float
    close_threshold: float
    confirm_cycles: int
    close_offset: float
    reopen_threshold: float
    task_mode: str


@dataclass(frozen=True)
class State:
    latched: bool = False
    close_votes: int = 0
    held_gap: float | None = None


def load(path: Path) -> Config:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    required = {
        "schema", "command_gap_range_m", "observation_gap_range_m",
        "pregrasp_open_m", "close_threshold_m", "close_confirmation_cycles",
        "close_offset_m", "reopen_threshold_m", "task_mode",
    }
    missing = sorted(required - set(raw))
    if missing or raw.get("schema") != SCHEMA:
        raise ValueError(f"invalid gripper guard config; missing={missing}, schema={raw.get('schema')!r}")
    command_min, command_max = map(float, raw["command_gap_range_m"])
    observation_min, observation_max = map(float, raw["observation_gap_range_m"])
    open_gap = float(raw["pregrasp_open_m"])
    close_threshold = float(raw["close_threshold_m"])
    confirm_cycles = int(raw["close_confirmation_cycles"])
    close_offset = float(raw["close_offset_m"])
    reopen_threshold = float(raw["reopen_threshold_m"])
    task_mode = str(raw["task_mode"])
    values = (command_min, command_max, observation_min, observation_max,
              open_gap, close_threshold, close_offset, reopen_threshold)
    if not all(math.isfinite(value) for value in values):
        raise ValueError("gripper guard config contains NaN/Inf")
    if not command_min < command_max or not observation_min < observation_max:
        raise ValueError("gripper guard ranges are invalid")
    if observation_min > command_min or observation_max < command_max:
        raise ValueError("observation range must contain command range")
    if not command_min <= close_threshold < open_gap <= command_max:
        raise ValueError("require command_min <= close_threshold < open_gap <= command_max")
    if not close_threshold < reopen_threshold <= command_max:
        raise ValueError("require close_threshold < reopen_threshold <= command_max")
    if confirm_cycles < 1 or not 0 <= close_offset < close_threshold - command_min:
        raise ValueError("invalid close confirmation or offset")
    if task_mode not in {"pick_hold", "policy_reopen"}:
        raise ValueError("task_mode must be pick_hold or policy_reopen")
    return Config(command_min, command_max, observation_min, observation_max,
                  open_gap, close_threshold, confirm_cycles, close_offset,
                  reopen_threshold, task_mode)


def apply(predicted, observed, state: State, config: Config):
    predicted = tuple(float(value) for value in predicted)
    observed = float(observed)
    if not predicted or not math.isfinite(observed) or not all(map(math.isfinite, predicted)):
        raise ValueError("empty/non-finite gripper input")
    if not config.observation_min <= observed <= config.observation_max:
        raise ValueError(f"observed gap {observed:.6f} is outside the configured range")
    if any(not config.command_min <= value <= config.command_max for value in predicted):
        raise ValueError("predicted gap is outside the configured command range")

    close_candidate = all(value <= config.close_threshold for value in predicted)
    reopen_candidate = all(value >= config.reopen_threshold for value in predicted)
    latched, votes, held = state.latched, state.close_votes, state.held_gap
    event = None
    if latched and config.task_mode == "policy_reopen" and reopen_candidate:
        latched, votes, held, event = False, 0, None, "reopened"
    if not latched:
        votes = votes + 1 if close_candidate else 0
        if votes >= config.confirm_cycles:
            latched, held, event = True, config.open_gap, "close_confirmed"

    if not latched:
        commanded = tuple(max(value, config.open_gap) for value in predicted)
        phase = "pregrasp_hold"
    elif config.task_mode == "pick_hold":
        held = config.open_gap if held is None else held
        commanded_list = []
        for value in predicted:
            held = min(held, max(config.command_min, value - config.close_offset))
            commanded_list.append(held)
        commanded = tuple(commanded_list)
        phase = "grasp_hold"
    else:
        commanded = tuple(max(config.command_min, value - config.close_offset)
                          for value in predicted)
        held = commanded[-1]
        phase = "grasp_policy"
    next_state = State(latched, votes, held)
    report = {
        "schema": SCHEMA, "phase": phase, "event": event,
        "task_mode": config.task_mode, "observed_gap_m": observed,
        "raw_predicted_gap_m": list(predicted), "commanded_gap_m": list(commanded),
        "close_candidate": close_candidate, "reopen_candidate": reopen_candidate,
        "close_votes": votes, "close_confirmation_cycles": config.confirm_cycles,
        "latched": latched,
    }
    return commanded, next_state, report
'''

GUARD_CONFIG = {
    "schema": "umi_gripper_phase_guard/0.1.0",
    "status": "diagnostic_not_hardware_approved",
    "source_dataset": "onsite260921_C_ours_va / pv_E",
    "command_gap_range_m": [0.0, 0.09],
    "observation_gap_range_m": [0.0, 0.095],
    "pregrasp_open_m": 0.078971848,
    "close_threshold_m": 0.05,
    "close_confirmation_cycles": 2,
    "close_offset_m": 0.0013778322,
    "reopen_threshold_m": 0.06,
    "task_mode": "pick_hold",
    "provenance": {
        "author": "황도경 pv_E_guard_v1_handoff_20260922 원문",
        "pregrasp_open_m": "9/21 gripper_report overall maximum and pv_E action upper support",
        "close_threshold_m": "diagnostic boundary above the 45.8 mm demonstrated grasp median",
        "close_offset_m": "25 ticks * 0.09 m / (1763 - 130)",
    },
}

CTRL_BLOCK = '''
        # {mark} 접근 중 폐쇄를 막고 래치 후 1회에 닫는다. 기본 off
        if getattr(args, "gripper_guard", None) and args.enable_gripper:
            from pathlib import Path as _GPath
            from . import gripper_guard as _gg
            _gcfg = getattr(args, "_guard_cfg", None)
            if _gcfg is None:
                _gcfg = _gg.load(_GPath(args.gripper_guard))
                args._guard_cfg = _gcfg
            _gstate = getattr(args, "_guard_state", None) or _gg.State()
            _obs_tick = int(common.retry_eintr(
                lambda: bus.read_diagnostics(common.GRIPPER_ID))["tick"])
            _obs = min(max(float(common.gripper_tick_to_width_m(_obs_tick)),
                           _gcfg.observation_min), _gcfg.observation_max)
            _pred = tuple(
                min(max(float(common.gripper_tick_to_width_m(int(_c["gripper_tick"]))),
                        _gcfg.command_min), _gcfg.command_max)
                for _c in candidates
            )
            _cmd, _gstate, _grep = _gg.apply(_pred, _obs, _gstate, _gcfg)
            args._guard_state = _gstate
            for _c, _wm in zip(candidates, _cmd):
                _c["gripper_tick"] = int(common.width_to_tick(_wm))
            _grep["observed_tick"] = _obs_tick
            _grep["commanded_ticks"] = [int(_c["gripper_tick"]) for _c in candidates]
            report["gripper_guard"] = _grep
'''.format(mark=MARK_CTRL)

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--gripper-guard", default=None,\n'
    '{indent}    help="path to a umi_gripper_phase_guard/0.1.0 config; omit to disable")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous. Refuse rather than write a guessed patch."""


def patch_controller(text: str) -> tuple[str, str]:
    if MARK_CTRL in text:
        return text, "= 이미 적용됨"
    lines = text.splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if CTRL_ANCHOR in line]
    if len(hits) != 1:
        raise PatchError(f"controller 앵커 발견 {len(hits)} / 기대 1")
    lines.insert(hits[0] + 1, CTRL_BLOCK.lstrip("\n"))
    return "".join(lines), "+ guard 호출 블록 삽입"


def patch_cli(text: str) -> tuple[str, str]:
    if MARK_CLI in text:
        return text, "= 이미 적용됨"
    pattern = re.compile(r'^(\s*)([A-Za-z_][\w.]*)\.add_argument\(\s*"--enable-gripper"')
    lines = text.splitlines(keepends=True)
    hits = [(i, m) for i, line in enumerate(lines) if (m := pattern.match(line))]
    if len(hits) != 1:
        raise PatchError(f'cli 앵커 "--enable-gripper" 발견 {len(hits)} / 기대 1')
    index, match = hits[0]
    lines.insert(index, CLI_TEMPLATE.format(indent=match.group(1), recv=match.group(2), mark=MARK_CLI))
    return "".join(lines), "+ --gripper-guard 추가"


def apply_to(root: Path) -> int:
    guard_path = root / "gripper_guard.py"
    config_path = root / CONFIG_NAME
    changed = 0
    if guard_path.exists() and guard_path.read_text(encoding="utf-8") == GUARD_SOURCE:
        print("  = gripper_guard.py 동일")
    else:
        guard_path.write_text(GUARD_SOURCE, encoding="utf-8")
        changed += 1
        print("  + gripper_guard.py 설치 (황도경 원문)")
    body = json.dumps(GUARD_CONFIG, ensure_ascii=False, indent=2) + "\n"
    if config_path.exists() and config_path.read_text(encoding="utf-8") == body:
        print(f"  = {CONFIG_NAME} 동일")
    else:
        config_path.write_text(body, encoding="utf-8")
        changed += 1
        print(f"  + {CONFIG_NAME} 설치")
    for name, fn in (("robot_controller.py", patch_controller), ("cli.py", patch_cli)):
        path = root / name
        if not path.is_file():
            raise PatchError(f"대상 없음 {path}")
        before = path.read_text(encoding="utf-8")
        after, note = fn(before)
        if after != before:
            path.write_text(after, encoding="utf-8")
            changed += 1
        print(f"  {note}  ({name})")
    print(f"변경 {changed} / 항목 4")
    print(f"설정 경로: {config_path}")
    return 0


def _selftest() -> int:
    import tempfile, types
    checks: list[tuple[str, bool]] = []
    ns: dict = {}
    exec(compile(GUARD_SOURCE, "<guard>", "exec"), ns)
    gg = types.SimpleNamespace(**ns)
    with tempfile.TemporaryDirectory() as tmp:
        cfg_path = Path(tmp) / CONFIG_NAME
        cfg_path.write_text(json.dumps(GUARD_CONFIG, ensure_ascii=False), encoding="utf-8")
        cfg = gg.load(cfg_path)
    checks.append(("설정 로드", abs(cfg.open_gap - 0.078971848) < 1e-9))

    st = gg.State()
    cmd, st, rep = gg.apply((0.0651, 0.0620, 0.0590, 0.0560), 0.0704, st, cfg)
    checks.append(("접근 중 벌려서 유지", min(cmd) >= cfg.open_gap and rep["phase"] == "pregrasp_hold"))
    cmd, st, rep = gg.apply((0.045, 0.044, 0.043, 0.042), 0.0700, st, cfg)
    checks.append(("1차 투표만, 아직 유지", rep["phase"] == "pregrasp_hold" and rep["close_votes"] == 1))
    cmd, st, rep = gg.apply((0.042, 0.041, 0.040, 0.040), 0.0700, st, cfg)
    checks.append(("2차에 래치", rep["event"] == "close_confirmed" and rep["phase"] == "grasp_hold"))
    checks.append(("한 번에 닫음", max(cmd) < 0.045))
    checks.append(("단조 폐쇄", all(b <= a + 1e-12 for a, b in zip(cmd, cmd[1:]))))
    cmd2, st, rep = gg.apply((0.070, 0.070, 0.070, 0.070), 0.0400, st, cfg)
    checks.append(("pick_hold: 재개방 없음", max(cmd2) <= max(cmd) + 1e-12))

    for name, pred, obs in (("판별: NaN", (float("nan"),), 0.07),
                            ("판별: 관측 범위 밖", (0.05,), 0.2),
                            ("판별: 명령 범위 밖", (0.5,), 0.07)):
        try:
            gg.apply(pred, obs, gg.State(), cfg); checks.append((name, False))
        except ValueError:
            checks.append((name, True))

    ctrl = ('def execute_chunk(candidates, common, args, report):\n'
            '    trace = []\n'
            '    with common.ST3215Bus(args.motor_port) as bus:\n'
            '        for candidate in candidates:\n'
            '            pass\n')
    cli = ('def build_parser():\n'
           '    parser = argparse.ArgumentParser()\n'
           '    parser.add_argument("--enable-gripper", action="store_true")\n')
    a, _ = patch_controller(ctrl)
    checks.append(("controller 삽입", MARK_CTRL in a and _compiles(a)))
    checks.append(("with 문 안쪽에 삽입", a.index(MARK_CTRL) > a.index(CTRL_ANCHOR)))
    b, note = patch_controller(a)
    checks.append(("controller 멱등", b == a and note.startswith("=")))
    c, _ = patch_cli(cli)
    checks.append(("cli 삽입", MARK_CLI in c and _compiles(c)))
    d, note = patch_cli(c)
    checks.append(("cli 멱등", d == c and note.startswith("=")))
    for name, fn, bad in (("판별: controller 앵커 없음", patch_controller, "def f():\n    pass\n"),
                          ("판별: cli 앵커 없음", patch_cli, "parser = 1\n"),
                          ("판별: 앵커 중복", patch_controller, ctrl + ctrl)):
        try:
            fn(bad); checks.append((name, False))
        except PatchError:
            checks.append((name, True))

    ok = sum(1 for _, p in checks if p)
    for name, passed in checks:
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
    print(f"자체검사 통과 {ok} / 전체 {len(checks)}")
    return 0 if ok == len(checks) else 1


def _compiles(source: str) -> bool:
    try:
        compile(source, "<patched>", "exec")
        return True
    except SyntaxError:
        return False


def main() -> int:
    ap = argparse.ArgumentParser(description="runtime_jt 그리퍼 guard 이식")
    ap.add_argument("root", nargs="?", help="runtime_jt/umi_checkpoint_executor 경로")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest or not args.root:
        return _selftest()
    try:
        return apply_to(Path(args.root))
    except PatchError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
