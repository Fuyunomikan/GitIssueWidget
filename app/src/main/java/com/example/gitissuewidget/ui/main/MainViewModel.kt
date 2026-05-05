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
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SwipeAction
import com.example.gitissuewidget.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
            val filter = IssueFilter(sort = sort, direction = direction, perPage = perPage)

            val firstRepo = repos.firstOrNull()
            val issues = if (firstRepo == null) {
                emptyList()
            } else {
                issueRepository.fetchIssues(firstRepo, filter)
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = e.message ?: "Issue取得に失敗しました",
                        )
                    }
                    .getOrDefault(emptyList())
            }

            _uiState.value = _uiState.value.copy(
                user = user,
                watchedRepos = repos,
                issues = issues,
                loading = false,
            )
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun applySwipeAction(issue: Issue, isLeftSwipe: Boolean) {
        val action = if (isLeftSwipe) _uiState.value.leftSwipeAction else _uiState.value.rightSwipeAction
        if (action == SwipeAction.NONE) return
        viewModelScope.launch {
            val result = when (action) {
                SwipeAction.DELETE -> issueRepository.closeIssue(issue.repoRef, issue.number, "not_planned")
                SwipeAction.COMPLETE -> issueRepository.closeIssue(issue.repoRef, issue.number, "completed")
                SwipeAction.PENDING -> issueRepository.moveToPending(
                    repo = issue.repoRef,
                    number = issue.number,
                    currentState = issue.state,
                    currentLabels = issue.labels.map { it.name },
                )
                SwipeAction.NONE -> return@launch
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "操作に失敗しました")
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
