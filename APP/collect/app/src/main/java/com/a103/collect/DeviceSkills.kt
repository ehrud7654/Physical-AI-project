package com.a103.collect

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 장치가 수집할 수 있는 스킬(학습) 목록. POST /api/v1/device-auth/skills(deviceId+secret)로 받아
 * 학습 피커·전송 시 학습 지정에 쓴다. 메모리+디스크 캐시(오프라인·재시작 대비). ARpose 모델 그대로.
 */
object DeviceSkills {
    private const val TAG = "AUTH"

    /** id=서버 skillId(그룹키), skillKey=계약값, displayName=표시명. */
    data class Skill(val id: String, val skillKey: String, val displayName: String)

    @Volatile var cached: List<Skill> = emptyList()
        private set

    private fun parse(arr: JSONArray): List<Skill> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Skill(o.getString("id"), o.getString("skillKey"), o.optString("displayName", o.getString("skillKey")))
    }

    /** 디스크 캐시에서 로드(메모리 비어있을 때만). */
    fun load(ctx: Context) {
        if (cached.isNotEmpty()) return
        val json = Prefs.skillCatalog(ctx)
        if (json.isBlank()) return
        cached = try { parse(JSONArray(json)) } catch (e: Exception) { emptyList() }
    }

    /** 서버에서 목록 새로고침(백그라운드 스레드). 실패 시 이전 캐시 유지. */
    fun refresh(ctx: Context): List<Skill> {
        val id = Prefs.deviceId(ctx).ifBlank { "test-device" }
        val secret = Prefs.deviceSecret(ctx).ifBlank { "test-secret" }
        val body = JSONObject().put("deviceId", id).put("deviceSecret", secret).toString()
        val conn = (URL("${BuildConfig.UPLOAD_BASE_URL}/api/v1/device-auth/skills")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) { Log.w(TAG, "스킬 목록 HTTP ${conn.responseCode}"); return cached }
            val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
            cached = parse(arr)
            Prefs.setSkillCatalog(ctx, arr.toString())
            Log.i(TAG, "스킬 목록 ${cached.size}건")
            cached
        } catch (e: Exception) { Log.w(TAG, "스킬 목록 실패: ${e.message}"); cached }
        finally { conn.disconnect() }
    }

    /** 테스트용 수동 학습 추가(서버 미연동 시 학습 분류 검증용). displayName=skillKey. */
    fun addLocal(ctx: Context, skillKey: String, displayName: String) {
        val arr = try { JSONArray(Prefs.skillCatalog(ctx).ifBlank { "[]" }) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().put("id", skillKey).put("skillKey", skillKey).put("displayName", displayName))
        Prefs.setSkillCatalog(ctx, arr.toString())
        cached = parse(arr)
    }
}
