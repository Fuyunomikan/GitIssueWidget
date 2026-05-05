package com.example.gitissuewidget.ui.newissue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.repo.IssueRepository
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class NewIssueUiState(
    val watchedRepos: List<RepoRef> = emptyList(),
    val selectedRepo: RepoRef? = null,
    val title: String = "",
    val body: String = "",
    val availableLabels: List<Label> = emptyList(),
    val selectedLabels: Set<String> = emptySet(),
    val labelsLoading: Boolean = false,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val created: Boolean = false,
)

class NewIssueViewModel(
    private val preferenceStore: PreferenceStore,
    private val issueRepository: IssueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewIssueUiState())
    val uiState: StateFlow<NewIssueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val repos = preferenceStore.watchedRepos.first()
            val firstRepo = repos.firstOrNull()
            _uiState.value = _uiState.value.copy(
                watchedRepos = repos,
                selectedRepo = firstRepo,
            )
            firstRepo?.let { fetchLabels(it) }
        }
    }

    fun selectRepo(repo: RepoRef) {
        if (repo == _uiState.value.selectedRepo) return
        _uiState.value = _uiState.value.copy(
            selectedRepo = repo,
            selectedLabels = emptySet(),
            availableLabels = emptyList(),
        )
        fetchLabels(repo)
    }

    private fun fetchLabels(repo: RepoRef) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(labelsLoading = true)
            val result = issueRepository.fetchAvailableLabels(repo)
            _uiState.value = _uiState.value.copy(
                labelsLoading = false,
                availableLabels = result.getOrDefault(emptyList()),
            )
        }
    }

    fun setTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun setBody(value: String) {
        _uiState.value = _uiState.value.copy(body = value)
    }

    fun toggleLabel(name: String) {
        val current = _uiState.value.selectedLabels
        _uiState.value = _uiState.value.copy(
            selectedLabels = if (name in current) current - name else current + name,
        )
    }

    fun submit() {
        val state = _uiState.value
        val repo = state.selectedRepo ?: return
        val title = state.title.trim()
        if (title.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "タイトルを入力してください")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(submitting = true, errorMessage = null)
            val result = issueRepository.createIssue(
                repo = repo,
                title = title,
                body = state.body.trim().takeIf { it.isNotEmpty() },
                labels = state.selectedLabels.toList(),
            )
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(submitting = false, created = true) },
                onFailure = { _uiState.value.copy(submitting = false, errorMessage = it.message ?: "作成に失敗しました") },
            )
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun consumeCreated() {
        _uiState.value = _uiState.value.copy(
            created = false,
            title = "",
            body = "",
            selectedLabels = emptySet(),
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IssueWidgetApp
                NewIssueViewModel(
                    preferenceStore = app.container.preferenceStore,
                    issueRepository = app.container.issueRepository,
                )
            }
        }
    }
}
