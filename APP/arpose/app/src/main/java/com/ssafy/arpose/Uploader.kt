package com.ssafy.arpose

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 녹화 폴더 하나를 zip 으로 묶어 서버에 올린다.
 *
 * 전송 형식은 multipart 가 아니라 zip 원본을 본문으로 보내고 정보는 헤더에 싣는다.
 * 받는 쪽 구현이 단순해지고, 목 서버와 실제 서버가 같은 규약을 쓸 수 있다.
 *
 *   POST {BASE_URL}/upload
 *   X-Device-Id       : 기기 식별자
 *   X-Episode         : rec_YYYYMMDD_HHmmss
 *   X-Idempotency-Key : {기기ID}_{에피소드}
 *   Content-Type      : application/zip
 *   본문               : zip 바이트
 *
 * 응답 처리
 *   200/201 : 성공. 로컬 삭제
 *   409     : 서버에 이미 있음. 성공과 동일하게 로컬 삭제 (재전송 방지)
 *   4xx     : 고칠 수 없는 요청. 재시도해도 소용없으므로 실패로 확정
 *   5xx/예외 : 일시적. 재시도
 */
object Uploader {

    private const val TAG = "UPLOAD"

    /** 목 서버 기준. adb reverse tcp:8000 tcp:8000 으로 USB 만으로 검증한다. */
    const val BASE_URL = "http://127.0.0.1:8000"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    enum class Result { SUCCESS, RETRY, FAILED }

    fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** 녹화 폴더를 zip 으로 묶는다. 이미 있으면 다시 만들지 않는다. */
    fun zipEpisode(dir: File, outDir: File): File {
        val zip = File(outDir, "${dir.name}.zip")
        if (zip.exists() && zip.length() > 0) return zip
        val tmp = File(outDir, "${dir.name}.zip.part")
        ZipOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { zos ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        // 다 만든 뒤에 이름을 바꾼다. 중간에 죽어도 반쪽 zip 을 올리지 않는다.
        if (zip.exists()) zip.delete()
        tmp.renameTo(zip)
        return zip
    }

    fun upload(ctx: Context, episode: String, zip: File): Result {
        val id = deviceId(ctx)
        val conn = (URL("$BASE_URL/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/zip")
            setRequestProperty("X-Device-Id", id)
            setRequestProperty("X-Episode", episode)
            setRequestProperty("X-Idempotency-Key", "${id}_$episode")
            setFixedLengthStreamingMode(zip.length())
        }
        return try {
            BufferedOutputStream(conn.outputStream, 1 shl 16).use { out ->
                zip.inputStream().use { it.copyTo(out) }
            }
            val code = conn.responseCode
            Log.i(TAG, "$episode -> HTTP $code (${zip.length() / 1024}KB)")
            when {
                code in 200..299 -> Result.SUCCESS
                code == 409 -> Result.SUCCESS          // 이미 있음. 다시 보낼 이유가 없다
                code in 400..499 -> Result.FAILED
                else -> Result.RETRY
            }
        } catch (e: Exception) {
            Log.w(TAG, "$episode 업로드 실패 - 재시도 예정: ${e.message}")
            Result.RETRY
        } finally {
            conn.disconnect()
        }
    }
}
