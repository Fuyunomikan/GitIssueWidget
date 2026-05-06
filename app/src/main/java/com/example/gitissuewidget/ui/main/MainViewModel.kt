package com.example.gitissuewidget.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.local.TokenStore
import com.example.gitissuewidget.data.repo.IssueRepository
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
import com.example.gitissuewidget.domain.SwipeAction
import com.example.gitissuewidget.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MainUiState(
    val tokenSet: Boolean = false,
    val user: User? = null,
    val issues: List<Issue> = emptyList(),
    val watchedRepos: List<RepoRef> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val leftSwipeAction: SwipeAction = SwipeAction.NONE,
    val rightSwipeAction: SwipeAction = SwipeAction.NONE,
    /** Project 未設定時にスワイプされた際に表示する警告メッセージ。null = 非表示。 */
    val swipeProjectMissing: String? = null,
    /** Open / Closed タブの選択状態。ALL は使用しない。 */
    val selectedStateTab: IssueFilter.StateFilter = IssueFilter.StateFilter.OPEN,
    /** 期限 (DueDate) を Issue 行に表示するかの設定値。 */
    val showDueDate: Boolean = true,
    /** 期限警告を出す残日数閾値。残日数 < この値（かつ 0 以上）で「あと〇日」を赤表示。 */
    val dueDateWarningDays: Int = PreferenceStore.DEFAULT_DUE_DATE_WARNING_DAYS,
)

class MainViewModel(
    private val tokenStore: TokenStore,
    private val preferenceStore: PreferenceStore,
    private val issueRepository: IssueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(tokenSet = tokenStore.hasToken()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceStore.leftSwipeAction.collect { value ->
                _uiState.value = _uiState.value.copy(leftSwipeAction = value)
            }
        }
        viewModelScope.launch {
            preferenceStore.rightSwipeAction.collect { value ->
                _uiState.value = _uiState.value.copy(rightSwipeAction = value)
            }
        }
        viewModelScope.launch {
            preferenceStore.showDueDate.collect { value ->
                _uiState.value = _uiState.value.copy(showDueDate = value)
            }
        }
        viewModelScope.launch {
            preferenceStore.dueDateWarningDays.collect { value ->
                _uiState.value = _uiState.value.copy(dueDateWarningDays = value)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasToken = tokenStore.hasToken()
            _uiState.value = _uiState.value.copy(
                tokenSet = hasToken,
                loading = true,
                errorMessage = null,
            )

            if (!hasToken) {
                _uiState.value = _uiState.value.copy(loading = false, user = null, issues = emptyList())
                return@launch
            }

            val user = issueRepository.fetchCurrentUser()
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        errorMessage = e.message ?: "ユーザー取得に失敗しました",
                    )
                }
                .getOrNull()

            val repos = preferenceStore.watchedRepos.first()
            val sort = preferenceStore.sortOption.first()
            val direction = preferenceStore.sortDirection.first()
            val perPage = preferenceStore.perPage.first()
            val stateFilter = _uiState.value.selectedStateTab
            val filter = IssueFilter(
                stateFilter = stateFilter,
                sort = sort,
                direction = direction,
                perPage = perPage,
            )

            // Project モードの優先: swipeProjectTitle が設定されていればその Project から取得し、
            // dueDate / Project ベースのソートを有効にする。未設定なら従来どおり REST から先頭リポジトリを取得。
            val projectTitle = preferenceStore.swipeProjectTitle.first()
            val dueDateFieldName = preferenceStore.dueDateFieldName.first()
            val issues = when {
                !projectTitle.isNullOrBlank() -> fetchIssuesViaProject(projectTitle, dueDateFieldName, filter)
                else -> {
                    val firstRepo = repos.firstOrNull()
                    if (firstRepo == null) emptyList()
                    else issueRepository.fetchIssues(firstRepo, filter)
                        .onFailure { e ->
                            _uiState.value = _uiState.value.copy(
                                errorMessage = e.message ?: "Issue取得に失敗しました",
                            )
                        }
                        .getOrDefault(emptyList())
                }
            }

            _uiState.value = _uiState.value.copy(
                user = user,
                watchedRepos = repos,
                issues = issues,
                loading = false,
            )
        }
    }

    /**
     * Project モードの Issue 取得。Project 全カラム横断で取得し、クライアント側でソートする。
     * dueDateFieldName が一致した Date フィールド値は [Issue.dueDate] に入る。
     */
    private suspend fun fetchIssuesViaProject(
        projectTitle: String,
        dueDateFieldName: String,
        filter: IssueFilter,
    ): List<Issue> {
        val meta = issueRepository.resolveProjectByTitle(projectTitle)
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Project 取得に失敗しました",
                )
            }
            .getOrNull() ?: return emptyList()
        val items = issueRepository.fetchProjectIssues(
            projectMeta = meta,
            columnName = null,
            perPage = filter.perPage,
            dueDateFieldName = dueDateFieldName,
            applyTakeLimit = false,
        )
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Project items 取得に失敗しました",
                )
            }
            .getOrDefault(emptyList())
        val stateFiltered = when (filter.stateFilter) {
            IssueFilter.StateFilter.OPEN -> items.filter { it.state == IssueState.OPEN }
            IssueFilter.StateFilter.CLOSED -> items.filter { it.state == IssueState.CLOSED }
            IssueFilter.StateFilter.ALL -> items
        }
        return stateFiltered
            .sortedByOption(filter.sort, filter.direction)
            .take(filter.perPage)
    }

    private fun List<Issue>.sortedByOption(sort: SortOption, direction: SortDirection): List<Issue> {
        val asc = direction == SortDirection.ASC
        if (sort == SortOption.DUE_DATE) {
            val (dated, undated) = partition { !it.dueDate.isNullOrBlank() }
            val datedSorted = dated.sortedBy { it.dueDate }
            return (if (asc) datedSorted else datedSorted.reversed()) + undated
        }
        val sorted = when (sort) {
            SortOption.UPDATED -> sortedBy { it.updatedAt }
            SortOption.CREATED -> sortedBy { it.createdAt }
            SortOption.COMMENTS -> sortedBy { it.commentsCount }
            SortOption.DUE_DATE -> this
        }
        return if (asc) sorted else sorted.reversed()
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Open / Closed タブの切替。同じタブのときは何もしない。変更時は再フェッチする。
     */
    fun setStateTab(state: IssueFilter.StateFilter) {
        if (_uiState.value.selectedStateTab == state) return
        _uiState.value = _uiState.value.copy(selectedStateTab = state)
        refresh()
    }

    fun consumeSwipeProjectMissing() {
        _uiState.value = _uiState.value.copy(swipeProjectMissing = null)
    }

    /**
     * スワイプアクションを実行する。
     *
     * 流れ:
     * 1. 設定画面の `swipeProjectTitle` を取得。空なら警告ダイアログ用 state をセットして中断
     * 2. アクションに応じた対象カラム名（pendingColumnName / doneColumnName）を取得
     * 3. ProjectMetaCache 経由で Project を解決（見つからなければ errorMessage で通知）
     * 4. `IssueRepository.moveIssueToColumn` で Status を更新
     * 5. 完了後 [refresh] で UI を最新化（失敗時も refresh して整合性を保つ）
     */
    fun applySwipeAction(issue: Issue, isLeftSwipe: Boolean) {
        val action = if (isLeftSwipe) _uiState.value.leftSwipeAction else _uiState.value.rightSwipeAction
        if (action == SwipeAction.NONE) return
        viewModelScope.launch {
            val projectTitle = preferenceStore.swipeProjectTitle.first()
            if (projectTitle.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    swipeProjectMissing = "スワイプ用 Project が未設定です。設定画面で Project タイトルを指定してください。",
                )
                refresh()
                return@launch
            }

            val targetColumnName = when (action) {
                SwipeAction.PENDING -> preferenceStore.pendingColumnName.first()
                SwipeAction.COMPLETE -> preferenceStore.doneColumnName.first()
                SwipeAction.NONE -> return@launch
            }

            val metaResult = issueRepository.resolveProjectByTitle(projectTitle)
            val projectMeta = metaResult.getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Project の取得に失敗しました",
                )
                refresh()
                return@launch
            }

            issueRepository.moveIssueToColumn(
                issueNodeId = issue.nodeId,
                projectMeta = projectMeta,
                targetColumnName = targetColumnName,
            ).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "カラム移動に失敗しました",
                )
            }
            refresh()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IssueWidgetApp
                MainViewModel(
                    tokenStore = app.container.tokenStore,
                    preferenceStore = app.container.preferenceStore,
                    issueRepository = app.container.issueRepository,
                )
            }
        }
    }
}
