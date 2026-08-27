package com.ssafy.arpose

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

/**
 * 대기 중인 에피소드를 하나씩 서버로 보낸다.
 *
 * 한 번 실행될 때 대기열 전체를 훑는다. 하나라도 재시도가 필요하면 Result.retry() 를
 * 돌려 WorkManager 가 백오프 후 다시 부르게 한다. 남은 것은 그때 이어서 보낸다.
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    companion object {
        private const val TAG = "UPLOAD"
    }

    override fun doWork(): Result {
        val ctx = applicationContext
        val zipDir = File(ctx.cacheDir, "upload").apply { mkdirs() }
        val queue = UploadQueue.pending(ctx)
        if (queue.isEmpty()) return Result.success()

        Log.i(TAG, "대기 ${queue.size}건 처리 시작")
        var needRetry = false

        for (dir in queue) {
            if (isStopped) return Result.retry()
            val zip = try {
                Uploader.zipEpisode(dir, zipDir)
            } catch (e: Exception) {
                Log.e(TAG, "${dir.name} zip 실패", e)
                needRetry = true; continue
            }
            when (Uploader.upload(ctx, dir.name, zip)) {
                Uploader.Result.SUCCESS -> {
                    zip.delete()
                    // 전송이 확인된 뒤에만 지운다. 순서가 바뀌면 데이터가 사라진다.
                    dir.deleteRecursively()
                    Log.i(TAG, "${dir.name} 전송 완료 - 로컬 삭제")
                }
                Uploader.Result.RETRY -> {
                    needRetry = true
                    // zip 은 남겨둔다. 다음 시도에서 다시 만들지 않아도 된다.
                }
                Uploader.Result.FAILED -> {
                    // 재시도해도 소용없는 요청. 데이터는 지우지 않고 남겨 사람이 보게 한다.
                    zip.delete()
                    Log.e(TAG, "${dir.name} 전송 실패(영구) - 로컬 보존")
                }
            }
        }
        return if (needRetry) Result.retry() else Result.success()
    }
}
