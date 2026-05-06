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
            prefs[reposKey(config.appWidgetId)] = config.repoRefs.map { it.fullName }.toSet()
            // Remove the legacy single-repo key if it still exists
            prefs.remove(legacyRepoKey(config.appWidgetId))
            prefs[stateKey(config.appWidgetId)] = config.stateFilter.name
            prefs[labelsKey(config.appWidgetId)] = config.labels.toSet()
            if (!config.assigneeLogin.isNullOrBlank()) {
                prefs[assigneeKey(config.appWidgetId)] = config.assigneeLogin
            } else {
                prefs.remove(assigneeKey(config.appWidgetId))
            }
            if (!config.projectTitle.isNullOrBlank()) {
                prefs[projectTitleKey(config.appWidgetId)] = config.projectTitle.trim()
            } else {
                prefs.remove(projectTitleKey(config.appWidgetId))
            }
            if (!config.projectColumnName.isNullOrBlank()) {
                prefs[projectColumnKey(config.appWidgetId)] = config.projectColumnName.trim()
            } else {
                prefs.remove(projectColumnKey(config.appWidgetId))
            }
        }
    }

    suspend fun getConfig(appWidgetId: Int): WidgetConfig? {
        val prefs = dataStore.data.first()
        // Prefer new multi-repo key, fall back to legacy single-repo string for migration
        val repoNames = prefs[reposKey(appWidgetId)]
            ?: prefs[legacyRepoKey(appWidgetId)]?.let { setOf(it) }
        val projectTitle = prefs[projectTitleKey(appWidgetId)]?.takeIf { it.isNotBlank() }
        val projectColumn = prefs[projectColumnKey(appWidgetId)]?.takeIf { it.isNotBlank() }

        // A widget is considered "configured" if it has either repos or a project.
        if (repoNames.isNullOrEmpty() && projectTitle == null) return null

        val repos = repoNames.orEmpty().mapNotNull { RepoRef.parse(it) }
        val state = prefs[stateKey(appWidgetId)]
            ?.let { runCatching { IssueFilter.StateFilter.valueOf(it) }.getOrNull() }
            ?: IssueFilter.StateFilter.OPEN
        val labels = prefs[labelsKey(appWidgetId)]?.toList().orEmpty()
        val assignee = prefs[assigneeKey(appWidgetId)]
        return WidgetConfig(
            appWidgetId = appWidgetId,
            repoRefs = repos,
            stateFilter = state,
            labels = labels,
            assigneeLogin = assignee,
            projectTitle = projectTitle,
            projectColumnName = projectColumn,
        )
    }

    suspend fun deleteConfig(appWidgetId: Int) {
        dataStore.edit { prefs ->
            prefs.remove(reposKey(appWidgetId))
            prefs.remove(legacyRepoKey(appWidgetId))
            prefs.remove(stateKey(appWidgetId))
            prefs.remove(labelsKey(appWidgetId))
            prefs.remove(assigneeKey(appWidgetId))
            prefs.remove(projectTitleKey(appWidgetId))
            prefs.remove(projectColumnKey(appWidgetId))
        }
    }

    private fun reposKey(id: Int) = stringSetPreferencesKey("widget_${id}_repos")
    private fun legacyRepoKey(id: Int) = stringPreferencesKey("widget_${id}_repo")
    private fun stateKey(id: Int) = stringPreferencesKey("widget_${id}_state")
    private fun labelsKey(id: Int) = stringSetPreferencesKey("widget_${id}_labels")
    private fun assigneeKey(id: Int) = stringPreferencesKey("widget_${id}_assignee")
    private fun projectTitleKey(id: Int) = stringPreferencesKey("widget_${id}_project_title")
    private fun projectColumnKey(id: Int) = stringPreferencesKey("widget_${id}_project_column")
}
