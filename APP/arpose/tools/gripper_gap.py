#!/usr/bin/env python3
"""녹화 프레임에서 그리퍼 개폐폭 시계열을 뽑아 gripper.csv 를 만든다.

손가락 레일 연결부에 붙인 **형광 마젠타 원 두 개**를 색(HSV)으로 검출해 좌우 간격(px)을 잰다.
색이 장면에 없어 오검출이 거의 없고 모션블러에도 강하다(검은 마커 대비 실측 확인).

사용:
  python gripper_gap.py <rec폴더>            # <rec폴더>/gripper.csv 생성
  python gripper_gap.py <rec폴더> --plot     # gripper_gap.png 도 함께

출력 gripper.csv: frame_index,gap_m,status   (SPEC_umi_raw_bundle 3컬럼)
  status = D(두 마커 검출) / X(마커 2개 미만 → 실패). gap_m 은 D 프레임에만.
  gap_m = 손가락 끝 폭(미터). 마커 지름(픽셀)으로 프레임별 거리를 보정해 얻는다.

필요: numpy, opencv-python. --plot 은 matplotlib.
"""
import os
import sys
import glob
import numpy as np
import cv2

# --- 마젠타 검출 (실측: hue 160~178 에 몰림, 고채도·고명도) ---
# 조명/노출 바뀌면 이 범위만 재조정하면 된다(캘리브 knob).
MAGENTA_LO = np.array([140, 90, 90])
MAGENTA_HI = np.array([175, 255, 255])
BLOB_MIN_A = 150.0      # 블롭 최소 면적(px^2, 1080p 기준). 너무 작은 반사·노이즈 제거.

# --- 개폐폭 캘리브레이션 (마젠타 원 15mm, rec_20260911_165325 개폐 스윕 실측) ---
# 마커는 지름 MARKER_MM 의 원. 이미지 속 마커 지름(px)과의 비로 그 프레임 거리를 보정한다.
# 마커 중심간 거리(거리보정 mm) ↔ 손가락 접촉면 gap(mm) 2점 선형(affine) 매핑.
# ponytail: 그리퍼/마커 바꾸면 개폐 스윕 재촬영해 아래 4개 값 재측정. 중간값 1개 있으면 선형성 검증 가능.
MARKER_MM = 15.0
MARKER_GAP_CLOSED_MM = 37.6   # 완전폐쇄 마커 중심간 거리(mm) — 스윕 실측 344px
MARKER_GAP_OPEN_MM = 134.0    # 완전개방(7cm, 레일유격 반영 실효최대) — 스윕 실측 1239px
FINGERTIP_CLOSED_MM = 0.0     # 완전폐쇄 손가락 gap(mm)
FINGERTIP_OPEN_MM = 70.0      # 완전개방 손가락 gap(mm) = 7cm (기구 최대 8cm은 손으로만, 미사용)


def fingertip_m(marker_gap_mm):
    """거리보정된 마커 간격(mm) -> 손가락 끝 폭(m). 두 극단 앵커로 선형 매핑 + 클램프."""
    span = MARKER_GAP_OPEN_MM - MARKER_GAP_CLOSED_MM
    ft = (FINGERTIP_CLOSED_MM + (FINGERTIP_OPEN_MM - FINGERTIP_CLOSED_MM)
          * (marker_gap_mm - MARKER_GAP_CLOSED_MM) / span)
    return np.clip(ft, FINGERTIP_CLOSED_MM, FINGERTIP_OPEN_MM) / 1000.0


def imread_bgr(path):
    # 한글 경로에서 cv2.imread 가 실패하므로 np.fromfile 로 읽는다.
    return cv2.imdecode(np.fromfile(path, np.uint8), cv2.IMREAD_COLOR)


def magenta_blobs(bgr):
    """한 프레임의 마젠타 블롭 목록 (면적 큰 순). 원형도는 필터하지 않는다
    — 마커가 비스듬히/블러로 타원이 되므로 색+크기로만 거른다."""
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    m = cv2.inRange(hsv, MAGENTA_LO, MAGENTA_HI)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    out = []
    for c in cnts:
        a = cv2.contourArea(c)
        if a < BLOB_MIN_A:
            continue
        mm = cv2.moments(c)
        if mm["m00"] == 0:
            continue
        cx = mm["m10"] / mm["m00"]; cy = mm["m01"] / mm["m00"]
        dia = 2.0 * np.sqrt(a / np.pi)      # 등가 지름(타원 두 축 평균 비슷) → 거리보정 자
        out.append(dict(cx=cx, cy=cy, a=a, dia=dia))
    return sorted(out, key=lambda b: -b["a"])


def track(files):
    """(status[], gap_px[], marker_dia_px[]) 반환. gap 은 X 프레임에서 NaN."""
    status, gap, mw = [], [], []
    for f in files:
        bs = magenta_blobs(imread_bgr(f))
        if len(bs) >= 2:
            l, r = sorted(bs[:2], key=lambda b: b["cx"])   # 큰 두 개를 좌/우로
            status.append("D")
            gap.append(r["cx"] - l["cx"])
            mw.append((l["dia"] + r["dia"]) / 2.0)
        else:
            status.append("X"); gap.append(np.nan); mw.append(np.nan)
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

    # 거리보정된 마커 간격(mm) -> 손가락 끝 폭(m). mw(마커 지름 px)는 D 프레임에만 있으므로
    # gap_m 도 D 에서만 나온다.
    marker_gap_mm = gap * MARKER_MM / mw
    gap_m = fingertip_m(marker_gap_mm)

    out_csv = os.path.join(rec, "gripper.csv")
    with open(out_csv, "w", encoding="utf-8") as fh:
        fh.write("frame_index,gap_m,status\n")   # SPEC 3컬럼
        for i, path in enumerate(files):
            fid = int(os.path.splitext(os.path.basename(path))[0])  # poses.csv 와 join
            gm = "" if np.isnan(gap_m[i]) else f"{gap_m[i]:.4f}"
            fh.write(f"{fid},{gm},{status[i]}\n")

    cnt = {s: int((status == s).sum()) for s in ("D", "X")}
    valid = cnt["D"]
    print(f"프레임 {n} ({n / 30:.1f}s)  ->  {out_csv}")
    print(f"  D {cnt['D']} ({cnt['D']/n*100:.0f}%)  X {cnt['X']} ({cnt['X']/n*100:.0f}%)  "
          f"-> 검출 {valid/n*100:.0f}%")
    if valid:
        print(f"  gap 범위 {np.nanmin(gap):.0f}~{np.nanmax(gap):.0f}px")
    if np.isfinite(gap_m).any():
        print(f"  손가락폭 gap_m {np.nanmin(gap_m)*1000:.0f}~{np.nanmax(gap_m)*1000:.0f}mm "
              f"(D 프레임 {np.isfinite(gap_m).sum()}개, 완전개방 70mm=7cm 기준)")

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
                        f"(D {cnt['D']/n*100:.0f}%)")
        col = {"D": "#c0559a", "X": "#e76f51"}
        for k, s in enumerate(status):
            ax[1].axvspan(t[k], t[k] + 1 / 30, color=col[s], lw=0)
        ax[1].set_yticks([]); ax[1].set_xlabel("time (s)"); ax[1].set_ylabel("D/X")
        out_png = os.path.join(rec, "gripper_gap.png")
        plt.tight_layout(); plt.savefig(out_png, dpi=110)
        print(f"  그림 -> {out_png}")


if __name__ == "__main__":
    main()
