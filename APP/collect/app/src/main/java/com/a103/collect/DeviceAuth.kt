package com.a103.collect

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 장치 인증. QR enroll 로 deviceId·deviceSecret 을 1회 수령(Keystore 저장)하고,
 * 짧은 수명 Device Access Token 을 발급받아 캐시·자동 갱신한다. 토큰·secret 은 로그 금지.
 * BASE_URL 은 빌드 타입별(BuildConfig.UPLOAD_BASE_URL).
 */
object DeviceAuth {
    private const val TAG = "AUTH"
    private const val SKEW_MS = 30_000L
    private val BASE = BuildConfig.UPLOAD_BASE_URL

    @Volatile private var token: String? = null
    @Volatile private var expiryMs = 0L

    @Synchronized
    fun token(ctx: Context): String? {
        val t = token
        if (t != null && System.currentTimeMillis() < expiryMs - SKEW_MS) return t
        return refresh(ctx)
    }

    /** QR URI 의 enrollmentId·registrationToken 을 교환해 자격을 저장. 성공 시 true. (백그라운드 스레드) */
    fun enroll(ctx: Context, enrollmentId: String, registrationToken: String): Boolean {
        val body = JSONObject().put("enrollmentId", enrollmentId)
            .put("registrationToken", registrationToken).toString()
        val conn = (URL("$BASE/api/v1/device-auth/enroll").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
                Log.w(TAG, "enroll HTTP ${conn.responseCode} body=$err"); return false
            }
            val d = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("data")
            Prefs.setDeviceCreds(ctx, d.getString("deviceId"), d.getString("deviceSecret"))
            token = null; expiryMs = 0L
            Log.i(TAG, "장치 등록 완료")
            true
        } catch (e: Exception) { Log.w(TAG, "enroll 실패: ${e.message}"); false }
        finally { conn.disconnect() }
    }

    @Synchronized
    fun refresh(ctx: Context): String? {
        val id = Prefs.deviceId(ctx).ifBlank { return null }
        val secret = Prefs.deviceSecret(ctx).ifBlank { return null }
        val body = JSONObject().put("deviceId", id).put("deviceSecret", secret).toString()
        val conn = (URL("$BASE/api/v1/device-auth/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) { Log.w(TAG, "토큰 발급 HTTP ${conn.responseCode}"); return null }
            val d = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("data")
            token = d.getString("accessToken")
            expiryMs = System.currentTimeMillis() + d.optInt("expiresIn", 900) * 1000L
            token
        } catch (e: Exception) { Log.w(TAG, "토큰 발급 실패: ${e.message}"); null }
        finally { conn.disconnect() }
    }
}
