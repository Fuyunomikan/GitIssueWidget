package com.example.gitissuewidget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gitissuewidget.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * 設定画面の DebugOption「通知テスト」から起動されるワーカー。
 *
 * [trigger] 呼び出しから [delaySeconds] 秒後に通知を 1 件ポストする。
 * 通知システム（チャンネル作成・runtime permission・PendingIntent）の疎通確認に使う。
 */
class TestNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.post(
            context = applicationContext,
            id = NotificationHelper.ID_TEST,
            title = "通知テスト",
            body = "通知システムが正常に動作しています",
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "test_notification_worker"

        fun trigger(context: Context, delaySeconds: Long = 5) {
            val request = OneTimeWorkRequestBuilder<TestNotificationWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
