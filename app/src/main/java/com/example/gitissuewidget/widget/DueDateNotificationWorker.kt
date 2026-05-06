package com.example.gitissuewidget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.util.NotificationHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * 期限通知の日次ワーカー。
 *
 * - 起動条件: [com.example.gitissuewidget.data.local.PreferenceStore.notificationEnabled] が true
 * - データソース: Projects v2 の Issue 一覧（`swipeProjectTitle` で指定された Project）
 * - 対象 Issue: `dueDate` が「残日数 0 以上 かつ `dueDateWarningDays` 未満」のもの
 * - 通知本文: 「〇件のIssueの期限まで〇日です」（最短残日数を表示）
 *
 * 自身を再エンキューするセルフスケジューリング方式。WorkManager 周期 work は最短 15 分なので、
 * 「毎朝 8 時」など固定時刻発火を実現するには OneTimeWorkRequest + 翌日同時刻の再スケジュールが必要。
 */
class DueDateNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IssueWidgetApp).container
        val prefs = container.preferenceStore

        try {
            val enabled = prefs.notificationEnabled.first()
            if (!enabled) return Result.success()

            val tokenSet = container.tokenStore.hasToken()
            val projectTitle = prefs.swipeProjectTitle.first().orEmpty()
            // 残日数 < warningDays で通知対象。0 だと通知が一切出ないので最低 1 に丸める。
            val warningDays = prefs.dueDateWarningDays.first().coerceAtLeast(1)
            val dueDateFieldName = prefs.dueDateFieldName.first()

            // PAT 未設定 / Project 未指定だと期限を引けないので、エラーにせず黙ってスキップ。
            if (!tokenSet || projectTitle.isBlank()) return Result.success()

            val meta = container.issueRepository
                .resolveProjectByTitle(projectTitle)
                .getOrElse { return Result.success() }

            val issues = container.issueRepository.fetchProjectIssues(
                projectMeta = meta,
                columnName = null,
                perPage = 100,
                dueDateFieldName = dueDateFieldName,
                applyTakeLimit = false,
            ).getOrElse { return Result.success() }

            val today = LocalDate.now()
            val approachingDays: List<Int> = issues.mapNotNull { issue ->
                val due = issue.dueDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@mapNotNull null
                // 過去日（負）は除外、警告閾値未満のみ通知対象。
                val days = ChronoUnit.DAYS.between(today, due).toInt()
                if (days in 0 until warningDays) days else null
            }

            if (approachingDays.isNotEmpty()) {
                val count = approachingDays.size
                val minDays = approachingDays.min()
                NotificationHelper.post(
                    context = applicationContext,
                    id = NotificationHelper.ID_DUE_DATE,
                    title = "Issue 期限のお知らせ",
                    body = "${count}件のIssueの期限まで${minDays}日です",
                )
            }

            return Result.success()
        } finally {
            // 翌日の発火を必ず再スケジュール（処理の途中で例外が出ても通知系統は止めない）。
            runCatching {
                val hour = prefs.notificationHour.first()
                val minute = prefs.notificationMinute.first()
                val enabled = prefs.notificationEnabled.first()
                if (enabled) schedule(applicationContext, hour, minute)
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "due_date_notification_worker"

        /**
         * 次の [hour]:[minute] 発火を OneTimeWorkRequest としてエンキュー。
         * 既存ワークは [ExistingWorkPolicy.REPLACE] で常に最新の時刻設定に置き換える。
         */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val delayMillis = ChronoUnit.MILLIS.between(now, target).coerceAtLeast(0)

            val request = OneTimeWorkRequestBuilder<DueDateNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
