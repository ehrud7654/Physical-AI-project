package com.ssafy.arpose

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 업로드 대기열.
 *
 * 별도의 상태 파일을 두지 않는다. **디스크 자체가 큐다.**
 * 녹화가 끝나면 summary.txt 가 쓰이므로, summary.txt 가 있는 rec_ 폴더 = 전송 대기.
 * 업로드에 성공하면 폴더를 지우므로 큐에서 자연히 빠진다.
 *
 * 상태 파일을 따로 관리하면 파일과 상태가 어긋나는 경우를 또 처리해야 한다.
 * 폴더의 존재 자체를 상태로 쓰면 그런 불일치가 생기지 않는다.
 */
object UploadQueue {

    private const val WORK_NAME = "arpose-upload"

    /** 전송 대기 중인 녹화 폴더. 녹화가 끝나지 않은 폴더(summary.txt 없음)는 제외한다. */
    fun pending(ctx: Context): List<File> {
        val base = ctx.getExternalFilesDir(null) ?: return emptyList()
        return base.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.filter { File(it, "summary.txt").exists() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun pendingCount(ctx: Context): Int = pending(ctx).size

    /**
     * 업로드를 예약한다. 촬영은 이 호출을 기다리지 않는다.
     *
     * 이미 예약된 작업이 있으면 그대로 둔다(KEEP). 매 녹화마다 새 작업을 쌓지 않고,
     * 하나의 작업이 대기열을 통째로 훑는 구조다.
     */
    fun enqueue(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    // 에피소드 하나가 30초 기준 약 43MB. 셀룰러로 올리면 요금이 터진다.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req)
    }
}
