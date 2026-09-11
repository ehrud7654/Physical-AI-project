package com.ssafy.arpose

import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * 촬영 직후 "이 세팅에서 그리퍼 마커가 잡히나"를 폰에서 바로 보는 즉시 QC (S15P21A103-160).
 *
 * 저장된 프레임 중 일부만 샘플링해 검은 사각형 마커 검출 여부를 센다. presence 수준 —
 * 정밀 gap_m·D/M/T/X·거리보정은 PC 후처리(tools/gripper_gap.py)가 담당한다.
 *
 * 검출 로직은 gripper_gap.py 의 candidates() 를 단순 포팅했다(같은 임계값·필터).
 * 끊긴 테두리 가로병합 단계는 QC 엔 불필요해 생략(우린 solid 검은 사각형이라 파편화 적음).
 * 파라미터는 실제 마커 촬영으로 튜닝하는 knob 이다.
 */
object MarkerQc {
    private const val TAG = "MARKERQC"

    // 검출 임계·필터 (gripper_gap.candidates 와 동일값). 실측 튜닝 대상.
    private const val ADAPT_BLOCK = 41
    private const val ADAPT_C = 15.0
    private const val MORPH_K = 9.0
    private const val A_MIN = 300.0
    private const val A_MAX = 12000.0
    private const val EDGE = 30.0            // 화면 좌우 가장자리 잡티 제외
    private const val Y_MAX_FRAC = 0.6       // 아래쪽(그리퍼 손잡이 등) 제외
    private const val A_MIN2 = 400.0
    private const val AR_LO = 0.9            // 가로로 눌린 사각형만
    private const val AR_HI = 3.2
    private const val FILL_MIN = 0.5         // boundingRect 대비 채움율
    private const val PAIR_MIN_SEP = 80.0    // 좌우 마커 최소 x 간격
    private const val PAIR_AREA_RATIO = 1.8  // 좌우 면적비 허용

    @Volatile private var ready = false

    /** OpenCV 네이티브 로드. 한 번만. 실패하면 QC 는 조용히 건너뛴다. */
    fun init(): Boolean {
        if (ready) return true
        ready = try { OpenCVLoader.initLocal() } catch (e: Throwable) {
            Log.e(TAG, "OpenCV 로드 실패", e); false
        }
        return ready
    }

    data class Result(val detected: Int, val total: Int) {
        val rate: Int get() = if (total == 0) 0 else detected * 100 / total
    }

    /** framesDir 의 jpg 를 최대 sample 장 균등 샘플링해 마커 검출률을 낸다. */
    fun detectRate(framesDir: File, sample: Int = 30): Result {
        if (!init()) return Result(0, 0)
        val files = framesDir.listFiles { f -> f.name.endsWith(".jpg") }
            ?.sortedBy { it.name } ?: return Result(0, 0)
        if (files.isEmpty()) return Result(0, 0)

        val step = maxOf(1, files.size / sample)
        val picked = files.filterIndexed { i, _ -> i % step == 0 }.take(sample)

        var detected = 0
        for (f in picked) {
            val g = Imgcodecs.imread(f.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
            if (g.empty()) { g.release(); continue }
            try {
                if (hasMarkerPair(g)) detected++
            } finally {
                g.release()
            }
        }
        Log.i(TAG, "마커 검출 ${detected}/${picked.size} (${picked.size}장 샘플, 전체 ${files.size})")
        return Result(detected, picked.size)
    }

    /** 한 프레임에 좌우 마커 쌍이 보이는지. */
    private fun hasMarkerPair(g: Mat): Boolean {
        val cs = candidates(g)
        if (cs.size < 2) return false
        for (i in cs.indices) for (j in i + 1 until cs.size) {
            val a = cs[i]; val b = cs[j]
            val l = if (a.cx < b.cx) a else b
            val r = if (a.cx < b.cx) b else a
            if (r.cx - l.cx < PAIR_MIN_SEP) continue
            val areaRatio = maxOf(l.area, r.area) / minOf(l.area, r.area)
            if (areaRatio <= PAIR_AREA_RATIO) return true
        }
        return false
    }

    private data class Cand(val cx: Double, val cy: Double, val area: Double)

    /** gripper_gap.candidates 포팅: 검은 사각형 마커 후보 덩어리. */
    private fun candidates(g: Mat): List<Cand> {
        val h = g.rows(); val w = g.cols()
        val work = Mat()
        Imgproc.GaussianBlur(g, work, Size(5.0, 5.0), 0.0)
        Imgproc.adaptiveThreshold(
            work, work, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, ADAPT_BLOCK, ADAPT_C
        )
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(MORPH_K, MORPH_K))
        Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, kernel)

        val cnts = ArrayList<MatOfPoint>()
        Imgproc.findContours(work, cnts, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        work.release(); kernel.release()

        val out = ArrayList<Cand>()
        for (c in cnts) {
            val rect = Imgproc.boundingRect(c)
            val a = hullArea(c)          // 헐 면적: ArUco 빈 속을 채운다
            c.release()
            if (a < A_MIN || a > A_MAX || rect.y > h * Y_MAX_FRAC) continue
            if (rect.x < EDGE || rect.x + rect.width > w - EDGE) continue
            val ar = rect.width.toDouble() / maxOf(rect.height, 1)
            val fill = a / (rect.width.toDouble() * rect.height)
            if (a < A_MIN2 || ar <= AR_LO || ar >= AR_HI || fill < FILL_MIN) continue
            out.add(Cand(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0, a))
        }
        return out
    }

    // convexHull 은 점 인덱스를 내므로 좌표로 되돌려 contourArea 를 잰다.
    private fun hullArea(c: MatOfPoint): Double {
        val idx = MatOfInt()
        Imgproc.convexHull(c, idx)
        val pts = c.toArray()
        val hp = MatOfPoint(*idx.toArray().map { pts[it] }.toTypedArray())
        val a = Imgproc.contourArea(hp)
        idx.release(); hp.release()
        return a
    }
}
