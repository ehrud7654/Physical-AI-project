#!/usr/bin/env python3
"""녹화 폴더 검사.

사용: python check_recording.py .\\data\\rec_20260825_143000
"""
import csv
import os
import sys
from collections import Counter

d = sys.argv[1] if len(sys.argv) > 1 else "."
csv_path = os.path.join(d, "poses.csv")
frames_dir = os.path.join(d, "frames")

if not os.path.exists(csv_path):
    sys.exit(f"poses.csv 없음: {csv_path}")

rows = []
with open(csv_path, newline="", encoding="utf-8") as fh:
    for r in csv.DictReader(fh):
        rows.append(r)

if not rows:
    sys.exit("빈 파일")

meta = os.path.join(d, "meta.txt")
if os.path.exists(meta):
    print("--- meta.txt ---")
    print(open(meta, encoding="utf-8").read().strip())
    print()

ts = [int(r["timestamp_ns"]) for r in rows]
dur = (ts[-1] - ts[0]) / 1e9
states = Counter(r["tracking"] for r in rows)
with_img = sum(1 for r in rows if r["image"])
dropped = len(rows) - with_img

print(f"pose 행       : {len(rows)}")
print(f"길이          : {dur:.1f} s")
print(f"실제 pose rate: {len(rows)/dur:.1f} Hz" if dur > 0 else "")
print(f"TRACKING      : {states.get('TRACKING',0)/len(rows)*100:.1f}%  {dict(states)}")
print(f"이미지 저장   : {with_img}  ({with_img/dur:.1f} Hz)" if dur > 0 else "")
print(f"이미지 드롭   : {dropped}  ({dropped/len(rows)*100:.1f}%)")

if os.path.isdir(frames_dir):
    files = [f for f in os.listdir(frames_dir) if f.endswith(".jpg")]
    total = sum(os.path.getsize(os.path.join(frames_dir, f)) for f in files)
    print(f"JPEG 파일     : {len(files)}개, {total/1e6:.1f} MB "
          f"(평균 {total/len(files)/1024:.0f} KB)" if files else "JPEG 없음")
    missing = [r["image"] for r in rows if r["image"] and
               not os.path.exists(os.path.join(frames_dir, r["image"]))]
    if missing:
        print(f"⚠ CSV에는 있는데 파일이 없는 이미지: {len(missing)}개 (예: {missing[:3]})")

tracked = [r for r in rows if r["tracking"] == "TRACKING"]
if len(tracked) > 1:
    import math
    xs = [float(r["x"]) for r in tracked]
    ys = [float(r["y"]) for r in tracked]
    zs = [float(r["z"]) for r in tracked]
    path = sum(math.dist((xs[i-1], ys[i-1], zs[i-1]), (xs[i], ys[i], zs[i]))
               for i in range(1, len(tracked)))
    print(f"\n이동 총거리   : {path:.3f} m")
    for n, a in (("x", xs), ("y", ys), ("z", zs)):
        print(f"  {n}: {min(a):+.3f} ~ {max(a):+.3f}  (범위 {max(a)-min(a):.3f} m)")

gaps = [(ts[i]-ts[i-1])/1e6 for i in range(1, len(ts))]
if gaps:
    big = [g for g in gaps if g > 100]
    print(f"\n프레임 간격   : 중앙값 {sorted(gaps)[len(gaps)//2]:.1f} ms, 최대 {max(gaps):.1f} ms")
    if big:
        print(f"⚠ 100ms 넘는 끊김 {len(big)}회")
