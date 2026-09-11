#!/usr/bin/env python3
"""녹화 프레임에서 그리퍼 개폐폭 시계열을 뽑아 gripper.csv 를 만든다.

손가락 레일 연결부에 붙인 검은 정사각형 마커 두 개를 검출해 좌우 간격(px)을 잰다.
판정 근거는 docs/gripper_width_marker.md.

사용:
  python gripper_gap.py <rec폴더>            # <rec폴더>/gripper.csv 생성
  python gripper_gap.py <rec폴더> --plot     # gripper_gap.png 도 함께

출력 gripper.csv: frame_index,gap_m,status   (SPEC_umi_raw_bundle 3컬럼)
  status = D(양쪽 직접) / M(중심선 대칭 추정) / T(템플릿 보강) / X(실패)
  gap_m = 손가락 끝 폭(미터). 마커(16mm)로 프레임별 거리를 보정해 얻으므로 D 프레임에만 있다
  (M/T 는 마커 크기를 못 재 빈값). 캘리브 상수는 파일 상단 참조.

필요: numpy, opencv-python. --plot 은 matplotlib.
"""
import os
import sys
import glob
import itertools
import numpy as np
import cv2

# --- 개폐폭 캘리브레이션 (rec_20260907_154317, S23 그리퍼) ---
# 마커는 한 변 16mm. 이미지 속 마커 픽셀크기와의 비로 그 프레임의 거리를 보정한다
# (marker_gap_mm = gap_px * MARKER_MM / 마커픽셀폭). 거리가 변해도 값이 유지됨을 실측 확인.
MARKER_MM = 16.0
# 레일 마커 중심간 거리(mm, 거리보정 후) — 완전폐쇄/완전개방에서 관측한 그리퍼 고유값.
MARKER_GAP_CLOSED_MM = 38.0
MARKER_GAP_OPEN_MM = 81.0
# 그 두 극단의 손가락 끝 폭(mm): 완전폐쇄 0, 완전개방 90.
# ponytail: 그리퍼 기구를 바꾸면 개폐 스윕 1회 재촬영해 이 4개 값을 다시 잡을 것.
FINGERTIP_CLOSED_MM = 0.0
FINGERTIP_OPEN_MM = 90.0


def fingertip_m(marker_gap_mm):
    """거리보정된 마커 간격(mm) -> 손가락 끝 폭(m). 두 극단 앵커로 선형 매핑 + 클램프."""
    span = MARKER_GAP_OPEN_MM - MARKER_GAP_CLOSED_MM
    ft = (FINGERTIP_CLOSED_MM + (FINGERTIP_OPEN_MM - FINGERTIP_CLOSED_MM)
          * (marker_gap_mm - MARKER_GAP_CLOSED_MM) / span)
    return np.clip(ft, FINGERTIP_CLOSED_MM, FINGERTIP_OPEN_MM) / 1000.0


def imread_gray(path):
    # 한글 경로에서 cv2.imread 가 실패하므로 np.fromfile 로 읽는다.
    return cv2.imdecode(np.fromfile(path, np.uint8), cv2.IMREAD_GRAYSCALE)


def candidates(g):
    """한 프레임에서 마커 후보 덩어리 목록. ArUco 빈 속·끊긴 테두리까지 메운다."""
    h, w = g.shape
    blur = cv2.GaussianBlur(g, (5, 5), 0)
    th = cv2.adaptiveThreshold(blur, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                               cv2.THRESH_BINARY_INV, 41, 15)
    th = cv2.morphologyEx(th, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    cnts, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    raw = []
    for c in cnts:
        x, y, bw, bh = cv2.boundingRect(c)
        a = cv2.contourArea(cv2.convexHull(c))          # 헐 면적: 빈 속을 채움
        if a < 300 or a > 12000 or y > h * 0.6:
            continue
        if x < 30 or x + bw > w - 30:                    # 화면 좌우 가장자리 잡티 제외
            continue
        raw.append([x, y, bw, bh, a])
    # 같은 높이의 끊긴 조각(얇게 잡힌 테두리)을 가로로 병합
    merged = True
    while merged:
        merged = False
        for i in range(len(raw)):
            for j in range(i + 1, len(raw)):
                xi, yi, wi, hi, _ = raw[i]
                xj, yj, wj, hj, _ = raw[j]
                same_row = abs((yi + hi / 2) - (yj + hj / 2)) < 15
                close_x = (max(xi, xj) - min(xi + wi, xj + wj)) < 20
                like_h = abs(hi - hj) < 0.4 * max(hi, hj)   # 높이 비슷한 것만(띠와 병합 금지)
                if same_row and close_x and like_h:
                    x = min(xi, xj); y = min(yi, yj)
                    x2 = max(xi + wi, xj + wj); y2 = max(yi + hi, yj + hj)
                    raw[i] = [x, y, x2 - x, y2 - y, (x2 - x) * (y2 - y) * 0.9]
                    raw.pop(j); merged = True; break
            if merged:
                break
    out = []
    for x, y, bw, bh, a in raw:
        ar = bw / max(bh, 1)
        if a < 400 or not (0.9 < ar < 3.2) or a / (bw * bh) < 0.5:  # 가로로 눌린 사각형만
            continue
        out.append(dict(cx=x + bw / 2, cy=y + bh / 2, a=a, box=(x, y, bw, bh)))
    return out


def sym_pairs(cs, h):
    """대칭성 점수가 낮은(=좌우 닮은) 순으로 (score, left, right)."""
    out = []
    for p, q in itertools.combinations(cs, 2):
        l, r = (p, q) if p["cx"] < q["cx"] else (q, p)
        if r["cx"] - l["cx"] < 80:
            continue
        score = abs(l["a"] - r["a"]) / max(l["a"], r["a"]) + abs(l["cy"] - r["cy"]) / h * 4
        out.append((score, l, r))
    return sorted(out, key=lambda t: t[0])


def template_track(g, tpl, pos, W, H, win=70, thr=0.6):
    if tpl is None or pos is None:
        return None
    th_, tw_ = tpl.shape
    x0 = int(max(0, pos[0] - win - tw_ / 2)); x1 = int(min(W, pos[0] + win + tw_ / 2))
    y0 = int(max(0, pos[1] - win - th_ / 2)); y1 = int(min(H, pos[1] + win + th_ / 2))
    roi = g[y0:y1, x0:x1]
    if roi.shape[0] <= th_ or roi.shape[1] <= tw_:
        return None
    res = cv2.matchTemplate(roi, tpl, cv2.TM_CCOEFF_NORMED)
    _, mx, _, loc = cv2.minMaxLoc(res)
    return (x0 + loc[0] + tw_ / 2, y0 + loc[1] + th_ / 2) if mx >= thr else None


def track(files):
    """(status[], gap_px[]) 반환. gap 은 X 프레임에서 NaN."""
    n = len(files)
    all_cs = [candidates(imread_gray(f)) for f in files]
    H, W = imread_gray(files[0]).shape

    # 1패스: 아주 확실한 대칭 쌍으로 그리퍼 중심선을 추정한다(폰 고정이라 x 불변).
    mids, cys, areas, gaps1 = [], [], [], []
    for cs in all_cs:
        for s, l, r in sym_pairs(cs, H)[:1]:
            if s < 0.25:
                mids.append((l["cx"] + r["cx"]) / 2); cys.append((l["cy"] + r["cy"]) / 2)
                areas.append((l["a"] + r["a"]) / 2); gaps1.append(r["cx"] - l["cx"])
    if len(mids) < 5:
        raise SystemExit("마커 쌍을 충분히 못 찾음 - 마커/초점/마운트를 확인할 것")
    cx0 = float(np.median(mids)); cy0 = float(np.median(cys))
    markerA = float(np.median(areas))
    b0, a0 = np.polyfit(gaps1, mids, 1)     # 중심선이 개폐에 따라 미세 이동 → gap 의존 모델
    MID_TOL, CY_TOL = 22, 45

    def mid_of(gp):
        return a0 + b0 * gp

    def valid_pair(l, r):
        return (abs((l[0] + r[0]) / 2 - mid_of(r[0] - l[0])) < MID_TOL
                and abs(l[1] - cy0) < CY_TOL and abs(r[1] - cy0) < CY_TOL
                and r[0] - l[0] > 60)

    def mirror_from(cx, cy):
        sgn = 1 if cx > cx0 else -1
        denom = b0 + sgn * 0.5
        g = abs((cx - a0) / denom * sgn) if abs(denom) > 1e-6 else 2 * abs(cx - cx0)
        m = mid_of(g)
        return (m - g / 2, cy), (m + g / 2, cy)

    def size_ok(c):
        return 0.5 < c["a"] / markerA < 1.8

    # 2패스: 검출(D) 우선 → 한쪽만이면 대칭 미러(M) → 템플릿 보강(T) → 실패(X)
    L = R = None; tplL = tplR = None
    status, gap, mw = [], [], []      # mw: 직접검출(D) 프레임의 두 마커 평균 픽셀폭(거리보정용)
    for cs, f in zip(all_cs, files):
        g = imread_gray(f); st = None; dW = np.nan
        for s, l, r in sym_pairs(cs, H):
            if valid_pair((l["cx"], l["cy"]), (r["cx"], r["cy"])) and size_ok(l) and size_ok(r):
                L = (l["cx"], l["cy"]); R = (r["cx"], r["cy"]); st = "D"
                lx, ly, lbw, lbh = l["box"]; tplL = g[ly:ly + lbh, lx:lx + lbw].copy()
                rx, ry, rbw, rbh = r["box"]; tplR = g[ry:ry + rbh, rx:rx + rbw].copy()
                dW = (lbw + rbw) / 2.0
                break
        if st is None:
            singles = [c for c in cs if abs(c["cy"] - cy0) < CY_TOL
                       and abs(c["cx"] - cx0) > 30 and size_ok(c)]
            if len(singles) == 1:
                c = singles[0]; L, R = mirror_from(c["cx"], c["cy"]); st = "M"
                dW = c["box"][2]   # 마커 하나만 보여도 그 폭으로 거리보정 가능
        if st is None and L and R:
            tl = template_track(g, tplL, L, W, H)
            tr = template_track(g, tplR, R, W, H)
            if tl and tr and valid_pair(tl, tr):
                L, R = tl, tr; st = "T"
            elif tl and not tr:
                L, R = mirror_from(tl[0], tl[1]); st = "T"
            elif tr and not tl:
                L, R = mirror_from(tr[0], tr[1]); st = "T"
        if st is None:
            st = "X"
        status.append(st)
        gap.append((R[0] - L[0]) if (st != "X" and L and R) else np.nan)
        mw.append(dW)
    return np.array(status), np.array(gap, float), np.array(mw, float)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    do_plot = "--plot" in sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    rec = args[0]
    frames_dir = os.path.join(rec, "frames")
    files = sorted(glob.glob(os.path.join(frames_dir, "*.jpg")))
    if not files:
        sys.exit(f"프레임 없음: {frames_dir}")

    status, gap, mw = track(files)
    n = len(files)

    # 거리보정된 마커 간격(mm) -> 손가락 끝 폭(m). mw(마커 픽셀폭)는 D 프레임에만 있으므로
    # gap_m 도 D 에서만 나온다. M/T 는 마커 크기를 못 재 거리보정 불가 -> gap_m 빈값.
    marker_gap_mm = gap * MARKER_MM / mw
    gap_m = fingertip_m(marker_gap_mm)

    out_csv = os.path.join(rec, "gripper.csv")
    with open(out_csv, "w", encoding="utf-8") as fh:
        fh.write("frame_index,gap_m,status\n")   # SPEC 3컬럼. gap_px 는 아래 검증 출력에만 씀
        for i, path in enumerate(files):
            fid = int(os.path.splitext(os.path.basename(path))[0])  # poses.csv 와 join 되도록 실제 프레임번호
            gm = "" if np.isnan(gap_m[i]) else f"{gap_m[i]:.4f}"
            fh.write(f"{fid},{gm},{status[i]}\n")

    cnt = {s: int((status == s).sum()) for s in "DMTX"}
    valid = n - cnt["X"]
    print(f"프레임 {n} ({n / 30:.1f}s)  ->  {out_csv}")
    print(f"  D {cnt['D']} ({cnt['D']/n*100:.0f}%)  M {cnt['M']} ({cnt['M']/n*100:.0f}%)  "
          f"T {cnt['T']} ({cnt['T']/n*100:.0f}%)  X {cnt['X']} ({cnt['X']/n*100:.0f}%)  "
          f"-> 유효 {valid/n*100:.0f}%")
    if valid:
        print(f"  gap 범위 {np.nanmin(gap):.0f}~{np.nanmax(gap):.0f}px")
    if np.isfinite(gap_m).any():
        print(f"  손가락폭 gap_m {np.nanmin(gap_m)*1000:.0f}~{np.nanmax(gap_m)*1000:.0f}mm "
              f"(D 프레임 {np.isfinite(gap_m).sum()}개)")

    if do_plot:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        t = np.arange(n) / 30.0
        fig, ax = plt.subplots(2, 1, figsize=(11, 5.5), sharex=True,
                               gridspec_kw=dict(height_ratios=[4, 1]))
        ax[0].plot(t, gap, lw=1.2, color="k")
        ax[0].set_ylabel("gap (px)"); ax[0].grid(alpha=.3)
        ax[0].set_title(f"{os.path.basename(os.path.normpath(rec))}  gripper gap @30Hz  "
                        f"(D {cnt['D']/n*100:.0f}% / valid {valid/n*100:.0f}%)")
        col = {"D": "#2a9d8f", "M": "#8ab17d", "T": "#e9c46a", "X": "#e76f51"}
        for k, s in enumerate(status):
            ax[1].axvspan(t[k], t[k] + 1 / 30, color=col[s], lw=0)
        ax[1].set_yticks([]); ax[1].set_xlabel("time (s)"); ax[1].set_ylabel("D/M/T/X")
        out_png = os.path.join(rec, "gripper_gap.png")
        plt.tight_layout(); plt.savefig(out_png, dpi=110)
        print(f"  그림 -> {out_png}")


if __name__ == "__main__":
    main()
