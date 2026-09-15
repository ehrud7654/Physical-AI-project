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

    /**
     * 목적지가 로컬이면 네트워크 조건을 걸지 않는다.
     *
     * 목 서버는 adb reverse 로 USB 터널을 타므로 폰의 네트워크를 전혀 쓰지 않는다.
     * 여기에 Wi-Fi 조건을 걸면 셀룰러만 있는 환경에서 워커가 영영 안 돈다.
     *
     * 실제 서버(HTTPS)로 바뀌면 자동으로 Wi-Fi 전용이 된다. 에피소드 하나가 30초
     * 기준 약 43MB(실측 분당 85MB)라 셀룰러로 올리면 데이터 요금이 터진다.
     */
    private fun networkType(): NetworkType {
        val url = Uploader.BASE_URL
        val local = url.contains("127.0.0.1") || url.contains("localhost") || url.contains("10.0.2.2")
        return when {
            local -> NetworkType.NOT_REQUIRED
            BuildConfig.DEBUG -> NetworkType.CONNECTED   // 디버그 테스트: 셀룰러 포함 아무 네트워크 허용
            else -> NetworkType.UNMETERED                // 릴리즈: Wi-Fi 전용(데이터 요금 보호)
        }
    }

    /**
     * 전송 대기 = 사용자가 기록에서 [전송]으로 표시(.queued)했고 아직 안 올라간(!.uploaded) 폴더.
     * 촬영만으로 자동 전송하지 않는다(검토 후 전송, S15P21A103-165).
     */
    fun pending(ctx: Context): List<File> {
        val base = ctx.getExternalFilesDir(null) ?: return emptyList()
        return base.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.filter {
                File(it, "summary.txt").exists() &&
                    File(it, ".queued").exists() && !File(it, ".uploaded").exists()
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun pendingCount(ctx: Context): Int = pending(ctx).size

    /**
     * 업로드를 예약한다. 촬영은 이 호출을 기다리지 않는다.
     *
     * REPLACE: 매 호출마다 대기열을 새로 훑는 작업으로 교체한다. 워커가 멱등이라
     * (.uploaded 마커·폴더별 멱등키·이어올리기) 진행 중 교체돼도 안전하고, 이전 실패가
     * 백오프로 물려 새 작업이 안 도는 문제(KEEP)를 피한다.
     */
    fun enqueue(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType())
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
