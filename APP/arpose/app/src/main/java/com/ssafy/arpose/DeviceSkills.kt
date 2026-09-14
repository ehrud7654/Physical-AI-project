package com.ssafy.arpose

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 장치가 수집할 수 있는 스킬 목록 (POST /api/v1/device-auth/skills, deviceId+secret).
 * 사용자 토큰 없이 장치 자격만으로 서버 카탈로그를 받아 학습 피커를 채운다 (S15P21A103-162).
 *
 * 수집앱은 장치 토큰(UMI_CAMERA)만 있어 SKILL-01(GET /skills, 사용자 전용)을 못 부른다.
 * 이 엔드포인트가 그 대체다. 메모리 캐시 1개, 앱 시작 시 새로고침. 실패 시 이전 캐시 유지.
 */
object DeviceSkills {
    private const val TAG = "AUTH"

    /** id=서버 skillId(UUID, 로컬 편수 그룹키로도 사용), skillKey=계약값, displayName=표시. */
    data class Skill(val id: String, val skillKey: String, val displayName: String)

    @Volatile var cached: List<Skill> = emptyList()
        private set

    /** 목록 새로고침. 네트워크 호출이라 백그라운드 스레드에서 부른다. 성공 시 캐시 갱신·반환. */
    fun refresh(ctx: Context): List<Skill> {
        // QR 저장 전이면 목 검증용 폴백 자격(DeviceAuth 와 동일).
        val id = Prefs.deviceId(ctx).ifBlank { "test-device" }
        val secret = Prefs.deviceSecret(ctx).ifBlank { "test-secret" }
        val body = JSONObject().put("deviceId", id).put("deviceSecret", secret).toString()
        val conn = (URL("${Uploader.BASE_URL}/api/v1/device-auth/skills")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) { Log.w(TAG, "스킬 목록 HTTP ${conn.responseCode}"); return cached }
            val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Skill(o.getString("id"), o.getString("skillKey"), o.optString("displayName", o.getString("skillKey")))
            }
            cached = list
            Log.i(TAG, "스킬 목록 ${list.size}건")
            list
        } catch (e: Exception) { Log.w(TAG, "스킬 목록 실패: ${e.message}"); cached }
        finally { conn.disconnect() }
    }
}
