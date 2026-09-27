package com.a103.collect

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * 세션 폴더(6파일)를 presigned S3 3단계로 업로드한다 — ARpose 계약·흐름 그대로.
 *   ① POST /api/v1/episode/data-upload-request  (세션 발급, metadata·summary)
 *   ② PUT  presigned S3 URL                     (zip 본문 + requiredHeaders)
 *   ③ POST .../{uploadSessionId}/complete        (완료 통지)
 * 인증 = Device Access Token(Bearer). Idempotency-Key 로 재시도 멱등.
 */
object Uploader {
    private const val TAG = "UPLOAD"
    private val BASE = BuildConfig.UPLOAD_BASE_URL
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    /** 세션 폴더를 STORED(무압축) zip 으로. 이미 있으면 재사용. 프레임은 이미 H.264 라 재압축 무의미. */
    fun zipSession(dir: File, sessionId: String, outDir: File): File {
        val zip = File(outDir, "$sessionId.zip")
        if (zip.exists() && zip.length() > 0) return zip
        val tmp = File(outDir, "$sessionId.zip.part")
        ZipOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") && it.extension != "part" }.forEach { f ->
                val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                val bytes = f.readBytes()
                val entry = ZipEntry(rel).apply {
                    method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                }
                zos.putNextEntry(entry); zos.write(bytes); zos.closeEntry()
            }
        }
        if (zip.exists()) zip.delete()
        tmp.renameTo(zip)
        return zip
    }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16); var n: Int
            while (ins.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class Session(val uploadSessionId: String, val uploadUrl: String, val requiredHeaders: Map<String, String>)

    private fun bearer(ctx: Context) =
        DeviceAuth.token(ctx)?.let { "Bearer $it" } ?: "Bearer stub-${Prefs.deviceId(ctx).ifBlank { "unknown" }}"

    /** ① 업로드 세션 발급. */
    fun requestSession(
        ctx: Context, sessionId: String, sizeBytes: Long, sha256: String, idemKey: String,
        metadata: JSONObject, summary: JSONObject
    ): Session? {
        val body = JSONObject()
            .put("sessionId", sessionId).put("contentType", "application/zip")
            .put("sizeBytes", sizeBytes).put("sha256", sha256)
            .put("metadata", metadata).put("summary", summary).toString()
        val conn = (URL("$BASE/api/v1/episode/data-upload-request").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", bearer(ctx))
            setRequestProperty("Idempotency-Key", idemKey)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 401) { DeviceAuth.refresh(ctx); Log.w(TAG, "session 401 → 토큰 갱신 후 재시도"); return null }
            if (code !in 200..299) {
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
                Log.w(TAG, "session 발급 HTTP $code body=$err"); return null
            }
            val d = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("data")
            val hdrs = mutableMapOf<String, String>()
            d.optJSONObject("requiredHeaders")?.let { rh -> rh.keys().forEach { k -> hdrs[k] = rh.getString(k) } }
            if (!hdrs.containsKey("Content-Type")) hdrs["Content-Type"] = "application/zip"
            Session(d.getString("uploadSessionId"), d.getString("uploadUrl"), hdrs)
        } catch (e: Exception) { Log.w(TAG, "session 발급 실패: ${e.message}"); null }
        finally { conn.disconnect() }
    }

    /** ② S3 presigned PUT. requiredHeaders 를 이름·값 그대로 실어야 한다. 성공 시 ETag. */
    fun putS3(uploadUrl: String, zip: File, requiredHeaders: Map<String, String>): String? {
        val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Expect", "100-continue")
            requiredHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            setFixedLengthStreamingMode(zip.length())
        }
        return try {
            BufferedOutputStream(conn.outputStream, 1 shl 16).use { out -> zip.inputStream().use { it.copyTo(out) } }
            val code = conn.responseCode
            if (code !in 200..299) {
                val u = URL(uploadUrl)
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
                Log.w(TAG, "S3 PUT HTTP $code url=${u.host}${u.path} body=$err"); return null
            }
            conn.getHeaderField("ETag") ?: "no-etag"
        } catch (e: Exception) { Log.w(TAG, "S3 PUT 실패: ${e.message}"); null }
        finally { conn.disconnect() }
    }

    /** ③ 완료 통지. 200/202 이면 true. */
    fun complete(ctx: Context, uploadSessionId: String, sessionId: String, sizeBytes: Long, sha256: String, idemKey: String): Boolean {
        val body = JSONObject().put("sessionId", sessionId).put("sizeBytes", sizeBytes).put("sha256", sha256).toString()
        val conn = (URL("$BASE/api/v1/episode/data-upload-request/$uploadSessionId/complete").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", bearer(ctx))
            setRequestProperty("Idempotency-Key", idemKey)
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 401) { DeviceAuth.refresh(ctx); Log.w(TAG, "complete 401 → 토큰 갱신"); return false }
            val resp = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText()
            } catch (e: Exception) { null }.orEmpty()
            Log.i(TAG, "complete HTTP $code body=$resp")
            if (code !in 200..299) return false
            // 2xx 라도 검증 상태를 본문에서 확인 — INVALID/FAILED 면 전송 성공 아님(서버가 상태만 담아 2xx 반환).
            val status = runCatching {
                val o = JSONObject(resp)
                (o.optJSONObject("data")?.optString("status").takeUnless { it.isNullOrBlank() }
                    ?: o.optString("status"))
            }.getOrNull().orEmpty().uppercase()
            if (Regex("FAIL|INVALID|REJECT|ERROR").containsMatchIn(status)) {
                Log.w(TAG, "complete 상태=$status → 검증 실패"); return false
            }
            true
        } catch (e: Exception) { Log.w(TAG, "complete 실패: ${e.message}"); false }
        finally { conn.disconnect() }
    }
}
