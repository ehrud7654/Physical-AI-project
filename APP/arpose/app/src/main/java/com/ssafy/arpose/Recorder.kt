package com.ssafy.arpose

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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

        // 실측 소요 용량: 프레임 JPEG 69~83MB/분 + IMU 1.4MB/분. 보수적으로 85 로 본다.
        private const val MB_PER_MIN = 85L

        /** 이만큼 여유가 없으면 녹화를 시작하지 않는다 (약 6분치 = 30초 시연 12회). */
        const val START_MIN_FREE_MB = 500L

        /** 녹화 중 이 아래로 떨어지면 자동 정지한다 (약 2분치 여유를 남기고 멈춘다). */
        const val STOP_MIN_FREE_MB = 200L

        /** 공간 확인 주기(처리한 프레임 수). 30fps 기준 약 5초. */
        private const val SPACE_CHECK_FRAMES = 150
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

    /** JPEG 저장이 실패한 프레임 수. 워커 스레드만 증가시킨다. */
    var writeFailedFrames = 0; private set

    /** poses.csv 행 쓰기가 실패한 수. GL 스레드만 증가시킨다. */
    var csvFailedRows = 0; private set

    /** 공간 부족으로 정지가 필요한 상태. MainActivity 가 매 프레임 확인한다. */
    @Volatile var lowSpace = false; private set
    var maxJumpMm = 0.0; private set

    var currentDir: File? = null; private set

    private val jumps = ArrayList<Jump>()
    private val pausedSpans = ArrayList<Paused>()
    private var openPaused: Paused? = null
    private var startMs = 0L
    private var firstTsNs = 0L
    private var lastTsNs = 0L
    private var lastMeta = ""   // start() 로 받은 meta 원문. stop() 에서 episode.json 에 담는다.

    private var freeMbStart = 0L

    /** 지금 쓸 수 있는 여유 공간(MB). */
    fun freeMb(): Long = baseDir.usableSpace / (1024L * 1024L)

    /** 녹화를 시작해도 될 만큼 공간이 남았는지. */
    fun hasStartSpace(): Boolean = freeMb() >= START_MIN_FREE_MB

    /** 남은 공간으로 몇 분이나 찍을 수 있는지. */
    fun remainingMinutes(): Int = (freeMb() / MB_PER_MIN).toInt()

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
        lastMeta = metaText

        File(dir, "meta.txt").writeText(metaText)

        csv = File(dir, "poses.csv").bufferedWriter().apply {
            write("index,timestamp_ns,tracking,x,y,z,qx,qy,qz,qw,jump_mm,jump_deg,image\n")
        }

        savedFrames = 0; droppedFrames = 0; frameIndex = 0
        jumpCount = 0; maxJumpMm = 0.0
        pausedFrames = 0
        writeFailedFrames = 0; csvFailedRows = 0; lowSpace = false
        freeMbStart = freeMb()
        jumps.clear(); pausedSpans.clear(); openPaused = null
        startMs = System.currentTimeMillis(); firstTsNs = 0L; lastTsNs = 0L
        queue.clear()
        isRecording = true

        worker = Thread {
            var processed = 0
            while (isRecording || queue.isNotEmpty()) {
                val job = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                try {
                    val yuv = YuvImage(job.buf, ImageFormat.NV21, job.w, job.h, null)
                    FileOutputStream(File(framesDir, name(job.index))).use { fos ->
                        yuv.compressToJpeg(Rect(0, 0, job.w, job.h), JPEG_QUALITY, fos)
                    }
                    savedFrames++
                } catch (e: Exception) {
                    writeFailedFrames++
                    Log.e(TAG, "JPEG 저장 실패 idx=${job.index}", e)
                } finally {
                    pool.offer(job.buf)
                }
                // 프레임 번호가 아니라 실제로 처리한 개수로 센다.
                // 번호로 세면 그 번호가 드롭됐을 때 확인 주기를 통째로 건너뛴다.
                if (++processed % SPACE_CHECK_FRAMES == 0 && freeMb() < STOP_MIN_FREE_MB) {
                    lowSpace = true
                    Log.w(TAG, "저장 공간 부족 (${freeMb()}MB) - 정지 요청")
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

        // 디스크가 차면 여기서도 실패한다. pose 가 없으면 그 프레임은 학습에 못 쓰므로
        // JPEG 와 마찬가지로 집계한다.
        try {
            csv?.write(
                "$idx,$timestampNs,$tracking," +
                    "${fmt(t[0])},${fmt(t[1])},${fmt(t[2])}," +
                    "${fmt(q[0])},${fmt(q[1])},${fmt(q[2])},${fmt(q[3])}," +
                    String.format(Locale.US, "%.1f,%.2f", jumpMm, jumpDeg) + ",$imageName\n"
            )
        } catch (e: Exception) {
            csvFailedRows++
            Log.e(TAG, "poses.csv 쓰기 실패 idx=$idx", e)
        }
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
        sb.append("frames_write_failed=$writeFailedFrames\n")
        sb.append("pose_rows_failed=$csvFailedRows\n")

        // 쓴 것과 센 것이 안 맞으면 어딘가에서 조용히 유실된 것이다.
        val accounted = savedFrames + droppedFrames + writeFailedFrames
        if (accounted != frameIndex) {
            sb.append("WARNING=프레임 수 불일치 total=$frameIndex accounted=$accounted\n")
        }
        if (writeFailedFrames > 0) {
            sb.append("WARNING=JPEG 저장 실패 ${writeFailedFrames}장 - 저장 공간을 확인할 것\n")
        }
        if (csvFailedRows > 0) {
            sb.append("WARNING=pose 기록 실패 ${csvFailedRows}행 - 해당 프레임은 학습에 쓸 수 없음\n")
        }
        if (lowSpace) {
            sb.append("WARNING=저장 공간 부족으로 자동 정지됨\n")
        }
        sb.append("free_mb_start=$freeMbStart\n")
        sb.append("free_mb_end=${freeMb()}\n")
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

        val segList = ArrayList<DoubleArray>()   // episode.json 용 사용가능 구간 [from,to]
        val verdict: String
        if (blocked.isEmpty()) {
            verdict = "OK - 전체 구간 사용 가능"
            sb.append("verdict=$verdict\n")
            sb.append("usable_segments=0.00~${String.format(Locale.US, "%.2f", durSec)}\n")
            segList.add(doubleArrayOf(0.0, durSec))
        } else {
            val reason = when {
                jumps.isNotEmpty() && pausedSpans.isNotEmpty() ->
                    "점프 ${jumpCount}회 + 추적상실 ${pausedSpans.size}구간"
                jumps.isNotEmpty() -> "점프 ${jumpCount}회"
                else -> "추적상실 ${pausedSpans.size}구간"
            }
            verdict = "$reason - 분할 사용"
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
                    segList.add(doubleArrayOf(from, b[0]))
                }
                if (b[1] > from) from = b[1]
            }
            if (durSec - from >= MIN_SEGMENT_SEC) {
                sb.append("  ${String.format(Locale.US, "%.2f~%.2f", from, durSec)}\n"); n++
                segList.add(doubleArrayOf(from, durSec))
            }
            if (n == 0) sb.append("  (없음 - 전체 폐기 권장)\n")
        }

        if (extra.isNotEmpty()) sb.append(extra)
        sb.append("recorded_at_ms=$startMs\n")

        val summary = sb.toString()
        try { dir?.let { File(it, "summary.txt").writeText(summary) } } catch (_: Exception) {}

        // 학습 계약 + 요약을 기계가 바로 읽는 episode.json 으로도 내보낸다 (서버 없이 파일 직수령용).
        try { dir?.let { writeEpisodeJson(it, durSec, hz, verdict, segList) } }
        catch (e: Exception) { Log.e(TAG, "episode.json 쓰기 실패", e) }

        Log.i(TAG, "녹화 종료: ${dir?.absolutePath}\n$summary")
        return dir
    }

    /**
     * episode.json — 학습팀 데이터 계약을 기계가 바로 읽게 정리한 매니페스트.
     * meta.txt(사람용) 의 key=value 를 구조화하고, 좌표/쿼터니언/클럭/단위 규약과
     * 요약(사용가능 구간)·파일 위치를 한 파일에 담는다.
     */
    private fun writeEpisodeJson(
        dir: File, durSec: Double, hz: Double, verdict: String, segList: List<DoubleArray>
    ) {
        // meta.txt 를 최상위 key=value 로 파싱 (값 안의 '=' 는 그대로 둔다)
        val m = JSONObject()
        for (line in lastMeta.split("\n")) {
            val i = line.indexOf('=')
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1))
        }

        // 학습팀과 합의한 규약 (좌표계·순서·클럭·단위·이미지·그리퍼)
        val contract = JSONObject()
            .put("pose_frame", "T_world_camera")
            .put("up_axis", "Y")
            .put("quaternion_order", "xyzw")          // scalar-last (qx,qy,qz,qw)
            .put("world_origin", "per_episode_relative")
            .put("translation_units", "m")
            .put("clock", "elapsedRealtimeNanos")     // pose·frame·imu 공통
            .put("image_format", "jpeg")
            .put("gripper_width_units", "m")
            .put("gripper_width_source", "postprocess: tools/gripper_gap.py")

        // intrinsics 문자열("w=.. h=.. fx=.. fy=.. cx=.. cy=..") → 수치
        val intr = JSONObject()
        for (tok in m.optString("intrinsics").split(" ")) {
            val kv = tok.split("=")
            if (kv.size == 2) kv[1].toDoubleOrNull()?.let { intr.put(kv[0], it) }
        }
        if (intr.has("w") && intr.has("h")) {
            contract.put("image_size", JSONArray().put(intr.getDouble("w").toInt()).put(intr.getDouble("h").toInt()))
        }

        val segs = JSONArray()
        for (s in segList) {
            segs.put(JSONArray().put(round2(s[0])).put(round2(s[1])))
        }

        val summary = JSONObject()
            .put("frames_total", frameIndex).put("frames_saved", savedFrames)
            .put("frames_dropped", droppedFrames).put("frames_write_failed", writeFailedFrames)
            .put("pose_rows_failed", csvFailedRows)
            .put("duration_sec", round2(durSec)).put("rate_hz", round2(hz))
            .put("tracking_jumps", jumpCount).put("max_jump_mm", round2(maxJumpMm))
            .put("paused_frames", pausedFrames).put("low_space", lowSpace)
            .put("verdict", verdict).put("usable_segments", segs)

        val poses = JSONObject()
            .put("file", "poses.csv")
            .put("columns", JSONArray(listOf(
                "index", "timestamp_ns", "tracking", "x", "y", "z",
                "qx", "qy", "qz", "qw", "jump_mm", "jump_deg", "image"
            )))
        val files = JSONObject()
            .put("frames", JSONObject().put("dir", "frames").put("pattern", "%06d.jpg"))
            .put("poses", poses)
            .put("imu", JSONObject().put("file", "imu.csv"))
            .put("meta_txt", "meta.txt").put("summary_txt", "summary.txt")

        val session = JSONObject()
            .put("server_url", m.optString("server_url"))
            .put("user_id", m.optString("user_id"))
            .put("session_id", m.optString("session_id"))
            .put("skill_id", m.optString("skill_id"))

        val camera = JSONObject()
            .put("intrinsics", intr)
            .put("config", m.optString("camera_config"))
            .put("modes", m.optString("camera_modes"))
            .put("focus_mode", m.optString("focus_mode"))
            .put("ae_lock", m.optString("ae_lock"))

        val root = JSONObject()
            .put("schema", "arpose.episode/1")
            .put("episode", dir.name)
            .put("recorded_at_ms", startMs)
            .put("contract", contract)
            .put("session", session)
            .put("device", m.optString("device"))
            .put("android", m.optString("android"))
            .put("camera", camera)
            .put("clock_ref", JSONObject()
                .put("start_wallclock_ms", m.optString("start_wallclock_ms"))
                .put("start_elapsed_realtime_ns", m.optString("start_elapsed_realtime_ns")))
            .put("files", files)
            .put("summary", summary)
            .put("meta_raw", m)   // 원본 전체 (누락 대비)

        File(dir, "episode.json").writeText(root.toString(2))
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0

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
