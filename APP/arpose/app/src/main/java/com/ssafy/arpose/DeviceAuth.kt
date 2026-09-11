package com.ssafy.arpose

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 장치 인증 (API명세 1.3.0 DEVICE-05). deviceId·deviceSecret(QR 로 저장)로 짧은 수명(15분)
 * Device Access Token 을 발급받아 캐시하고, 만료·401 시 자동 재발급한다 (S15P21A103-163).
 *
 * 토큰·secret 은 로그에 남기지 않는다. base URL 은 Uploader.BASE_URL(디버그=목).
 */
object DeviceAuth {
    private const val TAG = "AUTH"
    private const val SKEW_MS = 30_000L   // 만료 30초 전이면 미리 갱신

    @Volatile private var token: String? = null
    @Volatile private var expiryMs = 0L

    /** 유효 토큰. 없거나 만료 임박이면 재발급. 실패 시 null. */
    @Synchronized
    fun token(ctx: Context): String? {
        val t = token
        if (t != null && System.currentTimeMillis() < expiryMs - SKEW_MS) return t
        return refresh(ctx)
    }

    /** 401 등에서 강제 재발급. */
    @Synchronized
    fun refresh(ctx: Context): String? {
        // QR 저장 전이면 목 검증용 폴백 자격.
        val id = Prefs.deviceId(ctx).ifBlank { "test-device" }
        val secret = Prefs.deviceSecret(ctx).ifBlank { "test-secret" }
        val body = JSONObject().put("deviceId", id).put("deviceSecret", secret).toString()
        val conn = (URL("${Uploader.BASE_URL}/api/v1/device-auth/token")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) { Log.w(TAG, "토큰 발급 HTTP ${conn.responseCode}"); return null }
            val d = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("data")
            token = d.getString("accessToken")
            val ttl = d.optInt("expiresIn", 900)
            expiryMs = System.currentTimeMillis() + ttl * 1000L
            Log.i(TAG, "토큰 발급 (ttl=${ttl}s)")
            token
        } catch (e: Exception) { Log.w(TAG, "토큰 발급 실패: ${e.message}"); null }
        finally { conn.disconnect() }
    }
}
