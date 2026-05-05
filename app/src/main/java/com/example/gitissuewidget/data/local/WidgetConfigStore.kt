package com.example.gitissuewidget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.WidgetConfig
import kotlinx.coroutines.flow.first

private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_configs")

class WidgetConfigStore(context: Context) {

    private val dataStore = context.applicationContext.widgetConfigDataStore

    suspend fun saveConfig(config: WidgetConfig) {
        dataStore.edit { prefs ->
            prefs[repoKey(config.appWidgetId)] = config.repoRef.fullName
            prefs[stateKey(config.appWidgetId)] = config.stateFilter.name
            prefs[labelsKey(config.appWidgetId)] = config.labels.toSet()
            if (!config.assigneeLogin.isNullOrBlank()) {
                prefs[assigneeKey(config.appWidgetId)] = config.assigneeLogin
            } else {
                prefs.remove(assigneeKey(config.appWidgetId))
            }
        }
    }

    suspend fun getConfig(appWidgetId: Int): WidgetConfig? {
        val prefs = dataStore.data.first()
        val repo = prefs[repoKey(appWidgetId)]?.let(RepoRef::parse) ?: return null
        val state = prefs[stateKey(appWidgetId)]
            ?.let { runCatching { IssueFilter.StateFilter.valueOf(it) }.getOrNull() }
            ?: IssueFilter.StateFilter.OPEN
        val labels = prefs[labelsKey(appWidgetId)]?.toList().orEmpty()
        val assignee = prefs[assigneeKey(appWidgetId)]
        return WidgetConfig(
            appWidgetId = appWidgetId,
            repoRef = repo,
            stateFilter = state,
            labels = labels,
            assigneeLogin = assignee,
        )
    }

    suspend fun deleteConfig(appWidgetId: Int) {
        dataStore.edit { prefs ->
            prefs.remove(repoKey(appWidgetId))
            prefs.remove(stateKey(appWidgetId))
            prefs.remove(labelsKey(appWidgetId))
            prefs.remove(assigneeKey(appWidgetId))
        }
    }

    private fun repoKey(id: Int) = stringPreferencesKey("widget_${id}_repo")
    private fun stateKey(id: Int) = stringPreferencesKey("widget_${id}_state")
    private fun labelsKey(id: Int) = stringSetPreferencesKey("widget_${id}_labels")
    private fun assigneeKey(id: Int) = stringPreferencesKey("widget_${id}_assignee")
}
