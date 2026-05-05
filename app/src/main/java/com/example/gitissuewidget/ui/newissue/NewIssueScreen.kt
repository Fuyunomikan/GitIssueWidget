package com.example.gitissuewidget.ui.newissue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewIssueScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: NewIssueViewModel = viewModel(factory = NewIssueViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            viewModel.consumeCreated()
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Issue を作成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.watchedRepos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("設定画面で監視リポジトリを追加してください。")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RepoDropdown(
                repos = uiState.watchedRepos,
                selected = uiState.selectedRepo,
                onSelect = viewModel::selectRepo,
            )

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::setTitle,
                label = { Text("タイトル") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.body,
                onValueChange = viewModel::setBody,
                label = { Text("本文 (任意, Markdown)") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            LabelsSection(
                available = uiState.availableLabels,
                selected = uiState.selectedLabels,
                loading = uiState.labelsLoading,
                onToggle = viewModel::toggleLabel,
            )

            Button(
                onClick = viewModel::submit,
                enabled = uiState.selectedRepo != null && uiState.title.isNotBlank() && !uiState.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("作成")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoDropdown(
    repos: List<RepoRef>,
    selected: RepoRef?,
    onSelect: (RepoRef) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("リポジトリ", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.fullName ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                repos.forEach { repo ->
                    DropdownMenuItem(
                        text = { Text(repo.fullName) },
                        onClick = {
                            onSelect(repo)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsSection(
    available: List<Label>,
    selected: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
) {
    Column {
        Text("ラベル", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Text("ラベルを取得中...", style = MaterialTheme.typography.bodySmall)
            available.isEmpty() -> Text(
                "利用可能なラベルがありません",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                available.forEach { label ->
                    FilterChip(
                        selected = label.name in selected,
                        onClick = { onToggle(label.name) },
                        label = { Text(label.name) },
                    )
                }
            }
        }
    }
}
