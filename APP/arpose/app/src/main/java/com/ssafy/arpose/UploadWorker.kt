package com.ssafy.arpose

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID

/**
 * 대기 중인 에피소드를 presigned S3 3-step 으로 업로드한다 (EPISODE-UPLOAD-01/02, S15P21A103-162).
 *   ① 세션 발급 → ② S3 PUT → ③ 완료 통지.
 * 멱등키·sessionId 는 폴더에 영속해 재시도에도 동일. 서버 READY 확인 전 원본(rec 폴더)은 지우지 않는다.
 * base URL 은 BuildConfig(디버그=목 127.0.0.1:8000, adb reverse), 토큰은 스텁(C 연동 전).
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        private const val TAG = "UPLOAD"
    }

    override fun doWork(): Result {
        val ctx = applicationContext
        val zipDir = File(ctx.cacheDir, "upload").apply { mkdirs() }
        val queue = UploadQueue.pending(ctx).filter { !File(it, ".uploaded").exists() }
        if (queue.isEmpty()) return Result.success()

        Log.i(TAG, "대기 ${queue.size}건 처리 시작")
        var needRetry = false

        for (dir in queue) {
            if (isStopped) return Result.retry()
            try {
                if (!uploadOne(ctx, dir, zipDir)) needRetry = true
            } catch (e: Exception) {
                Log.e(TAG, "${dir.name} 업로드 예외", e); needRetry = true
            }
        }
        return if (needRetry) Result.retry() else Result.success()
    }

    private fun uploadOne(ctx: Context, dir: File, zipDir: File): Boolean {
        // 에피소드 식별자: 없으면(옛 촬영) 폴백 생성
        val sessionId = EpisodePackager.readSessionId(dir) ?: UUID.randomUUID().toString().lowercase()
            .also { File(dir, "session_id.txt").writeText(it) }

        // API 1.5.0: 업로드는 TrainingJob 과 분리. 서버가 metadata.skillKey 로 스킬 확정 → jobId 불필요.

        // zip + 세션 요청 metadata·summary (계약 6.1). endedAt 은 metadata 안에 있어 재시도에도 동일.
        val pkg = EpisodePackager.pack(dir, sessionId, zipDir) ?: return false
        // deviceIdentifier 주입 안 함: 서버가 토큰으로 장치 판별, metadata.device 는 {} 로 나감 (BE 계약 조정).
        // 전송 때 고른 스킬(.upload_skill)이 있으면 촬영 때 값보다 우선.
        File(dir, ".upload_skill").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { pkg.metadata.put("skillKey", it) }
        val zip = pkg.zip
        val size = zip.length()
        val sha = Uploader.sha256Hex(zip)
        val idemUp = idem(dir, "up")
        val idemDone = idem(dir, "done")

        val sess = Uploader.requestSession(ctx, sessionId, size, sha, idemUp, pkg.metadata, pkg.summary) ?: return false
        val etag = Uploader.putS3(sess.uploadUrl, zip, sess.requiredHeaders) ?: return false
        val ok = Uploader.complete(ctx, sess.uploadSessionId, sessionId, size, sha, idemDone)
        if (ok) {
            File(dir, ".uploaded").writeText("${sess.uploadSessionId} $etag")
            zip.delete()   // 캐시 zip 은 지워도 됨. rec 원본은 서버 READY 확인 전 보존.
            Log.i(TAG, "${dir.name} 업로드 완료 (sessionId=$sessionId, skillKey=${pkg.metadata.optString("skillKey")})")
        }
        return ok
    }

    /** 멱등키를 폴더에 영속 — 재시도 시 동일 키 재사용. */
    private fun idem(dir: File, kind: String): String {
        val f = File(dir, "idem_$kind.txt")
        return if (f.exists()) f.readText().trim()
        else UUID.randomUUID().toString().also { f.writeText(it) }
    }
}
