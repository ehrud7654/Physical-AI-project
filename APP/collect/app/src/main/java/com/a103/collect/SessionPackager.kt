package com.a103.collect

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 세션 폴더(6파일)를 zip 하고, 세션 발급 요청용 metadata·summary 를 manifest.json 에서 만든다.
 * 흐름·필드 형태(lower camel)는 ARpose 계약을 따른다. 파이프라인은 zip 안 manifest 를 직접 읽으므로
 * 여기 metadata 는 서버 라우팅·그룹핑(skillKey)용. 정확한 필드셋은 BE 확정 시 맞춘다.
 */
object SessionPackager {
    private const val TAG = "PACKAGE"

    data class Packaged(val zip: File, val metadata: JSONObject, val summary: JSONObject)

    fun pack(dir: File, sessionId: String, outDir: File): Packaged? {
        val manifest = try { JSONObject(File(dir, "manifest.json").readText()) }
        catch (e: Exception) { Log.e(TAG, "manifest 없음 ${dir.name}", e); return null }

        // 전송 때 고른 학습(.upload_skill)이 있으면 촬영 때 값보다 우선. 빈 키(미분류)는 서버가 거부.
        val skillKey = File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim() else "" }
            .ifBlank { manifest.optString("skill_key") }
        if (skillKey.isBlank()) { Log.w(TAG, "학습 미지정 — 전송 불가 ${dir.name}"); return null }

        val zip = try { Uploader.zipSession(dir, sessionId, outDir) }
        catch (e: Exception) { Log.e(TAG, "zip 실패 ${dir.name}", e); return null }

        // 수집 시간 기준은 세션 시작이 아니라 첫/마지막 프레임(계약: startMonotonicNs == firstTimestampNs,
        // lastTimestampNs == startMonotonicNs + (endedAt−startedAt)). 프레임 없으면 전송 불가.
        val (firstTs, lastTs) = frameTsBounds(dir)
        if (firstTs <= 0L || lastTs < firstTs) { Log.w(TAG, "프레임 타임스탬프 없음/역전 ${dir.name}"); return null }

        val startNs = manifest.optLong("start_elapsed_ns")
        // 폴더명 rec_<epochMs>_… = 세션 시작 wall clock. wall(ts) = startWallNs + (ts − startNs) 로 프레임 시각 환산.
        val startWallMs = dir.name.removePrefix("rec_").substringBefore("_").toLongOrNull() ?: 0L
        val startWallNs = startWallMs * 1_000_000L
        // ARpose(실서버 검증본)의 값을 그대로 따른다: schemaVersion "1.0", success=null, labelStatus=UNLABELED.
        val metadata = JSONObject()
            .put("contractVersion", "episode-archive-v1")
            .put("schemaVersion", "1.0")
            .put("skillKey", skillKey)
            .put("source", "REAL")
            .put("success", JSONObject.NULL)                   // 아직 라벨 전(계약 라벨 불변식)
            .put("labelStatus", "UNLABELED")
            .put("startedAt", iso8601(startWallNs + (firstTs - startNs)))
            .put("endedAt", iso8601(startWallNs + (lastTs - startNs)))
            .put("startMonotonicNs", startNs)                  // 계약 §6: manifest start_elapsed_ns 와 동일(=첫 프레임 ts)
            .put("timestampTimebase", "MONOTONIC_NS")
            .put("device", JSONObject())                       // 서버가 토큰으로 장치 판별
            .put("camera", manifest.optJSONObject("camera") ?: JSONObject())

        val summary = JSONObject()
            .put("schemaVersion", "1.0")
            .put("firstTimestampNs", firstTs)
            .put("lastTimestampNs", lastTs)
            .put("frameCount", csvRows(dir, "frames.csv"))     // 계약 §6: frames.csv 데이터 행 수와 일치
            .put("poseSampleCount", 0)                         // 포즈 없음(초광각 Camera2)
            .put("imuSampleCount", csvRows(dir, "accelerometer.csv") + csvRows(dir, "gyroscope.csv"))
            .put("gripperSampleCount", 0)                      // 온디바이스 그리퍼 샘플 없음
            .put("missingFrameCount", 0)
            .put("trackingGapCount", 0)
            .put("trackingLostDurationNs", 0L)

        return Packaged(zip, metadata, summary)
    }

    /** epoch ns → ISO-8601(오프셋·가변 소수자리, 끝 0 생략). 예: 2026-09-21T15:48:08.962267134+09:00 */
    private fun iso8601(epochNs: Long): String {
        val sec = Math.floorDiv(epochNs, 1_000_000_000L)
        val nano = Math.floorMod(epochNs, 1_000_000_000L)
        return java.time.OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(sec, nano), java.time.ZoneId.systemDefault()
        ).toString()
    }

    /** frames.csv 의 첫·마지막 sensor_timestamp_ns(2번째 열). */
    private fun frameTsBounds(dir: File): Pair<Long, Long> {
        val f = File(dir, "frames.csv")
        if (!f.exists()) return 0L to 0L
        val rows = f.readLines().drop(1).filter { it.isNotBlank() }
        if (rows.isEmpty()) return 0L to 0L
        fun ts(line: String) = line.split(",").getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        return ts(rows.first()) to ts(rows.last())
    }

    /** CSV 데이터 행 수(헤더 제외). */
    private fun csvRows(dir: File, name: String): Int {
        val f = File(dir, name)
        if (!f.exists()) return 0
        return (f.readLines().size - 1).coerceAtLeast(0)
    }
}
