package com.a103.collect

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID

/**
 * 대기 세션을 presigned S3 3단계로 업로드(ARpose 흐름 그대로): 세션 발급 → S3 PUT → 완료 통지.
 * sessionId·멱등키는 폴더에 영속해 재시도에도 동일. 성공 시 .uploaded 표시(원본은 서버 확인 전 보존).
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val zipDir = File(ctx.cacheDir, "upload").apply { mkdirs() }
        val queue = UploadQueue.pending(ctx)
        if (queue.isEmpty()) return Result.success()

        Log.i(TAG, "대기 ${queue.size}건 업로드 시작")
        var needRetry = false
        for (dir in queue) {
            if (isStopped) return Result.retry()
            try { if (!uploadOne(ctx, dir, zipDir)) needRetry = true }
            catch (e: Exception) { Log.e(TAG, "${dir.name} 업로드 예외", e); needRetry = true }
        }
        return if (needRetry) Result.retry() else Result.success()
    }

    private fun uploadOne(ctx: Context, dir: File, zipDir: File): Boolean {
        // sessionId: manifest 에 있음. 없으면(옛 촬영) 폴백 생성해 영속.
        val sessionId = readSessionId(dir) ?: UUID.randomUUID().toString().lowercase()
            .also { File(dir, "session_id.txt").writeText(it) }

        val pkg = SessionPackager.pack(dir, sessionId, zipDir) ?: return false
        val zip = pkg.zip
        val size = zip.length()
        val sha = Uploader.sha256Hex(zip)

        val sess = Uploader.requestSession(ctx, sessionId, size, sha, idem(dir, "up"), pkg.metadata, pkg.summary) ?: return false
        val etag = Uploader.putS3(sess.uploadUrl, zip, sess.requiredHeaders) ?: return false
        val ok = Uploader.complete(ctx, sess.uploadSessionId, sessionId, size, sha, idem(dir, "done"))
        if (ok) {
            File(dir, ".uploaded").writeText("${sess.uploadSessionId} $etag")
            zip.delete()
            Log.i(TAG, "${dir.name} 업로드 완료 (sessionId=$sessionId)")
        }
        return ok
    }

    private fun readSessionId(dir: File): String? {
        File(dir, "session_id.txt").let { if (it.exists()) return it.readText().trim() }
        return try { org.json.JSONObject(File(dir, "manifest.json").readText()).optString("session_id").ifBlank { null } }
        catch (e: Exception) { null }
    }

    private fun idem(dir: File, kind: String): String {
        val f = File(dir, "idem_$kind.txt")
        return if (f.exists()) f.readText().trim() else UUID.randomUUID().toString().also { f.writeText(it) }
    }

    companion object { private const val TAG = "UPLOAD" }
}
