package com.example.gitissuewidget

import android.app.Application
import com.example.gitissuewidget.util.NotificationHelper
import com.example.gitissuewidget.widget.DueDateNotificationWorker
import com.example.gitissuewidget.widget.IssueRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IssueWidgetApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        IssueRefreshWorker.schedule(this)

        // 通知チャンネルは早めに作っておく（初回通知時の遅延を避ける）。
        NotificationHelper.ensureChannel(this)

        // 端末起動 / アプリ初期化時に、設定が enabled なら次回発火を再エンキューする。
        // DataStore 読み出しは suspend なので別スコープで非同期に行う。
        CoroutineScope(Dispatchers.Default).launch {
            val prefs = container.preferenceStore
            if (prefs.notificationEnabled.first()) {
                val hour = prefs.notificationHour.first()
                val minute = prefs.notificationMinute.first()
                DueDateNotificationWorker.schedule(this@IssueWidgetApp, hour, minute)
            }
        }
    }
}
