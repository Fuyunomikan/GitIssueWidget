package com.example.gitissuewidget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.data.local.PreferenceStore
import com.example.gitissuewidget.data.local.TokenStore
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
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
)

class SettingsViewModel(
    private val tokenStore: TokenStore,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    private val tokenSaved = MutableStateFlow(tokenStore.hasToken())

    val uiState: StateFlow<SettingsUiState> = combine(
        tokenSaved,
        preferenceStore.watchedRepos,
        preferenceStore.sortOption,
        preferenceStore.sortDirection,
        preferenceStore.perPage,
    ) { saved, repos, sort, direction, perPage ->
        SettingsUiState(
            tokenSaved = saved,
            watchedRepos = repos,
            sortOption = sort,
            sortDirection = direction,
            perPage = perPage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
                )
            }
        }
    }
}
