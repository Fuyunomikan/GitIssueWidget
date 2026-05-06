package com.example.gitissuewidget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
import com.example.gitissuewidget.domain.SwipeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val availableProjectTitles by viewModel.availableProjectTitles.collectAsStateWithLifecycle()
    val projectsLoading by viewModel.projectsLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TokenSection(
                tokenSaved = uiState.tokenSaved,
                onSave = viewModel::saveToken,
                onClear = viewModel::clearToken,
            )
            HorizontalDivider()
            RepoSection(
                repos = uiState.watchedRepos,
                onAdd = viewModel::addRepo,
                onRemove = viewModel::removeRepo,
            )
            HorizontalDivider()
            SortSection(
                sortOption = uiState.sortOption,
                sortDirection = uiState.sortDirection,
                perPage = uiState.perPage,
                onSortOption = viewModel::setSortOption,
                onSortDirection = viewModel::setSortDirection,
                onPerPage = viewModel::setPerPage,
            )
            HorizontalDivider()
            DisplaySection(
                showOpenBadge = uiState.showOpenBadge,
                showLabels = uiState.showLabels,
                onShowOpenBadgeChange = viewModel::setShowOpenBadge,
                onShowLabelsChange = viewModel::setShowLabels,
            )
            HorizontalDivider()
            SwipeSection(
                leftSwipeAction = uiState.leftSwipeAction,
                rightSwipeAction = uiState.rightSwipeAction,
                onLeftChange = viewModel::setLeftSwipeAction,
                onRightChange = viewModel::setRightSwipeAction,
            )
            HorizontalDivider()
            ProjectSection(
                swipeProjectTitle = uiState.swipeProjectTitle,
                pendingColumnName = uiState.pendingColumnName,
                doneColumnName = uiState.doneColumnName,
                dueDateFieldName = uiState.dueDateFieldName,
                availableProjectTitles = availableProjectTitles,
                projectsLoading = projectsLoading,
                onProjectTitleChange = viewModel::setSwipeProjectTitle,
                onPendingColumnChange = viewModel::setPendingColumnName,
                onDoneColumnChange = viewModel::setDoneColumnName,
                onDueDateFieldChange = viewModel::setDueDateFieldName,
                onRefreshProjects = viewModel::refreshProjects,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSection(
    swipeProjectTitle: String,
    pendingColumnName: String,
    doneColumnName: String,
    dueDateFieldName: String,
    availableProjectTitles: List<String>?,
    projectsLoading: Boolean,
    onProjectTitleChange: (String) -> Unit,
    onPendingColumnChange: (String) -> Unit,
    onDoneColumnChange: (String) -> Unit,
    onDueDateFieldChange: (String) -> Unit,
    onRefreshProjects: () -> Unit,
) {
    // Local edit state — only commit on focus loss / blur via onValueChange to keep typing smooth.
    var titleDraft by remember(swipeProjectTitle) { mutableStateOf(swipeProjectTitle) }
    var pendingDraft by remember(pendingColumnName) { mutableStateOf(pendingColumnName) }
    var doneDraft by remember(doneColumnName) { mutableStateOf(doneColumnName) }
    var dueDateDraft by remember(dueDateFieldName) { mutableStateOf(dueDateFieldName) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("スワイプ用 Project (Projects v2)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "PENDING / COMPLETE スワイプ時に Issue を移動させる Project を指定します。\n" +
                "PAT に project スコープが必要です。Project が未設定 / 見つからない場合、" +
                "スワイプ時に警告ダイアログが出て処理を中断します。",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = titleDraft,
            onValueChange = {
                titleDraft = it
                onProjectTitleChange(it)
            },
            label = { Text("Project タイトル (例: ToDoList)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = pendingDraft,
            onValueChange = {
                pendingDraft = it
                onPendingColumnChange(it)
            },
            label = { Text("Pending カラム名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = doneDraft,
            onValueChange = {
                doneDraft = it
                onDoneColumnChange(it)
            },
            label = { Text("Done カラム名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dueDateDraft,
            onValueChange = {
                dueDateDraft = it
                onDueDateFieldChange(it)
            },
            label = { Text("期限フィールド名 (Date 型カスタムフィールド)") },
            supportingText = {
                Text(
                    "Project に作成した Date 型カスタムフィールドの名前。Issue 行に期限が表示され、" +
                        "ソート順を「due date」にすると期限順に並び替えできます。",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRefreshProjects, enabled = !projectsLoading) {
                Text("Project 一覧を取得")
            }
            if (projectsLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
            }
        }

        availableProjectTitles?.let { titles ->
            if (titles.isEmpty()) {
                Text("（取得結果: 0 件）", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("取得済み Project:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        titles.forEach { title ->
                            AssistChip(
                                onClick = {
                                    titleDraft = title
                                    onProjectTitleChange(title)
                                },
                                label = { Text(title) },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeSection(
    leftSwipeAction: SwipeAction,
    rightSwipeAction: SwipeAction,
    onLeftChange: (SwipeAction) -> Unit,
    onRightChange: (SwipeAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("スワイプ操作", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "メイン画面で Issue カードを左右にスワイプしたときの動作。" +
                "PENDING / COMPLETE は下の「スワイプ用 Project」で指定した Project の各カラムへ Issue を移動します。" +
                "実行前に確認ダイアログが表示されます。",
            style = MaterialTheme.typography.bodySmall,
        )
        SwipeActionDropdown(
            label = "右スワイプ",
            selected = rightSwipeAction,
            onSelect = onRightChange,
        )
        SwipeActionDropdown(
            label = "左スワイプ",
            selected = leftSwipeAction,
            onSelect = onLeftChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionDropdown(
    label: String,
    selected: SwipeAction,
    onSelect: (SwipeAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected.label,
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
                SwipeAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            onSelect(action)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplaySection(
    showOpenBadge: Boolean,
    showLabels: Boolean,
    onShowOpenBadgeChange: (Boolean) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ウィジェット表示項目", style = MaterialTheme.typography.titleMedium)
        DisplayToggleRow(
            label = "Open / Closed バッジ",
            checked = showOpenBadge,
            onCheckedChange = onShowOpenBadgeChange,
        )
        DisplayToggleRow(
            label = "ラベル",
            checked = showLabels,
            onCheckedChange = onShowLabelsChange,
        )
    }
}

@Composable
private fun DisplayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TokenSection(
    tokenSaved: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GitHub Personal Access Token", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (tokenSaved) "保存済み（非表示）" else "未設定",
            color = if (tokenSaved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("PAT (repo または public_repo)") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隠す" else "表示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
            ) {
                Text("保存")
            }
            OutlinedButton(onClick = onClear, enabled = tokenSaved) {
                Text("削除")
            }
        }
    }
}

@Composable
private fun RepoSection(
    repos: List<RepoRef>,
    onAdd: (String) -> Unit,
    onRemove: (RepoRef) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("監視リポジトリ", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("owner/repo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    onAdd(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
            ) {
                Text("追加")
            }
        }
        if (repos.isEmpty()) {
            Text("未登録", style = MaterialTheme.typography.bodySmall)
        } else {
            repos.forEach { repo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(repo.fullName, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(repo) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortSection(
    sortOption: SortOption,
    sortDirection: SortDirection,
    perPage: Int,
    onSortOption: (SortOption) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onPerPage: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ソート / 表示", style = MaterialTheme.typography.titleMedium)

        Text("ソート順", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortOption.values().forEach { opt ->
                FilterChip(
                    selected = opt == sortOption,
                    onClick = { onSortOption(opt) },
                    label = { Text(opt.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("方向", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortDirection.values().forEach { dir ->
                FilterChip(
                    selected = dir == sortDirection,
                    onClick = { onSortDirection(dir) },
                    label = { Text(dir.apiValue) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("表示件数", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, 50).forEach { n ->
                FilterChip(
                    selected = n == perPage,
                    onClick = { onPerPage(n) },
                    label = { Text(n.toString()) },
                )
            }
        }
    }
}
