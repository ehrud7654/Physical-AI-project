package com.ssafy.arpose

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 프레임(JPEG) + pose(CSV) 를 폰 저장소에 기록하고, 추적 점프를 요약한다.
 *
 * 저장 위치: /sdcard/Android/data/com.ssafy.arpose/files/rec_YYYYMMDD_HHmmss/
 *   - frames 폴더 : 000000.jpg ...
 *   - poses.csv
 *   - meta.txt
 *   - summary.txt : 프레임 수, 실제 Hz, 점프 목록, 사용 가능 구간
 *
 * GL 스레드는 YUV to NV21 복사만 하고, JPEG 인코딩은 워커 스레드가 한다.
 */
class Recorder(private val baseDir: File) {

    companion object {
        private const val TAG = "REC"
        private const val QUEUE = 8
        private const val JPEG_QUALITY = 90
        private const val MIN_SEGMENT_SEC = 2.0

        // 점프·추적상실 지점 앞뒤로 잘라낼 여유. 경계 프레임의 pose 는 믿을 수 없다.
        private const val MARGIN_BEFORE = 0.1
        private const val MARGIN_AFTER = 0.2
    }

    private class Job(val index: Int, val w: Int, val h: Int, val buf: ByteArray)
    private class Jump(val index: Int, val tSec: Double, val mm: Double, val deg: Double)

    /** 추적을 놓친 구간. tracking != TRACKING 인 프레임이 이어진 범위다. */
    private class Paused(val fromSec: Double, var toSec: Double, var frames: Int)

    @Volatile var isRecording = false
        private set

    var savedFrames = 0; private set
    var droppedFrames = 0; private set
    var frameIndex = 0; private set

    var jumpCount = 0; private set
    var pausedFrames = 0; private set
    var maxJumpMm = 0.0; private set

    var currentDir: File? = null; private set

    private val jumps = ArrayList<Jump>()
    private val pausedSpans = ArrayList<Paused>()
    private var openPaused: Paused? = null
    private var startMs = 0L
    private var firstTsNs = 0L
    private var lastTsNs = 0L

    private var framesDir: File? = null
    private var csv: BufferedWriter? = null
    private val queue = ArrayBlockingQueue<Job>(QUEUE)
    private val pool = ArrayBlockingQueue<ByteArray>(QUEUE + 2)
    private var worker: Thread? = null

    fun start(metaText: String) {
        if (isRecording) return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(baseDir, "rec_$stamp")
        val fd = File(dir, "frames")
        fd.mkdirs()
        currentDir = dir
        framesDir = fd

        File(dir, "meta.txt").writeText(metaText)

        csv = File(dir, "poses.csv").bufferedWriter().apply {
            write("index,timestamp_ns,tracking,x,y,z,qx,qy,qz,qw,jump_mm,jump_deg,image\n")
        }

        savedFrames = 0; droppedFrames = 0; frameIndex = 0
        jumpCount = 0; maxJumpMm = 0.0
        pausedFrames = 0
        jumps.clear(); pausedSpans.clear(); openPaused = null
        startMs = System.currentTimeMillis(); firstTsNs = 0L; lastTsNs = 0L
        queue.clear()
        isRecording = true

        worker = Thread {
            while (isRecording || queue.isNotEmpty()) {
                val job = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                try {
                    val yuv = YuvImage(job.buf, ImageFormat.NV21, job.w, job.h, null)
                    FileOutputStream(File(framesDir, name(job.index))).use { fos ->
                        yuv.compressToJpeg(Rect(0, 0, job.w, job.h), JPEG_QUALITY, fos)
                    }
                    savedFrames++
                } catch (e: Exception) {
                    Log.e(TAG, "JPEG 저장 실패 idx=${job.index}", e)
                } finally {
                    pool.offer(job.buf)
                }
            }
        }.also { it.name = "rec-encoder"; it.priority = Thread.NORM_PRIORITY - 1; it.start() }

        Log.i(TAG, "녹화 시작: ${dir.absolutePath}")
    }

    /** MainActivity 가 점프를 감지했을 때 호출. */
    fun addJump(mm: Double, deg: Double) {
        if (!isRecording) return
        val tSec = if (firstTsNs != 0L) (lastTsNs - firstTsNs) / 1e9 else 0.0
        jumps.add(Jump(frameIndex, tSec, mm, deg))
        jumpCount++
        if (mm > maxJumpMm) maxJumpMm = mm
    }

    /** GL 스레드에서 호출. image 는 호출자가 close 한다. */
    fun submit(
        image: Image, timestampNs: Long, tracking: String,
        t: FloatArray, q: FloatArray, jumpMm: Double, jumpDeg: Double
    ) {
        if (!isRecording) return
        if (firstTsNs == 0L) firstTsNs = timestampNs
        lastTsNs = timestampNs

        val tSec = (timestampNs - firstTsNs) / 1e9
        if (tracking != "TRACKING") {
            pausedFrames++
            val open = openPaused
            if (open == null) {
                Paused(tSec, tSec, 1).also { pausedSpans.add(it); openPaused = it }
            } else {
                open.toSec = tSec; open.frames++
            }
        } else {
            openPaused = null
        }
        val idx = frameIndex++
        val w = image.width
        val h = image.height
        var imageName = ""

        if (queue.remainingCapacity() > 0) {
            val need = w * h * 3 / 2
            var buf = pool.poll()
            if (buf == null || buf.size != need) buf = ByteArray(need)
            try {
                yuv420ToNv21(image, buf)
                if (queue.offer(Job(idx, w, h, buf))) {
                    imageName = name(idx)
                } else {
                    droppedFrames++; pool.offer(buf)
                }
            } catch (e: Exception) {
                droppedFrames++; pool.offer(buf)
                Log.e(TAG, "YUV 변환 실패", e)
            }
        } else {
            droppedFrames++
        }

        csv?.write(
            "$idx,$timestampNs,$tracking," +
                "${fmt(t[0])},${fmt(t[1])},${fmt(t[2])}," +
                "${fmt(q[0])},${fmt(q[1])},${fmt(q[2])},${fmt(q[3])}," +
                String.format(Locale.US, "%.1f,%.2f", jumpMm, jumpDeg) + ",$imageName\n"
        )
    }

    /** [extra] 는 summary.txt 에 그대로 덧붙는다 (IMU 실측치 등). */
    fun stop(extra: String = ""): File? {
        if (!isRecording) return null
        isRecording = false
        try { worker?.join(5000) } catch (_: InterruptedException) {}
        worker = null
        try { csv?.flush(); csv?.close() } catch (_: Exception) {}
        csv = null

        val dir = currentDir
        val durSec = if (firstTsNs != 0L) (lastTsNs - firstTsNs) / 1e9 else 0.0
        val hz = if (durSec > 0) frameIndex / durSec else 0.0

        val sb = StringBuilder()
        sb.append("frames_total=$frameIndex\n")
        sb.append("frames_saved=$savedFrames\n")
        sb.append("frames_dropped=$droppedFrames\n")
        sb.append("duration_sec=${String.format(Locale.US, "%.2f", durSec)}\n")
        sb.append("rate_hz=${String.format(Locale.US, "%.2f", hz)}\n")
        sb.append("tracking_jumps=$jumpCount\n")
        sb.append("max_jump_mm=${String.format(Locale.US, "%.1f", maxJumpMm)}\n")

        sb.append("paused_frames=$pausedFrames\n")

        // 점프(순간)와 추적 상실(구간)을 모두 "쓸 수 없는 구간"으로 환산해 합친다.
        // 경계 프레임의 pose 는 믿을 수 없으므로 앞뒤로 여유를 둔다.
        val bad = ArrayList<DoubleArray>()
        for (j in jumps) bad.add(doubleArrayOf(j.tSec - MARGIN_BEFORE, j.tSec + MARGIN_AFTER))
        for (sp in pausedSpans) {
            bad.add(doubleArrayOf(sp.fromSec - MARGIN_BEFORE, sp.toSec + MARGIN_AFTER))
        }
        bad.sortBy { it[0] }
        val blocked = ArrayList<DoubleArray>()
        for (b in bad) {
            val last = blocked.lastOrNull()
            if (last != null && b[0] <= last[1]) {
                if (b[1] > last[1]) last[1] = b[1]
            } else blocked.add(b)
        }

        if (blocked.isEmpty()) {
            sb.append("verdict=OK - 전체 구간 사용 가능\n")
            sb.append("usable_segments=0.00~${String.format(Locale.US, "%.2f", durSec)}\n")
        } else {
            val reason = when {
                jumps.isNotEmpty() && pausedSpans.isNotEmpty() ->
                    "점프 ${jumpCount}회 + 추적상실 ${pausedSpans.size}구간"
                jumps.isNotEmpty() -> "점프 ${jumpCount}회"
                else -> "추적상실 ${pausedSpans.size}구간"
            }
            sb.append("verdict=$reason - 아래 구간으로 분할해서 사용\n")

            if (jumps.isNotEmpty()) {
                sb.append("jumps(index,t_sec,mm,deg):\n")
                for (j in jumps) {
                    sb.append(
                        "  ${j.index},${String.format(Locale.US, "%.2f", j.tSec)}," +
                            "${String.format(Locale.US, "%.1f", j.mm)}," +
                            "${String.format(Locale.US, "%.1f", j.deg)}\n"
                    )
                }
            }
            if (pausedSpans.isNotEmpty()) {
                sb.append("paused_spans(from_sec,to_sec,frames):\n")
                for (sp in pausedSpans) {
                    sb.append(
                        "  ${String.format(Locale.US, "%.2f", sp.fromSec)}," +
                            "${String.format(Locale.US, "%.2f", sp.toSec)},${sp.frames}\n"
                    )
                }
            }

            sb.append("usable_segments:\n")
            var from = 0.0
            var n = 0
            for (b in blocked) {
                if (b[0] - from >= MIN_SEGMENT_SEC) {
                    sb.append("  ${String.format(Locale.US, "%.2f~%.2f", from, b[0])}\n"); n++
                }
                if (b[1] > from) from = b[1]
            }
            if (durSec - from >= MIN_SEGMENT_SEC) {
                sb.append("  ${String.format(Locale.US, "%.2f~%.2f", from, durSec)}\n"); n++
            }
            if (n == 0) sb.append("  (없음 - 전체 폐기 권장)\n")
        }

        if (extra.isNotEmpty()) sb.append(extra)
        sb.append("recorded_at_ms=$startMs\n")

        val summary = sb.toString()
        try { dir?.let { File(it, "summary.txt").writeText(summary) } } catch (_: Exception) {}

        Log.i(TAG, "녹화 종료: ${dir?.absolutePath}\n$summary")
        return dir
    }

    private fun name(i: Int) = String.format(Locale.US, "%06d.jpg", i)
    private fun fmt(v: Float) = String.format(Locale.US, "%.6f", v)

    /** YUV_420_888 을 NV21 로 변환 (Y plane + VU interleaved) */
    private fun yuv420ToNv21(image: Image, out: ByteArray) {
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        var pos = 0
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            yBuf.get(out, pos, w)
            pos += w
        }

        val cw = w / 2
        val ch = h / 2
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRow = uPlane.rowStride; val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride; val vPix = vPlane.pixelStride
        for (row in 0 until ch) {
            var vi = row * vRow
            var ui = row * uRow
            for (col in 0 until cw) {
                out[pos++] = vBuf.get(vi); vi += vPix
                out[pos++] = uBuf.get(ui); ui += uPix
            }
        }
    }
}
