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

/**
 * 한 번의 녹화 = 세션 폴더 하나. `rec_<ms>_<uuid8>/` 아래에 6파일을 만든다.
 *   manifest.json / frames.csv / encoded.csv / accelerometer.csv / gyroscope.csv / video.mp4
 *
 * manifest 는 .pending 로 쓰고 rename 해서 원자적으로 커밋한다(중간에 죽어도 반쪽 없음).
 * 모든 타임스탬프는 elapsedRealtimeNanos 단일 시간축(카메라 SENSOR_TIMESTAMP·IMU event.timestamp 와 동일).
 * 스키마·필드는 S22 UMI 파이프라인 계약(schema_version 2)을 그대로 따른다.
 */
class Recording(root: File, camera: JSONObject) {

    val dir: File = File(root, "rec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}")
    val manifest = JSONObject()
    @Volatile var error: String? = null

    private val files = HashMap<String, BufferedWriter>()
    private var closed = false

    init {
        if (!dir.mkdirs()) throw IOException("세션 폴더 생성 실패: $dir")
        manifest
            .put("schema_version", 2)
            .put("app_version", BuildConfig_VERSION)
            .put("status", "incomplete")
            .put("camera", camera)
            .put("device", Build.MODEL)
            .put("android", Build.VERSION.RELEASE)
            .put("start_elapsed_ns", SystemClock.elapsedRealtimeNanos())
            .put("task", "grasp_lift_hold")
            .put("outcome", "unrated")
            .put("calibration_id", JSONObject.NULL)
            .put("training_ready", false)
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
            .put("marker_black_square_size_verified", false)
            .put("imu_rotation_applied", false)

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
        manifest
            .put("stop_elapsed_ns", SystemClock.elapsedRealtimeNanos())
            .put("status", if (reason == null && error == null) "recorded" else "incomplete")
            .put("error", reason ?: (error ?: JSONObject.NULL))
        save()
    }

    @Synchronized
    fun outcome(value: String) {
        manifest.put("outcome", value)
        save()
    }

    companion object {
        // build.gradle 의 versionName 을 코드에서 상수로 (BuildConfig 활성화 없이 간단히).
        const val BuildConfig_VERSION = "0.1"
    }
}
