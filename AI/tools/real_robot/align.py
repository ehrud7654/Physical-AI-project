"""Compare the live S22 view with the r4 reference view: vertical shift and roll of the background.
S22 실시간 화면을 r4 첫 화면과 비교해 배경의 상하 이동량과 기울기를 잰다.

정책 입력과 같은 전처리(180° 회전 → 224 높이 리사이즈 → 가운데 224 크롭)를 쓴다.
아래쪽(그리퍼·로봇 몸체)은 카메라에 고정돼 있어 비교에서 뺀다 — 위쪽 110행(배경)만 쓴다.

출력
  dy  배경이 r4 보다 위(+)/아래(−)로 몇 px   (+ 면 카메라가 아래를 봄 → 손목을 위로)
  rot 배경이 r4 대비 몇 도 회전               (+ 반시계 / − 시계방향 = 오른쪽이 처짐)
  ~/align.jpg  왼쪽 r4, 오른쪽 지금

사용: cd ~/Workspace/stanford_umi_demo && .venv/bin/python ~/align.py
      .venv/bin/python ~/align.py selftest
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import cv2
import numpy as np

REF = Path.home() / "Workspace/stanford_umi_demo/runs/d0924_r4_151104/input_cycle_0000_attempt_00.jpg"
OUT = Path.home() / "align.jpg"
ROWS = 110
CENTER = (112, 60)


def preprocess(bgr: np.ndarray) -> np.ndarray:
    """Same crop as policy_runtime.preprocess, returned as BGR.
    정책 입력과 같은 전처리 (BGR 로 반환)."""
    img = cv2.rotate(bgr, cv2.ROTATE_180)
    width = math.ceil(224 / 1080 * 1920)
    img = cv2.resize(img, (width, 224), interpolation=cv2.INTER_AREA)
    left = (width - 224) // 2
    return img[:, left:left + 224]


def measure(ref_bgr: np.ndarray, cur_bgr: np.ndarray) -> tuple[float, float, float]:
    """Return (dy px, rot deg, ecc score) of cur background relative to ref.
    ref 대비 cur 배경의 (상하 px, 회전 도, 일치도)."""
    a = cv2.cvtColor(ref_bgr, cv2.COLOR_BGR2GRAY)[:ROWS].astype(np.float32)
    b = cv2.cvtColor(cur_bgr, cv2.COLOR_BGR2GRAY)[:ROWS].astype(np.float32)
    win = cv2.createHanningWindow(a.shape[::-1], cv2.CV_32F)
    (sx, sy), _ = cv2.phaseCorrelate(a, b, win)
    warp = np.array([[1, 0, sx], [0, 1, sy]], np.float32)
    crit = (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 200, 1e-5)
    try:
        score, warp = cv2.findTransformECC(cv2.GaussianBlur(a, (5, 5), 0), cv2.GaussianBlur(b, (5, 5), 0),
                                           warp, cv2.MOTION_EUCLIDEAN, crit, None, 5)
    except cv2.error:
        score = float("nan")
    rot = -math.degrees(math.atan2(warp[1, 0], warp[0, 0]))
    # ECC warp maps cur→ref template coords; evaluate the shift at the image centre so roll
    # does not leak into dy. Report "+ = cur content higher than ref".
    cx, cy = CENTER
    py = warp[1, 0] * cx + warp[1, 1] * cy + warp[1, 2]
    return float(cy - py), rot, float(score)


def selftest() -> int:
    """Known answers: shift the reference by a known dy / roll and recover it.
    정답을 아는 입력: 기준 이미지를 알려진 만큼 옮기고 돌려서 되찾는다."""
    if not REF.is_file():
        print(f"기준 이미지 없음: {REF}")
        return 1
    ref = cv2.imread(str(REF))
    ok = 0
    cases = [(0, 0.0), (-12, 0.0), (8, 0.0), (0, 3.0), (-10, -2.5)]
    for dy, rot in cases:
        m = cv2.getRotationMatrix2D(CENTER, rot, 1.0)
        m[1, 2] += -dy
        moved = cv2.warpAffine(ref, m, (224, 224), borderMode=cv2.BORDER_REFLECT)
        gdy, grot, _ = measure(ref, moved)
        good = abs(gdy - dy) <= 1.5 and abs(grot - rot) <= 0.7
        ok += good
        print(f"  넣은 dy {dy:+4d} rot {rot:+.1f}  →  잰 dy {gdy:+6.1f} rot {grot:+5.2f}  {'OK' if good else 'FAIL'}")
    print(f"자체검사 {ok} / {len(cases)}")
    return 0 if ok == len(cases) else 1


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "selftest":
        return selftest()
    if selftest() != 0:
        print("FAIL: 자체검사 실패 — 측정하지 않는다")
        return 1
    sys.path.insert(0, str(Path.home() / "Workspace/stanford_umi_demo"))
    from runtime_jt.umi_checkpoint_executor.camera_stream import UsbCamera

    cam = UsbCamera(8765)
    try:
        for _ in range(3):
            frame, _ = cam.read_frame()
    finally:
        cam.release()
    cur = preprocess(frame)
    ref = cv2.imread(str(REF))
    dy, rot, score = measure(ref, cur)
    side = np.concatenate([ref, np.full((224, 6, 3), 255, np.uint8), cur], axis=1)
    cv2.line(side, (0, ROWS), (side.shape[1], ROWS), (0, 0, 255), 1)
    cv2.imwrite(str(OUT), cv2.resize(side, None, fx=2, fy=2))
    print(f"dy {dy:+.1f} px   rot {rot:+.2f} deg   일치도 {score:.2f}   → {OUT}")
    print("  dy +: 지금 배경이 r4 보다 위 = 카메라가 아래를 봄 → 손목(4번) 위로 = 틱 −")
    print("  dy −: 카메라가 위를 봄 → 손목 틱 +")
    print("  일치도 0.6 미만이면 숫자를 믿지 말고 align.jpg 를 눈으로 보세요")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
