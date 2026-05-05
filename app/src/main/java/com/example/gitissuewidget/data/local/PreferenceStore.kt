package com.example.gitissuewidget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class PreferenceStore(context: Context) {

    private val dataStore = context.applicationContext.preferencesDataStore

    val sortOption: Flow<SortOption> = dataStore.data.map { prefs ->
        prefs[KEY_SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
            ?: SortOption.UPDATED
    }

    val sortDirection: Flow<SortDirection> = dataStore.data.map { prefs ->
        prefs[KEY_SORT_DIRECTION]?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
            ?: SortDirection.DESC
    }

    val perPage: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_PER_PAGE] ?: DEFAULT_PER_PAGE
    }

    val refreshIntervalMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_INTERVAL] ?: DEFAULT_REFRESH_INTERVAL
    }

    val watchedRepos: Flow<List<RepoRef>> = dataStore.data.map { prefs ->
        prefs[KEY_WATCHED_REPOS]
            ?.mapNotNull { RepoRef.parse(it) }
            ?: emptyList()
    }

    val showOpenBadge: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_OPEN_BADGE] ?: true }
    val showLabels: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_LABELS] ?: true }

    suspend fun setSortOption(value: SortOption) {
        dataStore.edit { it[KEY_SORT_OPTION] = value.name }
    }

    suspend fun setSortDirection(value: SortDirection) {
        dataStore.edit { it[KEY_SORT_DIRECTION] = value.name }
    }

    suspend fun setPerPage(value: Int) {
        dataStore.edit { it[KEY_PER_PAGE] = value }
    }

    suspend fun setRefreshIntervalMinutes(value: Int) {
        dataStore.edit { it[KEY_REFRESH_INTERVAL] = value }
    }

    suspend fun addRepo(repo: RepoRef) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_WATCHED_REPOS].orEmpty().toMutableSet()
            current += repo.fullName
            prefs[KEY_WATCHED_REPOS] = current
        }
    }

    suspend fun removeRepo(repo: RepoRef) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_WATCHED_REPOS].orEmpty().toMutableSet()
            current -= repo.fullName
            prefs[KEY_WATCHED_REPOS] = current
        }
    }

    suspend fun setShowOpenBadge(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_OPEN_BADGE] = value }
    }

    suspend fun setShowLabels(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_LABELS] = value }
    }

    companion object {
        private val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
        private val KEY_SORT_DIRECTION = stringPreferencesKey("sort_direction")
        private val KEY_PER_PAGE = intPreferencesKey("per_page")
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        private val KEY_WATCHED_REPOS = stringSetPreferencesKey("watched_repos")
        private val KEY_SHOW_OPEN_BADGE = booleanPreferencesKey("show_open_badge")
        private val KEY_SHOW_LABELS = booleanPreferencesKey("show_labels")

        const val DEFAULT_PER_PAGE = 20
        const val DEFAULT_REFRESH_INTERVAL = 30
    }
}
