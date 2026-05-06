package com.example.gitissuewidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.WidgetConfig
import com.example.gitissuewidget.ui.theme.GitIssueWidgetTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            GitIssueWidgetTheme {
                ConfigureScreen(
                    appWidgetId = appWidgetId,
                    onCancel = ::finish,
                    onSave = ::saveAndFinish,
                )
            }
        }
    }

    private fun saveAndFinish(draft: ConfigDraft) {
        val app = applicationContext as IssueWidgetApp
        lifecycleScope.launch {
            val resolvedAssignee = if (draft.assigneeMe) {
                app.container.issueRepository.fetchCurrentUser().getOrNull()?.login
            } else null

            val config = WidgetConfig(
                appWidgetId = appWidgetId,
                repoRefs = draft.repoRefs,
                stateFilter = draft.stateFilter,
                labels = draft.labels,
                labelFilterMode = draft.labelFilterMode,
                assigneeLogin = resolvedAssignee,
                projectTitle = draft.projectTitle.takeIf { it.isNotBlank() },
                projectColumnName = draft.projectColumnName.takeIf { it.isNotBlank() },
            )
            app.container.widgetConfigStore.saveConfig(config)

            val mgr = GlanceAppWidgetManager(this@WidgetConfigureActivity)
            runCatching {
                val glanceId = mgr.getGlanceIdBy(appWidgetId)
                IssueGlanceWidget().update(this@WidgetConfigureActivity, glanceId)
            }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

private data class ConfigDraft(
    val repoRefs: List<RepoRef>,
    val stateFilter: IssueFilter.StateFilter,
    val labels: List<String>,
    val labelFilterMode: WidgetConfig.LabelFilterMode,
    val assigneeMe: Boolean,
    val projectTitle: String,
    val projectColumnName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSave: (ConfigDraft) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as IssueWidgetApp

    var watchedRepos by remember { mutableStateOf<List<RepoRef>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var selectedRepos by remember { mutableStateOf<List<RepoRef>>(emptyList()) }
    var stateFilter by remember { mutableStateOf(IssueFilter.StateFilter.OPEN) }
    var labelInput by remember { mutableStateOf("") }
    var includeUnlabeled by remember { mutableStateOf(false) }
    var labelFilterMode by remember { mutableStateOf(WidgetConfig.LabelFilterMode.AND) }
    var assigneeMe by remember { mutableStateOf(false) }
    var hadExistingConfig by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }
    var projectColumn by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val repos = app.container.preferenceStore.watchedRepos.first()
        val existing = app.container.widgetConfigStore.getConfig(appWidgetId)
        if (existing != null) {
            hadExistingConfig = true
            stateFilter = existing.stateFilter
            // ラベル予約名 LABEL_NONE は別 Switch で表現するため、テキスト入力からは除外する
            val (specials, normals) = existing.labels.partition { it == WidgetConfig.LABEL_NONE }
            labelInput = normals.joinToString(", ")
            includeUnlabeled = specials.isNotEmpty()
            labelFilterMode = existing.labelFilterMode
            assigneeMe = existing.assigneeLogin != null
            projectTitle = existing.projectTitle.orEmpty()
            projectColumn = existing.projectColumnName.orEmpty()
            // Include existing repos even if removed from watched list
            val merged = (repos + existing.repoRefs).distinct()
            watchedRepos = merged
            selectedRepos = existing.repoRefs
        } else {
            watchedRepos = repos
            // Default: select all watched repos so widget shows everything
            selectedRepos = repos
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (hadExistingConfig) "ウィジェット再構成 (#$appWidgetId)"
                        else "ウィジェット設定 (#$appWidgetId)",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!loaded) {
                Text("読み込み中...")
                return@Column
            }

            if (watchedRepos.isEmpty()) {
                Text(
                    text = "アプリ本体の設定で監視リポジトリを先に追加してください。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("キャンセル") }
                }
                return@Column
            }

            ProjectFilterSection(
                projectTitle = projectTitle,
                onProjectTitleChange = { projectTitle = it },
                projectColumn = projectColumn,
                onProjectColumnChange = { projectColumn = it },
            )
            HorizontalDivider()
            MultiSelectRepoDropdown(
                repos = watchedRepos,
                selected = selectedRepos,
                onToggle = { repo ->
                    selectedRepos = if (repo in selectedRepos) selectedRepos - repo
                    else selectedRepos + repo
                },
            )
            HorizontalDivider()
            StateFilterPicker(
                state = stateFilter,
                onSelect = { stateFilter = it },
            )
            HorizontalDivider()
            LabelInput(
                value = labelInput,
                onValueChange = { labelInput = it },
                includeUnlabeled = includeUnlabeled,
                onIncludeUnlabeledChange = { includeUnlabeled = it },
                filterMode = labelFilterMode,
                onFilterModeChange = { labelFilterMode = it },
            )
            HorizontalDivider()
            AssigneeToggle(
                checked = assigneeMe,
                onCheckedChange = { assigneeMe = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) { Text("キャンセル") }
                Button(
                    onClick = {
                        val typedLabels = labelInput
                            .split(',', '、')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val labels = if (includeUnlabeled) typedLabels + WidgetConfig.LABEL_NONE
                        else typedLabels
                        onSave(
                            ConfigDraft(
                                repoRefs = selectedRepos,
                                stateFilter = stateFilter,
                                labels = labels,
                                labelFilterMode = labelFilterMode,
                                assigneeMe = assigneeMe,
                                projectTitle = projectTitle.trim(),
                                projectColumnName = projectColumn.trim(),
                            ),
                        )
                    },
                    // Project モード時は repos が空でも保存可能（リポジトリは追加絞り込み）
                    enabled = selectedRepos.isNotEmpty() || projectTitle.isNotBlank(),
                ) {
                    Text("完了")
                }
            }
        }
    }
}

@Composable
private fun ProjectFilterSection(
    projectTitle: String,
    onProjectTitleChange: (String) -> Unit,
    projectColumn: String,
    onProjectColumnChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Project (Projects v2)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Project タイトルを指定するとそのプロジェクトの Issue を表示します。" +
                "未指定なら下のリポジトリから直接 Issue を取得します。" +
                "PAT に project スコープが必要です。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = projectTitle,
            onValueChange = onProjectTitleChange,
            label = { Text("Project タイトル (例: ToDoList)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = projectColumn,
            onValueChange = onProjectColumnChange,
            label = { Text("カラム名 (例: Todo / Pending / Done。空 = 全カラム)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectRepoDropdown(
    repos: List<RepoRef>,
    selected: List<RepoRef>,
    onToggle: (RepoRef) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("対象リポジトリ (複数選択可)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = formatSelectedRepos(selected),
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
                    val isSelected = repo in selected
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(repo.fullName)
                            }
                        },
                        onClick = { onToggle(repo) },
                    )
                }
            }
        }
    }
}

private fun formatSelectedRepos(selected: List<RepoRef>): String = when (selected.size) {
    0 -> ""
    1 -> selected.first().fullName
    else -> "${selected.first().fullName} +${selected.size - 1}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateFilterPicker(
    state: IssueFilter.StateFilter,
    onSelect: (IssueFilter.StateFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("State フィルタ", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IssueFilter.StateFilter.values().forEach { s ->
                FilterChip(
                    selected = s == state,
                    onClick = { onSelect(s) },
                    label = { Text(s.apiValue) },
                )
            }
        }
    }
}

@Composable
private fun LabelInput(
    value: String,
    onValueChange: (String) -> Unit,
    includeUnlabeled: Boolean,
    onIncludeUnlabeledChange: (Boolean) -> Unit,
    filterMode: WidgetConfig.LabelFilterMode,
    onFilterModeChange: (WidgetConfig.LabelFilterMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ラベル絞り込み", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("カンマ区切り (例: bug, p1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "ラベルなしも含める", modifier = Modifier.weight(1f))
            Switch(checked = includeUnlabeled, onCheckedChange = onIncludeUnlabeledChange)
        }
        LabelFilterModePicker(mode = filterMode, onSelect = onFilterModeChange)
    }
}

@Composable
private fun LabelFilterModePicker(
    mode: WidgetConfig.LabelFilterMode,
    onSelect: (WidgetConfig.LabelFilterMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "複数ラベルの結合",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetConfig.LabelFilterMode.values().forEach { m ->
                FilterChip(
                    selected = m == mode,
                    onClick = { onSelect(m) },
                    label = { Text(m.displayName) },
                )
            }
        }
    }
}

@Composable
private fun AssigneeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("自分にアサインされたものだけ", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "保存時に GET /user で自分のloginを解決します",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
