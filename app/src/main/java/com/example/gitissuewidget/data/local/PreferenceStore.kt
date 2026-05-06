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
import com.example.gitissuewidget.domain.SwipeAction
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
    val showDueDate: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_DUE_DATE] ?: true }

    /**
     * 期限日が近付いたときに「あと〇日」警告を出す閾値（日数）。
     * 残日数がこの値未満（0 以上）の場合に赤文字の警告を表示する。
     * 0 を指定すると警告を出さない扱いになる（残日数 0 未満＝過去日付は別表現）。
     */
    val dueDateWarningDays: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_DUE_DATE_WARNING_DAYS] ?: DEFAULT_DUE_DATE_WARNING_DAYS
    }

    val leftSwipeAction: Flow<SwipeAction> = dataStore.data.map { prefs ->
        prefs[KEY_LEFT_SWIPE]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
            ?: SwipeAction.NONE
    }
    val rightSwipeAction: Flow<SwipeAction> = dataStore.data.map { prefs ->
        prefs[KEY_RIGHT_SWIPE]?.let { runCatching { SwipeAction.valueOf(it) }.getOrNull() }
            ?: SwipeAction.NONE
    }

    /** スワイプアクション (PENDING/COMPLETE) で Issue を移動させる Project のタイトル。viewer 所有。 */
    val swipeProjectTitle: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SWIPE_PROJECT_TITLE]?.takeIf { it.isNotBlank() }
    }

    /** PENDING スワイプ時に移動するカラム名（Project の Status SingleSelect オプション名）。 */
    val pendingColumnName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PENDING_COLUMN_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_PENDING_COLUMN
    }

    /** COMPLETE スワイプ時に移動するカラム名。 */
    val doneColumnName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DONE_COLUMN_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_DONE_COLUMN
    }

    /**
     * Projects v2 の Date 型カスタムフィールドの名前。表示・ソートで Issue の期限として参照される。
     * デフォルト [DEFAULT_DUE_DATE_FIELD] = "Due Date"。
     */
    val dueDateFieldName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DUE_DATE_FIELD]?.takeIf { it.isNotBlank() } ?: DEFAULT_DUE_DATE_FIELD
    }

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

    suspend fun setShowDueDate(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_DUE_DATE] = value }
    }

    suspend fun setDueDateWarningDays(value: Int) {
        dataStore.edit { it[KEY_DUE_DATE_WARNING_DAYS] = value.coerceAtLeast(0) }
    }

    suspend fun setLeftSwipeAction(value: SwipeAction) {
        dataStore.edit { it[KEY_LEFT_SWIPE] = value.name }
    }

    suspend fun setRightSwipeAction(value: SwipeAction) {
        dataStore.edit { it[KEY_RIGHT_SWIPE] = value.name }
    }

    suspend fun setSwipeProjectTitle(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_SWIPE_PROJECT_TITLE)
            else prefs[KEY_SWIPE_PROJECT_TITLE] = value.trim()
        }
    }

    suspend fun setPendingColumnName(value: String) {
        dataStore.edit { it[KEY_PENDING_COLUMN_NAME] = value.trim().ifBlank { DEFAULT_PENDING_COLUMN } }
    }

    suspend fun setDoneColumnName(value: String) {
        dataStore.edit { it[KEY_DONE_COLUMN_NAME] = value.trim().ifBlank { DEFAULT_DONE_COLUMN } }
    }

    suspend fun setDueDateFieldName(value: String) {
        dataStore.edit { it[KEY_DUE_DATE_FIELD] = value.trim().ifBlank { DEFAULT_DUE_DATE_FIELD } }
    }

    companion object {
        private val KEY_SORT_OPTION = stringPreferencesKey("sort_option")
        private val KEY_SORT_DIRECTION = stringPreferencesKey("sort_direction")
        private val KEY_PER_PAGE = intPreferencesKey("per_page")
        private val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval_minutes")
        private val KEY_WATCHED_REPOS = stringSetPreferencesKey("watched_repos")
        private val KEY_SHOW_OPEN_BADGE = booleanPreferencesKey("show_open_badge")
        private val KEY_SHOW_LABELS = booleanPreferencesKey("show_labels")
        private val KEY_SHOW_DUE_DATE = booleanPreferencesKey("show_due_date")
        private val KEY_DUE_DATE_WARNING_DAYS = intPreferencesKey("due_date_warning_days")
        private val KEY_LEFT_SWIPE = stringPreferencesKey("left_swipe_action")
        private val KEY_RIGHT_SWIPE = stringPreferencesKey("right_swipe_action")
        private val KEY_SWIPE_PROJECT_TITLE = stringPreferencesKey("swipe_project_title")
        private val KEY_PENDING_COLUMN_NAME = stringPreferencesKey("pending_column_name")
        private val KEY_DONE_COLUMN_NAME = stringPreferencesKey("done_column_name")
        private val KEY_DUE_DATE_FIELD = stringPreferencesKey("due_date_field_name")

        const val DEFAULT_PER_PAGE = 20
        const val DEFAULT_REFRESH_INTERVAL = 30
        const val DEFAULT_PENDING_COLUMN = "Pending"
        const val DEFAULT_DONE_COLUMN = "Done"
        const val DEFAULT_DUE_DATE_FIELD = "Due Date"
        const val DEFAULT_DUE_DATE_WARNING_DAYS = 3
    }
}
