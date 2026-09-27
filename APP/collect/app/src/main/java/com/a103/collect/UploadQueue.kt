package com.a103.collect

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 업로드 대기열 — 디스크가 곧 큐. 전송 표시(.queued)됐고 아직 안 올라간(!.uploaded) 세션이 대기.
 * 촬영만으로 자동 전송하지 않는다(검토 후 전송, ARpose 흐름 그대로).
 */
object UploadQueue {
    const val WORK_NAME = "collect-upload"

    fun pending(ctx: Context): List<File> {
        val base = File(ctx.getExternalFilesDir(null), "sessions")
        return base.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.filter { File(it, "manifest.json").exists() && File(it, ".queued").exists() && !File(it, ".uploaded").exists() }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun enqueue(ctx: Context) {
        // 네트워크 제약 없음 — 대기 즉시 시도(실패 시 백오프 재시도).
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
