package com.ssafy.arpose

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.CRC32
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

    /**
     * 업로드 대상. build.gradle.kts 의 buildConfigField 에서 온다.
     *
     *   debug   : 목 서버 (adb reverse tcp:8000 tcp:8000 으로 USB 터널)
     *   release : 실제 서버
     *
     * 여기 한 곳에서만 정하므로 테스트 빌드가 실서버로 나가는 사고가 없다.
     * 나중에 QR 프로비저닝이 들어오면 이 값을 기본값으로 두고 덮어쓰면 된다.
     */
    val BASE_URL: String = BuildConfig.UPLOAD_BASE_URL

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    enum class Result { SUCCESS, RETRY, FAILED }

    fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /**
     * 녹화 폴더를 zip 으로 묶는다. 이미 있으면 다시 만들지 않는다.
     *
     * 압축률 0(STORED). 프레임이 이미 JPEG 라 재압축은 CPU·시간만 먹고 크기 이득이 없고,
     * STORED 는 받는 쪽이 압축 해제 없이 바이트를 바로 읽을 수 있다(서버 계약).
     * STORED 는 엔트리마다 size·crc 를 미리 채워야 하므로 파일을 한 번 읽어 계산한다.
     */
    fun zipEpisode(dir: File, outDir: File): File {
        val zip = File(outDir, "${dir.name}.zip")
        if (zip.exists() && zip.length() > 0) return zip
        val tmp = File(outDir, "${dir.name}.zip.part")
        ZipOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                val bytes = f.readBytes()
                val crc = CRC32().apply { update(bytes) }.value
                val entry = ZipEntry(rel).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
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
