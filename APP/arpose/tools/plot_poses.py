#!/usr/bin/env python3
"""v4 poses.csv 궤적/흔들림 시각화 + AF 모드 비교.

사용:
  python plot_poses.py <rec폴더>              # 한 회차 궤적 + 프레임간 이동량
  python plot_poses.py --compare <상위폴더>   # 하위 rec_* 전부를 focus_mode별로 비교

"""
import os
import sys
import glob
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

FIXED_C, AUTO_C = "#1f77b4", "#d62728"


def load(d):
    """rec 폴더 -> (focus_mode, t[s], pos[N,3], quat[N,4], jump_mm)"""
    csv = os.path.join(d, "poses.csv")
    rows = np.genfromtxt(csv, delimiter=",", names=True, dtype=None, encoding="utf-8")
    ok = rows["tracking"] == "TRACKING"
    rows = rows[ok]
    t = (rows["timestamp_ns"] - rows["timestamp_ns"][0]) / 1e9
    pos = np.column_stack([rows["x"], rows["y"], rows["z"]])
    quat = np.column_stack([rows["qx"], rows["qy"], rows["qz"], rows["qw"]])
    af = "UNKNOWN"
    meta = os.path.join(d, "meta.txt")
    if os.path.exists(meta):
        for line in open(meta, encoding="utf-8"):
            if line.startswith("focus_mode="):
                af = line.strip().split("=", 1)[1]
    return af, t, pos, quat, rows["jump_mm"]


def metrics(t, pos, quat):
    """프레임간 이동(mm), 회전(deg), 2차차분=고주파 흔들림(mm)"""
    dp = np.diff(pos, axis=0)
    d_mm = np.linalg.norm(dp, axis=1) * 1000
    dot = np.abs(np.sum(quat[1:] * quat[:-1], axis=1)).clip(-1, 1)
    r_deg = np.degrees(2 * np.arccos(dot))
    jerk_mm = np.linalg.norm(np.diff(dp, axis=0), axis=1) * 1000
    return d_mm, r_deg, jerk_mm


def plot_one(d, out):
    af, t, pos, quat, jump = load(d)
    d_mm, r_deg, jerk = metrics(t, pos, quat)
    fig, ax = plt.subplots(1, 3, figsize=(16, 4.6))

    ax[0].plot(pos[:, 0], pos[:, 2], lw=1, color="#444")
    ax[0].scatter(*pos[0, [0, 2]], c="g", zorder=5, label="start")
    ax[0].scatter(*pos[-1, [0, 2]], c="r", zorder=5, label="end")
    jidx = np.nonzero(jump > 0)[0]
    if len(jidx):
        ax[0].scatter(pos[jidx, 0], pos[jidx, 2], c="orange", s=90, marker="x",
                      zorder=6, label=f"jump x{len(jidx)}")
    ax[0].set(xlabel="x (m)", ylabel="z (m)", title="top-down trajectory")
    ax[0].axis("equal"); ax[0].grid(alpha=.3); ax[0].legend(fontsize=8)

    for n, c in zip("xyz", ("#1f77b4", "#2ca02c", "#d62728")):
        ax[1].plot(t, pos[:, "xyz".index(n)], lw=1, label=n, color=c)
    ax[1].set(xlabel="time (s)", ylabel="position (m)", title="position")
    ax[1].grid(alpha=.3); ax[1].legend(fontsize=8)

    ax[2].plot(t[1:], d_mm, lw=.8, color="#444", label="per-frame move")
    ax[2].axhline(15, color="orange", ls="--", lw=1, label="15 mm")
    if len(jidx):
        ax[2].scatter(t[jidx], d_mm[jidx - 1], c="orange", s=90, marker="x", zorder=6)
    ax[2].set(xlabel="time (s)", ylabel="mm", title="per-frame displacement")
    ax[2].grid(alpha=.3); ax[2].legend(fontsize=8)

    fig.suptitle(f"{os.path.basename(d)}   AF={af}   {len(t)} frames, {t[-1]:.1f}s",
                 fontsize=11)
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    print(f"-> {out}")
    print(f"   AF={af}  frames={len(t)}  dur={t[-1]:.1f}s  jumps={len(jidx)}")
    print(f"   move mm : med {np.median(d_mm):.2f}  p99 {np.percentile(d_mm,99):.2f}"
          f"  max {d_mm.max():.2f}")


def plot_compare(root, out, skip_first=True):
    by = {}
    for d in sorted(glob.glob(os.path.join(root, "rec_*"))):
        if not os.path.exists(os.path.join(d, "poses.csv")):
            continue
        af, t, pos, quat, jump = load(d)
        by.setdefault(af, []).append((os.path.basename(d),) + metrics(t, pos, quat))
    if skip_first:
        for af in by:
            dropped = by[af].pop(0)[0]
            print(f"[{af}] 워밍업 회차 제외: {dropped}")

    fig, ax = plt.subplots(1, 3, figsize=(16, 4.6))
    for af, color in (("FIXED", FIXED_C), ("AUTO", AUTO_C)):
        if af not in by:
            continue
        d_all = np.concatenate([m[1] for m in by[af]])
        j_all = np.concatenate([m[3] for m in by[af]])
        n = len(d_all)
        lbl = f"{af} ({len(by[af])} takes, {n} fr)"

        ax[0].plot(np.sort(d_all), np.linspace(0, 100, n), color=color, lw=1.8, label=lbl)
        ax[1].plot(np.sort(j_all), np.linspace(0, 100, len(j_all)), color=color, lw=1.8,
                   label=lbl)
        print(f"[{af}] takes={len(by[af])} frames={n}  "
              f"move p99={np.percentile(d_all,99):.2f} max={d_all.max():.2f}mm  "
              f">15mm={np.sum(d_all>15)}회({np.sum(d_all>15)*1000/n:.2f}/1000fr)  "
              f"jitter p99={np.percentile(j_all,99):.2f}mm")

    for a, title, xl in ((ax[0], "per-frame displacement CDF", "mm"),
                         (ax[1], "high-freq jitter CDF (2nd diff)", "mm")):
        a.set(xlabel=xl, ylabel="percentile (%)", title=title, xscale="log")
        a.set_ylim(80, 100.3); a.set_xlim(left=0.8)
        a.grid(alpha=.3, which="both"); a.legend(fontsize=8)
    ax[0].axvline(15, color="orange", ls="--", lw=1)

    labels, vals, colors = [], [], []
    for af, color in (("FIXED", FIXED_C), ("AUTO", AUTO_C)):
        if af not in by:
            continue
        d_all = np.concatenate([m[1] for m in by[af]])
        labels.append(af); colors.append(color)
        vals.append(np.sum(d_all > 15) * 1000 / len(d_all))
    bars = ax[2].bar(labels, vals, color=colors, width=.55)
    ax[2].bar_label(bars, fmt="%.2f", fontsize=10)
    ax[2].set(ylabel="count per 1000 frames", title="frames exceeding 15 mm (lower=better)")
    ax[2].grid(alpha=.3, axis="y")

    fig.suptitle("AF FIXED vs AUTO — tracking stability (warm-up take excluded)", fontsize=12)
    fig.tight_layout()
    fig.savefig(out, dpi=120)
    print(f"-> {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a and a[0] == "--compare":
        root = a[1] if len(a) > 1 else "."
        plot_compare(root, os.path.join(root, "af_compare.png"))
    else:
        d = a[0] if a else "."
        plot_one(d, os.path.join(d, "trajectory.png"))
