package com.example.gitissuewidget.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.WidgetConfig
import com.example.gitissuewidget.ui.theme.GitIssueWidgetTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetQuickEditActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            GitIssueWidgetTheme {
                QuickEditDialog(
                    appWidgetId = appWidgetId,
                    onCancel = ::finish,
                    onSave = { repos, labels, projectTitle, projectColumn ->
                        saveAndFinish(repos, labels, projectTitle, projectColumn)
                    },
                )
            }
        }
    }

    private fun saveAndFinish(
        repos: List<RepoRef>,
        labels: List<String>,
        projectTitle: String,
        projectColumn: String,
    ) {
        val app = applicationContext as IssueWidgetApp
        lifecycleScope.launch {
            val existing = app.container.widgetConfigStore.getConfig(appWidgetId)
            val merged = WidgetConfig(
                appWidgetId = appWidgetId,
                repoRefs = repos,
                stateFilter = existing?.stateFilter ?: IssueFilter.StateFilter.OPEN,
                labels = labels,
                assigneeLogin = existing?.assigneeLogin,
                projectTitle = projectTitle.takeIf { it.isNotBlank() },
                projectColumnName = projectColumn.takeIf { it.isNotBlank() },
            )
            app.container.widgetConfigStore.saveConfig(merged)

            runCatching {
                val mgr = GlanceAppWidgetManager(this@WidgetQuickEditActivity)
                val glanceId = mgr.getGlanceIdBy(appWidgetId)
                IssueGlanceWidget().update(this@WidgetQuickEditActivity, glanceId)
            }
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickEditDialog(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSave: (List<RepoRef>, List<String>, String, String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as IssueWidgetApp

    var watchedRepos by remember { mutableStateOf<List<RepoRef>>(emptyList()) }
    var selectedRepos by remember { mutableStateOf<List<RepoRef>>(emptyList()) }
    var availableLabels by remember { mutableStateOf<List<Label>>(emptyList()) }
    var selectedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var labelsLoading by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }
    var projectColumn by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val repos = app.container.preferenceStore.watchedRepos.first()
        val existing = app.container.widgetConfigStore.getConfig(appWidgetId)
        val merged = if (existing != null) (repos + existing.repoRefs).distinct() else repos
        watchedRepos = merged
        selectedRepos = existing?.repoRefs ?: merged
        selectedLabels = existing?.labels?.toSet() ?: emptySet()
        projectTitle = existing?.projectTitle.orEmpty()
        projectColumn = existing?.projectColumnName.orEmpty()
        initialized = true
    }

    // When selected repos change, refetch label candidates from union of selected repos.
    // Use a single representative (first) for the API call to keep things light.
    LaunchedEffect(selectedRepos) {
        val repo = selectedRepos.firstOrNull() ?: run {
            availableLabels = emptyList()
            return@LaunchedEffect
        }
        labelsLoading = true
        val result = app.container.issueRepository.fetchAvailableLabels(repo)
        availableLabels = result.getOrDefault(emptyList())
        labelsLoading = false
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 360.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ウィジェット編集 (#$appWidgetId)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))

                if (!initialized) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (watchedRepos.isEmpty()) {
                    Text(
                        text = "アプリで監視リポジトリを先に追加してください。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProjectInputs(
                            projectTitle = projectTitle,
                            onProjectTitleChange = { projectTitle = it },
                            projectColumn = projectColumn,
                            onProjectColumnChange = { projectColumn = it },
                        )
                        MultiSelectRepoDropdown(
                            repos = watchedRepos,
                            selected = selectedRepos,
                            onToggle = { repo ->
                                selectedRepos = if (repo in selectedRepos) selectedRepos - repo
                                else selectedRepos + repo
                            },
                        )
                        LabelsDropdownSection(
                            available = availableLabels,
                            selected = selectedLabels,
                            loading = labelsLoading,
                            onToggle = { name ->
                                selectedLabels = if (name in selectedLabels) selectedLabels - name
                                else selectedLabels + name
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onCancel) { Text("キャンセル") }
                    Button(
                        onClick = {
                            onSave(
                                selectedRepos,
                                selectedLabels.toList(),
                                projectTitle.trim(),
                                projectColumn.trim(),
                            )
                        },
                        // Project モード時は repos が空でも保存可能
                        enabled = initialized && (selectedRepos.isNotEmpty() || projectTitle.isNotBlank()),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
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
        Text("リポジトリ (複数選択可)", style = MaterialTheme.typography.labelLarge)
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
                                Checkbox(checked = isSelected, onCheckedChange = null)
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

@Composable
private fun ProjectInputs(
    projectTitle: String,
    onProjectTitleChange: (String) -> Unit,
    projectColumn: String,
    onProjectColumnChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Project (任意)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = projectTitle,
            onValueChange = onProjectTitleChange,
            label = { Text("Project タイトル") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = projectColumn,
            onValueChange = onProjectColumnChange,
            label = { Text("カラム名 (空=全カラム)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsDropdownSection(
    available: List<Label>,
    selected: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
) {
    Column {
        Text("ラベル", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Text("ラベル取得中...", style = MaterialTheme.typography.bodySmall)
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = WidgetConfig.LABEL_NONE in selected,
                    onClick = { onToggle(WidgetConfig.LABEL_NONE) },
                    label = { Text(WidgetConfig.LABEL_NONE_DISPLAY) },
                )
                if (available.isEmpty()) {
                    Text(
                        "利用可能なラベルがありません",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
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
}
