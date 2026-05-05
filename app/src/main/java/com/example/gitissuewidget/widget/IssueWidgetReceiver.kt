package com.example.gitissuewidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.gitissuewidget.IssueWidgetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IssueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IssueGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        val container = (context.applicationContext as IssueWidgetApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    container.widgetConfigStore.deleteConfig(id)
                    container.issueCacheStore.clear(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
