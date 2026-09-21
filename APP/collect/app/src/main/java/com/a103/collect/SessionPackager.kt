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

        val zip = try { Uploader.zipSession(dir, sessionId, outDir) }
        catch (e: Exception) { Log.e(TAG, "zip 실패 ${dir.name}", e); return null }

        // 전송 때 고른 학습(.upload_skill)이 있으면 촬영 때 값보다 우선.
        val skillKey = File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim() else "" }
            .ifBlank { manifest.optString("skill_key") }

        val startNs = manifest.optLong("start_elapsed_ns")
        val stopNs = manifest.optLong("stop_elapsed_ns")

        val metadata = JSONObject()
            .put("sessionId", sessionId)
            .put("schemaVersion", manifest.optInt("schema_version"))
            .put("skillKey", skillKey)
            .put("source", "REAL")
            .put("outcome", manifest.optString("outcome"))
            .put("startElapsedNs", startNs)
            .put("stopElapsedNs", stopNs)
            .put("device", JSONObject())                       // 서버가 토큰으로 장치 판별
            .put("camera", manifest.optJSONObject("camera") ?: JSONObject())

        val summary = JSONObject()
            .put("frameCount", csvRows(dir, "encoded.csv"))
            .put("accelSampleCount", csvRows(dir, "accelerometer.csv"))
            .put("gyroSampleCount", csvRows(dir, "gyroscope.csv"))
            .put("durationSec", if (stopNs > startNs) (stopNs - startNs) / 1_000_000_000.0 else 0.0)

        return Packaged(zip, metadata, summary)
    }

    /** CSV 데이터 행 수(헤더 제외). */
    private fun csvRows(dir: File, name: String): Int {
        val f = File(dir, name)
        if (!f.exists()) return 0
        return (f.readLines().size - 1).coerceAtLeast(0)
    }
}
