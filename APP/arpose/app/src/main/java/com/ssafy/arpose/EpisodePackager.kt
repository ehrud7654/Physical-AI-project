package com.ssafy.arpose

import android.util.Log
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * rec 폴더를 계약 아카이브로 묶는다 — `{sessionId}.zip` (STORE).
 * 엔트리: meta.json · summary.json · frames.csv · pose.csv · imu.csv · gripper.csv · frames 이하 jpg
 *
 * meta.json·summary.json 은 **UMI 인터페이스계약 v1.2.0 (episode-archive-v1)** 스키마로 생성한다.
 * pose.csv·imu.csv·gripper.csv 는 현재 녹화 산출물(poses.csv·imu.csv·gripper.csv)을 그대로 넣는다
 * (컬럼 계약화는 후속). frames.csv 는 poses.csv 에서 생성한다.
 */
object EpisodePackager {
    private const val TAG = "PACKAGE"
    private const val ARCORE_VERSION = "1.45.0"   // build.gradle com.google.ar:core
    private const val APP_VERSION = "1.0"

    /** zip + 세션 요청용 metadata·summary(계약 6.1, lower camel). endedAt 은 S3 key 일치용. */
    data class Packaged(val zip: File, val metadata: JSONObject, val summary: JSONObject, val endedAt: String)

    /** 계약 아카이브 zip 생성(이미 있으면 재사용) + 세션 요청 metadata·summary 반환. 실패 시 null. */
    fun pack(dir: File, sessionId: String, outDir: File): Packaged? {
        val sid = sessionId.ifBlank { readSessionId(dir) ?: return null }
        val ep = readEpisodeJson(dir)

        // 엔트리 바이트를 먼저 만든다 — summary.json 이 다른 파일들의 sha256 을 담아야 하므로.
        val poseBytes = readBytes(dir, "poses.csv") ?: ByteArray(0)
        val imuBytes = readBytes(dir, "imu.csv") ?: ByteArray(0)
        val gripperBytes = gripperCsv(dir)
        val framesBytes = framesCsv(dir)
        val metaJson = buildMetaJson(sid, ep)
        val metaBytes = metaJson.toString(2).toByteArray()
        val checksums = JSONObject()
            .put("meta.json", sha256(metaBytes))
            .put("frames.csv", sha256(framesBytes))
            .put("pose.csv", sha256(poseBytes))
            .put("imu.csv", sha256(imuBytes))
            .put("gripper.csv", sha256(gripperBytes))
        val summaryJson = buildSummaryJson(sid, ep, poseBytes, imuBytes, gripperBytes, framesBytes, checksums)
        val summaryBytes = summaryJson.toString(2).toByteArray()

        val zip = File(outDir, "$sid.zip")
        if (!(zip.exists() && zip.length() > 0)) {
            val tmp = File(outDir, "$sid.zip.part")
            try {
                ZipOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { zos ->
                    zos.setMethod(ZipOutputStream.STORED)   // 압축률 0
                    store(zos, "meta.json", metaBytes)
                    store(zos, "summary.json", summaryBytes)
                    store(zos, "frames.csv", framesBytes)
                    store(zos, "pose.csv", poseBytes)
                    store(zos, "imu.csv", imuBytes)
                    store(zos, "gripper.csv", gripperBytes)
                    File(dir, "frames").listFiles { f -> f.name.endsWith(".jpg") }
                        ?.sortedBy { it.name }
                        ?.forEach { store(zos, "frames/${it.name}", it.readBytes()) }
                }
                if (zip.exists()) zip.delete()
                tmp.renameTo(zip)
            } catch (e: Exception) {
                Log.e(TAG, "패키징 실패 ${dir.name}", e); tmp.delete(); return null
            }
        }
        return Packaged(zip, apiMetadataFrom(metaJson), apiSummaryFrom(summaryJson),
            metaJson.optString("ended_at"))
    }

    /** meta.json(snake) → 세션 요청 metadata(lower camel, 계약 6.1). */
    private fun apiMetadataFrom(m: JSONObject): JSONObject = JSONObject()
        .put("contractVersion", m.optString("contract_version"))
        .put("schemaVersion", m.optString("schema_version"))
        .put("skillKey", m.optString("skill_key"))
        .put("source", m.optString("source"))
        .put("success", m.opt("success") ?: JSONObject.NULL)
        .put("labelStatus", m.optString("label_status"))
        .put("startedAt", m.optString("started_at"))
        .put("endedAt", m.optString("ended_at"))
        .put("startMonotonicNs", m.optLong("start_monotonic_ns"))
        .put("timestampTimebase", m.optString("timestamp_timebase"))
        .put("device", JSONObject().put("deviceIdentifier",
            m.optJSONObject("device")?.optString("device_id") ?: "unknown"))
        .put("camera", m.optJSONObject("camera") ?: JSONObject())

    /** summary.json(snake) → 세션 요청 summary(lower camel, 계약 6.1). */
    private fun apiSummaryFrom(s: JSONObject): JSONObject = JSONObject()
        .put("schemaVersion", s.optString("schema_version"))
        .put("firstTimestampNs", s.optLong("first_timestamp_ns"))
        .put("lastTimestampNs", s.optLong("last_timestamp_ns"))
        .put("frameCount", s.optInt("frame_count"))
        .put("poseSampleCount", s.optInt("pose_sample_count"))
        .put("imuSampleCount", s.optInt("imu_sample_count"))
        .put("gripperSampleCount", s.optInt("gripper_sample_count"))
        .put("missingFrameCount", s.optInt("missing_frame_count"))
        .put("trackingGapCount", s.optInt("tracking_gap_count"))
        .put("trackingLostDurationNs", s.optLong("tracking_lost_duration_ns"))

    fun readSessionId(dir: File): String? =
        File(dir, "session_id.txt").let { if (it.exists()) it.readText().trim() else null }

    private fun readBytes(dir: File, name: String): ByteArray? =
        File(dir, name).let { if (it.exists()) it.readBytes() else null }

    private fun readEpisodeJson(dir: File): JSONObject? =
        readBytes(dir, "episode.json")?.let {
            try { JSONObject(String(it)) } catch (e: Exception) { null }
        }

    /**
     * meta.json — episode-archive-v1 (계약 5.2). episode.json 에서 값을 매핑한다.
     * 라벨은 아직 없으므로 success=null, label_status=UNLABELED (계약 라벨 불변식).
     */
    private fun buildMetaJson(sid: String, ep: JSONObject?): JSONObject {
        val summary = ep?.optJSONObject("summary")
        val clock = ep?.optJSONObject("clock_ref")
        val camIntr = ep?.optJSONObject("camera")?.optJSONObject("intrinsics")
        val raw = ep?.optJSONObject("meta_raw")

        val startMs = clock?.optString("start_wallclock_ms")?.toLongOrNull()
            ?: ep?.optLong("recorded_at_ms") ?: 0L
        val durSec = summary?.optDouble("duration_sec", 0.0) ?: 0.0
        val endMs = if (startMs > 0) startMs + (durSec * 1000).toLong() else 0L
        val startMono = clock?.optString("start_elapsed_realtime_ns")?.toLongOrNull() ?: 0L

        // "Samsung SM-S938N" → manufacturer / model
        val deviceStr = ep?.optString("device") ?: raw?.optString("device") ?: ""
        val manufacturer = deviceStr.substringBefore(" ", deviceStr)
        val model = deviceStr.substringAfter(" ", "")
        // "16 (SDK 36)" → "Android 16"
        val osStr = ep?.optString("android") ?: raw?.optString("android") ?: ""
        val osVersion = if (osStr.isNotBlank()) "Android " + osStr.substringBefore(" ") else "unknown"

        val device = JSONObject()
            .put("device_id", raw?.optString("device_id", "").orEmpty().ifBlank { "unknown" })
            .put("manufacturer", manufacturer)
            .put("model", model)
            .put("app_version", APP_VERSION)
            .put("os_version", osVersion)
            .put("arcore_version", ARCORE_VERSION)

        val camera = JSONObject()
            .put("width", camIntr?.optDouble("w", 0.0)?.toInt() ?: 0)
            .put("height", camIntr?.optDouble("h", 0.0)?.toInt() ?: 0)
            .put("fx", camIntr?.optDouble("fx", 0.0) ?: 0.0)
            .put("fy", camIntr?.optDouble("fy", 0.0) ?: 0.0)
            .put("cx", camIntr?.optDouble("cx", 0.0) ?: 0.0)
            .put("cy", camIntr?.optDouble("cy", 0.0) ?: 0.0)

        val coord = JSONObject()
            .put("handedness", "RIGHT_HANDED")
            .put("world_frame", "ARCORE_WORLD")
            .put("pose_frame", "CAMERA")
            .put("imu_frame", "DEVICE")

        // ponytail: gripper 는 현재 gap_m(미터) 유지 결정 → 계약 기본 NORMALIZED_0_TO_1 대신 실측 단위 표기.
        // gripper.csv 를 openness(0~1)로 바꾸면 여기 "NORMALIZED_0_TO_1" 로 되돌릴 것.
        val units = JSONObject()
            .put("position", "METER")
            .put("acceleration", "METER_PER_SECOND_SQUARED")
            .put("angular_velocity", "RADIAN_PER_SECOND")
            .put("gripper", "METER")
            .put("acceleration_includes_gravity", true)

        return JSONObject()
            .put("schema_version", "1.0")
            .put("contract_version", "episode-archive-v1")
            .put("session_id", sid)
            .put("skill_key", raw?.optString("active_skill_key", "") ?: "")
            .put("source", "REAL")
            .put("success", JSONObject.NULL)
            .put("label_status", "UNLABELED")
            .put("started_at", rfc3339(startMs))
            .put("ended_at", rfc3339(endMs))
            .put("start_monotonic_ns", startMono)
            .put("timestamp_timebase", "MONOTONIC_NS")
            .put("device", device)
            .put("camera", camera)
            .put("coordinate_system", coord)
            .put("units", units)
    }

    /** summary.json — 계약 5.3. 개수·시간·품질 집계 + 내부 파일 sha256(자기 자신·최종 zip 제외). */
    private fun buildSummaryJson(
        sid: String, ep: JSONObject?, poseBytes: ByteArray, imuBytes: ByteArray,
        gripperBytes: ByteArray, framesBytes: ByteArray, checksums: JSONObject
    ): JSONObject {
        val s = ep?.optJSONObject("summary")
        val frameCount = rows(framesBytes)
        val poseCount = rows(poseBytes)
        val (firstTs, lastTs) = firstLastTs(poseBytes)
        val durNs = if (lastTs > firstTs) lastTs - firstTs else 0L
        val dropped = s?.optInt("frames_dropped", 0) ?: 0
        val paused = s?.optInt("paused_frames", 0) ?: 0
        val jumps = s?.optInt("tracking_jumps", 0) ?: 0
        val writeFail = s?.optInt("frames_write_failed", 0) ?: 0
        val poseFail = s?.optInt("pose_rows_failed", 0) ?: 0
        val rateHz = s?.optDouble("rate_hz", 30.0) ?: 30.0
        val lostNs = if (rateHz > 0) (paused * 1e9 / rateHz).toLong() else 0L
        val quality = when {
            writeFail > 0 || poseFail > 0 -> "INVALID"
            jumps > 0 || paused > 0 -> "WARNING"
            else -> "VALID"
        }
        return JSONObject()
            .put("schema_version", "1.0")
            .put("session_id", sid)
            .put("frame_count", frameCount)
            .put("pose_sample_count", poseCount)
            .put("imu_sample_count", rows(imuBytes))
            .put("gripper_sample_count", rows(gripperBytes))
            .put("first_timestamp_ns", firstTs)
            .put("last_timestamp_ns", lastTs)
            .put("duration_ns", durNs)
            .put("missing_frame_count", dropped)
            .put("tracking_gap_count", if (paused > 0) 1 else 0)  // 근사(스팬 수 미집계)
            .put("tracking_lost_duration_ns", lostNs)
            .put("quality_status", quality)
            .put("checksums", checksums)
            .put("generated_at", rfc3339(System.currentTimeMillis()))
    }

    /** poses.csv(index,timestamp_ns,…,image) → frames.csv(frame_index,t_ns,file_name) */
    private fun framesCsv(dir: File): ByteArray {
        val sb = StringBuilder("frame_index,t_ns,file_name\n")
        File(dir, "poses.csv").takeIf { it.exists() }?.useLines { lines ->
            lines.drop(1).forEach { line ->
                val c = line.split(",")
                if (c.size >= 13 && c[12].trim().isNotEmpty()) sb.append("${c[0]},${c[1]},${c[12].trim()}\n")
            }
        }
        return sb.toString().toByteArray()
    }

    /** 후처리 gripper.csv 있으면 그대로, 없으면 헤더만(온디바이스/PC 후처리가 채움). */
    private fun gripperCsv(dir: File): ByteArray =
        readBytes(dir, "gripper.csv") ?: "t_ns,openness,tracking_state\n".toByteArray()

    /** CSV 데이터 행 수(헤더 1줄 제외). */
    private fun rows(bytes: ByteArray): Int {
        if (bytes.isEmpty()) return 0
        val n = String(bytes).lineSequence().count { it.isNotBlank() }
        return (n - 1).coerceAtLeast(0)
    }

    /** poses.csv 첫/마지막 데이터 행의 timestamp_ns(2번째 컬럼). */
    private fun firstLastTs(poseBytes: ByteArray): Pair<Long, Long> {
        if (poseBytes.isEmpty()) return 0L to 0L
        var first = 0L; var last = 0L
        String(poseBytes).lineSequence().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val ts = line.split(",").getOrNull(1)?.trim()?.toLongOrNull() ?: return@forEach
            if (first == 0L) first = ts
            last = ts
        }
        return first to last
    }

    private fun rfc3339(ms: Long): String {
        if (ms <= 0) return ""
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun store(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zos.putNextEntry(entry); zos.write(bytes); zos.closeEntry()
    }
}
