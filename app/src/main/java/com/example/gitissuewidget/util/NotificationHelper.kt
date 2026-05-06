package com.example.gitissuewidget.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gitissuewidget.MainActivity

/**
 * 端末通知の汎用ヘルパ。
 *
 * - チャンネル作成 ([ensureChannel])
 * - 通知ポスト ([post])
 * - 通知 ID 定数 ([ID_DUE_DATE] / [ID_TEST])
 *
 * 期限通知用とデバッグ用テスト通知の両方からこの post を呼ぶ。
 * Android 13+ で `POST_NOTIFICATIONS` が拒否されている場合、通知は単に表示されない（戻り値で通知）。
 */
object NotificationHelper {

    /** 既定の通知チャンネル ID（期限通知 + デバッグ通知共通）。 */
    const val CHANNEL_ID = "git_issue_widget_default"
    private const val CHANNEL_NAME = "Issue 通知"
    private const val CHANNEL_DESCRIPTION = "Issue の期限通知やテスト通知などを表示します"

    const val ID_DUE_DATE = 1001
    const val ID_TEST = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = CHANNEL_DESCRIPTION
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 通知をポストする。
     * - チャンネルは未作成なら自動で作る
     * - Android 13+ で `POST_NOTIFICATIONS` が無い場合は何もせず false を返す
     * - タップで [MainActivity] を開く PendingIntent を付与
     */
    fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
    ): Boolean {
        ensureChannel(context)

        if (!hasPostPermission(context)) return false

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    /** Android 13+ の runtime permission 判定。それ未満では常に true。 */
    fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
