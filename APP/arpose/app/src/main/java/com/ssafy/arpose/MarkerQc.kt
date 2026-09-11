package com.ssafy.arpose

import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * 촬영 직후 "이 세팅에서 그리퍼 마커가 잡히나"를 폰에서 바로 보는 즉시 QC (S15P21A103-160).
 *
 * 형광 마젠타 원 두 개를 색(HSV)으로 검출한다. 색이 장면에 없어 오검출이 거의 없고
 * 모션블러에도 강함(검은 마커는 60% 전부 오검출이었음, 마젠타는 실측 100%).
 * gripper_gap.py 의 magenta_blobs 와 같은 방식 — presence(두 블롭 잡히나)까지만,
 * 정밀 gap_m 은 PC 후처리가 담당한다.
 */
object MarkerQc {
    private const val TAG = "MARKERQC"

    // 마젠타 HSV 범위 (실측 hue 160~178, 고채도·고명도). 조명·노출 바뀌면 이 값만 재조정(knob).
    private val LO = Scalar(140.0, 90.0, 90.0)
    private val HI = Scalar(175.0, 255.0, 255.0)
    private const val BLOB_MIN_A = 150.0   // 블롭 최소 면적(px^2, 1080p 기준)

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

    /** framesDir 의 jpg 를 최대 sample 장 균등 샘플링해 마젠타 마커 검출률을 낸다. */
    fun detectRate(framesDir: File, sample: Int = 30): Result {
        if (!init()) return Result(0, 0)
        val files = framesDir.listFiles { f -> f.name.endsWith(".jpg") }
            ?.sortedBy { it.name } ?: return Result(0, 0)
        if (files.isEmpty()) return Result(0, 0)

        val step = maxOf(1, files.size / sample)
        val picked = files.filterIndexed { i, _ -> i % step == 0 }.take(sample)

        var detected = 0
        for (f in picked) {
            val bgr = Imgcodecs.imread(f.absolutePath, Imgcodecs.IMREAD_COLOR)
            if (bgr.empty()) { bgr.release(); continue }
            try {
                if (hasMagentaPair(bgr)) detected++
            } finally {
                bgr.release()
            }
        }
        Log.i(TAG, "마젠타 검출 ${detected}/${picked.size} (전체 ${files.size})")
        return Result(detected, picked.size)
    }

    /** 한 프레임에 마젠타 블롭이 2개 이상 보이는지. */
    private fun hasMagentaPair(bgr: Mat): Boolean {
        val hsv = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
        val mask = Mat()
        Core.inRange(hsv, LO, HI, mask)
        hsv.release()

        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val k7 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, k3)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k7)
        k3.release(); k7.release()

        val cnts = ArrayList<MatOfPoint>()
        Imgproc.findContours(mask, cnts, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        mask.release()

        var blobs = 0
        for (c in cnts) {
            if (Imgproc.contourArea(c) >= BLOB_MIN_A) blobs++
            c.release()
        }
        return blobs >= 2
    }
}
