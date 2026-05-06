package com.example.gitissuewidget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.local.TokenStore
import com.example.gitissuewidget.data.repo.IssueRepository
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
import com.example.gitissuewidget.domain.SwipeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val tokenSaved: Boolean = false,
    val watchedRepos: List<RepoRef> = emptyList(),
    val sortOption: SortOption = SortOption.UPDATED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val perPage: Int = PreferenceStore.DEFAULT_PER_PAGE,
    val showOpenBadge: Boolean = true,
    val showLabels: Boolean = true,
    val leftSwipeAction: SwipeAction = SwipeAction.NONE,
    val rightSwipeAction: SwipeAction = SwipeAction.NONE,
    val swipeProjectTitle: String = "",
    val pendingColumnName: String = PreferenceStore.DEFAULT_PENDING_COLUMN,
    val doneColumnName: String = PreferenceStore.DEFAULT_DONE_COLUMN,
    val dueDateFieldName: String = PreferenceStore.DEFAULT_DUE_DATE_FIELD,
)

private data class BasicPrefs(
    val tokenSaved: Boolean,
    val watchedRepos: List<RepoRef>,
    val sortOption: SortOption,
    val sortDirection: SortDirection,
    val perPage: Int,
)

private data class DisplayPrefs(
    val showOpenBadge: Boolean,
    val showLabels: Boolean,
)

private data class SwipePrefs(
    val left: SwipeAction,
    val right: SwipeAction,
)

private data class ProjectPrefs(
    val swipeProjectTitle: String,
    val pendingColumnName: String,
    val doneColumnName: String,
    val dueDateFieldName: String,
)

class SettingsViewModel(
    private val tokenStore: TokenStore,
    private val preferenceStore: PreferenceStore,
    private val issueRepository: IssueRepository,
) : ViewModel() {

    private val tokenSaved = MutableStateFlow(tokenStore.hasToken())

    private val basicFlow = combine(
        tokenSaved,
        preferenceStore.watchedRepos,
        preferenceStore.sortOption,
        preferenceStore.sortDirection,
        preferenceStore.perPage,
    ) { saved, repos, sort, direction, perPage ->
        BasicPrefs(saved, repos, sort, direction, perPage)
    }

    private val displayFlow = combine(
        preferenceStore.showOpenBadge,
        preferenceStore.showLabels,
    ) { open, labels -> DisplayPrefs(open, labels) }

    private val swipeFlow = combine(
        preferenceStore.leftSwipeAction,
        preferenceStore.rightSwipeAction,
    ) { left, right -> SwipePrefs(left, right) }

    private val projectFlow = combine(
        preferenceStore.swipeProjectTitle,
        preferenceStore.pendingColumnName,
        preferenceStore.doneColumnName,
        preferenceStore.dueDateFieldName,
    ) { title, pending, done, dueField ->
        ProjectPrefs(title.orEmpty(), pending, done, dueField)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        basicFlow,
        displayFlow,
        swipeFlow,
        projectFlow,
    ) { basic, display, swipe, project ->
        SettingsUiState(
            tokenSaved = basic.tokenSaved,
            watchedRepos = basic.watchedRepos,
            sortOption = basic.sortOption,
            sortDirection = basic.sortDirection,
            perPage = basic.perPage,
            showOpenBadge = display.showOpenBadge,
            showLabels = display.showLabels,
            leftSwipeAction = swipe.left,
            rightSwipeAction = swipe.right,
            swipeProjectTitle = project.swipeProjectTitle,
            pendingColumnName = project.pendingColumnName,
            doneColumnName = project.doneColumnName,
            dueDateFieldName = project.dueDateFieldName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** GraphQL で取得済みの viewer Project タイトル一覧。null = 未取得。 */
    private val _availableProjectTitles = MutableStateFlow<List<String>?>(null)
    val availableProjectTitles: StateFlow<List<String>?> = _availableProjectTitles.asStateFlow()

    private val _projectsLoading = MutableStateFlow(false)
    val projectsLoading: StateFlow<Boolean> = _projectsLoading.asStateFlow()

    fun saveToken(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            _message.value = "PATを入力してください"
            return
        }
        tokenStore.saveToken(trimmed)
        tokenSaved.value = true
        _message.value = "PATを保存しました"
    }

    fun clearToken() {
        tokenStore.clearToken()
        tokenSaved.value = false
        _message.value = "PATを削除しました"
    }

    fun addRepo(input: String) {
        val ref = RepoRef.parse(input)
        if (ref == null) {
            _message.value = "owner/repo の形式で入力してください"
            return
        }
        viewModelScope.launch {
            preferenceStore.addRepo(ref)
            _message.value = "${ref.fullName} を追加しました"
        }
    }

    fun removeRepo(repo: RepoRef) {
        viewModelScope.launch {
            preferenceStore.removeRepo(repo)
            _message.value = "${repo.fullName} を削除しました"
        }
    }

    fun setSortOption(value: SortOption) {
        viewModelScope.launch { preferenceStore.setSortOption(value) }
    }

    fun setSortDirection(value: SortDirection) {
        viewModelScope.launch { preferenceStore.setSortDirection(value) }
    }

    fun setPerPage(value: Int) {
        viewModelScope.launch { preferenceStore.setPerPage(value) }
    }

    fun setShowOpenBadge(value: Boolean) {
        viewModelScope.launch { preferenceStore.setShowOpenBadge(value) }
    }

    fun setShowLabels(value: Boolean) {
        viewModelScope.launch { preferenceStore.setShowLabels(value) }
    }

    fun setLeftSwipeAction(value: SwipeAction) {
        viewModelScope.launch { preferenceStore.setLeftSwipeAction(value) }
    }

    fun setRightSwipeAction(value: SwipeAction) {
        viewModelScope.launch { preferenceStore.setRightSwipeAction(value) }
    }

    fun setSwipeProjectTitle(value: String) {
        viewModelScope.launch { preferenceStore.setSwipeProjectTitle(value) }
    }

    fun setPendingColumnName(value: String) {
        viewModelScope.launch { preferenceStore.setPendingColumnName(value) }
    }

    fun setDoneColumnName(value: String) {
        viewModelScope.launch { preferenceStore.setDoneColumnName(value) }
    }

    fun setDueDateFieldName(value: String) {
        viewModelScope.launch { preferenceStore.setDueDateFieldName(value) }
    }

    /**
     * viewer の Projects v2 一覧を GraphQL で取得し、タイトル一覧をUIに反映する。
     * PAT に project スコープが無い場合・Project が無い場合はエラーメッセージを Snackbar に出す。
     */
    fun refreshProjects() {
        if (_projectsLoading.value) return
        viewModelScope.launch {
            _projectsLoading.value = true
            issueRepository.listAvailableProjects(forceRefresh = true)
                .onSuccess { metas ->
                    _availableProjectTitles.value = metas.map { it.project.title }
                    _message.value = if (metas.isEmpty()) {
                        "Project が見つかりませんでした (PAT に project スコープがあるか確認してください)"
                    } else {
                        "Project ${metas.size} 件を取得しました"
                    }
                }
                .onFailure { e ->
                    _message.value = "Project 取得エラー: ${e.message ?: "不明"}"
                }
            _projectsLoading.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IssueWidgetApp
                SettingsViewModel(
                    tokenStore = app.container.tokenStore,
                    preferenceStore = app.container.preferenceStore,
                    issueRepository = app.container.issueRepository,
                )
            }
        }
    }
}
