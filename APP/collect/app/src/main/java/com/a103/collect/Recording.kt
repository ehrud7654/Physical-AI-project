package com.a103.collect

import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 한 번의 녹화 = 세션 폴더 하나. `rec_<ms>_<uuid8>/` 아래에 6파일을 만든다.
 *   manifest.json / frames.csv / encoded.csv / accelerometer.csv / gyroscope.csv / video.mp4
 *
 * manifest 는 .pending 로 쓰고 rename 해서 원자적으로 커밋한다(중간에 죽어도 반쪽 없음).
 * 모든 타임스탬프는 elapsedRealtimeNanos 단일 시간축(카메라 SENSOR_TIMESTAMP·IMU event.timestamp 와 동일).
 * 스키마·필드는 S22 UMI 파이프라인 계약(schema_version 2)을 그대로 따른다.
 */
class Recording(root: File, camera: JSONObject, skillKey: String) {

    val dir: File = File(root, "rec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
    /** 에피소드 식별자 = UUID v4. 촬영 1회당 1개, 업로드 단위. */
    val sessionId: String = UUID.randomUUID().toString().lowercase()
    val manifest = JSONObject()
    @Volatile var error: String? = null

    private val files = HashMap<String, BufferedWriter>()
    private var closed = false

    // frames.csv ↔ encoded.csv 정합: 카메라 프레임 메타를 pts_us(=sensorTs/1000)로 보관했다가
    // 인코딩 출력 시점에 매칭해 두 CSV를 같은 0-based 인덱스로 함께 쓴다(행 수 동일·연속·pts 일치 보장).
    private val pendingMeta = ConcurrentHashMap<Long, String>()
    private var frameIdx = 0

    init {
        if (!dir.mkdirs()) throw IOException("세션 폴더 생성 실패: $dir")
        manifest
            .put("schema_version", 2)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("status", "incomplete")
            .put("session_id", sessionId)
            .put("skill_key", skillKey)        // "" = 미분류(나중에 지정)
            .put("camera", camera)
            .put("device", JSONObject()                        // 계약: device 는 객체
                .put("model", Build.MODEL)
                .put("manufacturer", Build.MANUFACTURER)
                .put("os_version", "Android ${Build.VERSION.RELEASE}"))
            .put("android", Build.VERSION.RELEASE)
            .put("start_elapsed_ns", SystemClock.elapsedRealtimeNanos())
            .put("task", "grasp_lift_hold")
            .put("outcome", "unrated")
            .put("calibration_id", "uncalibrated")             // 계약: 비어있지 않은 문자열
            .put("training_ready", true)
            .put("width", 1920)
            .put("height", 1080)
            .put("imu_axes", "Android device axes; acceleration includes gravity")
            .put("image_rotation_degrees", 180)
            .put("rotation_baked_into_pixels", true)
            .put("decoder_rotation_degrees", 0)
            .put("image_orientation_contract", "upright_v1")
            .put("ui_orientation", "reverseLandscape")
            .put("gripper_left_id", 0)
            .put("gripper_right_id", 1)
            .put("marker_side_mm_user_reported", 16)
            .put("marker_black_square_size_verified", true)
            .put("imu_rotation_applied", false)
            .put("auto_exposure", "continuous; per-frame exposure recorded")   // AE 기본 ON(연속)
            .put("auto_white_balance", "continuous")                            // AWB 기본 ON(연속)
            .put("encoder_timestamp_base", "SENSOR")                            // 인코더 TIMESTAMP_BASE_SENSOR

        add("frames.csv", "frame_number,sensor_timestamp_ns,exposure_ns,frame_duration_ns,iso,rolling_shutter_skew_ns,focus_diopters,af_mode,eis_mode,ois_mode,crop_left,crop_top,crop_right,crop_bottom")
        add("encoded.csv", "sample_index,pts_us,size_bytes,flags")
        add("accelerometer.csv", "timestamp_ns,x_m_s2,y_m_s2,z_m_s2,accuracy")
        add("gyroscope.csv", "timestamp_ns,x_rad_s,y_rad_s,z_rad_s,accuracy")
        save()
    }

    private fun add(name: String, header: String) {
        val w = BufferedWriter(OutputStreamWriter(FileOutputStream(File(dir, name)), "UTF-8"), 1 shl 16)
        files[name] = w
        w.write(header); w.newLine()
    }

    @Synchronized
    fun row(name: String, line: String) {
        if (closed) return
        try { files[name]?.apply { write(line); newLine() } }
        catch (e: Exception) { error = e.toString() }
    }

    /** 카메라 프레임 메타 보관(frame_number 제외한 frames.csv 행). key = pts_us. */
    fun putFrameMeta(sensorTs: Long, metaRest: String) {
        if (!closed) pendingMeta[sensorTs / 1000] = metaRest
    }

    /**
     * 인코딩 출력 1건 → frames.csv·encoded.csv 를 같은 0-based 인덱스로 함께 기록.
     * 인코더 드레인 스레드 단독 호출(frameIdx 단일 writer). 시작 시 인코더 출력이 카메라 메타보다
     * 먼저 나오는 레이스가 있어 잠깐 기다린다 — 안 그러면 첫 프레임(키프레임)이 버려짐.
     */
    fun emitEncoded(ptsUs: Long, size: Int, flags: Int) {
        if (closed) return
        var meta = pendingMeta.remove(ptsUs)
        var tries = 0
        while (meta == null && tries++ < 10 && !closed) { Thread.sleep(5); meta = pendingMeta.remove(ptsUs) }
        meta ?: return
        val idx = frameIdx++
        row("frames.csv", "$idx,$meta")
        row("encoded.csv", "$idx,$ptsUs,$size,$flags")
    }

    @Synchronized
    fun save() {
        val pending = File(dir, "manifest.pending")
        FileOutputStream(pending).use { it.write(manifest.toString(2).toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if (!pending.renameTo(File(dir, "manifest.json"))) throw IOException("manifest commit 실패")
    }

    @Synchronized
    fun finish(reason: String?) {
        if (closed) return
        closed = true
        files.values.forEach { try { it.close() } catch (e: IOException) { error = e.toString() } }
        // start/stop_elapsed_ns = 실제 첫·마지막 카메라 프레임 센서 타임스탬프(계약 정합: startMonotonicNs·first/last·범위).
        val (fTs, lTs) = frameBounds()
        if (fTs > 0L && lTs > fTs) {
            manifest.put("start_elapsed_ns", fTs).put("stop_elapsed_ns", lTs)
            // IMU는 녹화 내내 수집돼 프레임 범위 밖 샘플이 생김 → 세션 범위 [start,stop] 로 트림(계약 §5).
            trimByTs("accelerometer.csv", fTs, lTs)
            trimByTs("gyroscope.csv", fTs, lTs)
        } else manifest.put("stop_elapsed_ns", SystemClock.elapsedRealtimeNanos())
        manifest
            .put("status", if (reason == null && error == null) "recorded" else "incomplete")
            .put("error", reason ?: (error ?: JSONObject.NULL))
        save()
    }

    /** CSV(첫 열 timestamp_ns)를 [lo,hi] 범위 행만 남기고 다시 쓴다. 헤더 유지. */
    private fun trimByTs(name: String, lo: Long, hi: Long) {
        val f = File(dir, name)
        if (!f.exists()) return
        val lines = f.readLines()
        if (lines.size < 2) return
        val kept = lines.drop(1).filter {
            val ts = it.substringBefore(",").trim().toLongOrNull()
            ts != null && ts in lo..hi
        }
        f.writeText((listOf(lines.first()) + kept).joinToString("\n") + "\n")
    }

    /** frames.csv 첫·마지막 sensor_timestamp_ns(2번째 열). */
    private fun frameBounds(): Pair<Long, Long> {
        val f = File(dir, "frames.csv")
        if (!f.exists()) return 0L to 0L
        val rows = f.readLines().drop(1).filter { it.isNotBlank() }
        if (rows.isEmpty()) return 0L to 0L
        fun ts(l: String) = l.split(",").getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        return ts(rows.first()) to ts(rows.last())
    }

    @Synchronized
    fun outcome(value: String) {
        manifest.put("outcome", value)
        save()
    }
}
