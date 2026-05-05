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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
                repoRef = draft.repoRef,
                stateFilter = draft.stateFilter,
                labels = draft.labels,
                assigneeLogin = resolvedAssignee,
            )
            app.container.widgetConfigStore.saveConfig(config)

            // Trigger immediate widget render for this id
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
    val repoRef: RepoRef,
    val stateFilter: IssueFilter.StateFilter,
    val labels: List<String>,
    val assigneeMe: Boolean,
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

    var selectedRepo by remember { mutableStateOf<RepoRef?>(null) }
    var stateFilter by remember { mutableStateOf(IssueFilter.StateFilter.OPEN) }
    var labelInput by remember { mutableStateOf("") }
    var assigneeMe by remember { mutableStateOf(false) }
    var hadExistingConfig by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val repos = app.container.preferenceStore.watchedRepos.first()
        val existing = app.container.widgetConfigStore.getConfig(appWidgetId)
        // Restore previous values when reconfiguring
        if (existing != null) {
            hadExistingConfig = true
            stateFilter = existing.stateFilter
            labelInput = existing.labels.joinToString(", ")
            assigneeMe = existing.assigneeLogin != null
            // Include existing repo even if user removed it from watched list
            val merged = if (existing.repoRef !in repos) repos + existing.repoRef else repos
            watchedRepos = merged
            selectedRepo = existing.repoRef
        } else {
            watchedRepos = repos
            selectedRepo = repos.firstOrNull()
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

            RepoPicker(
                repos = watchedRepos,
                selected = selectedRepo,
                onSelect = { selectedRepo = it },
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
                        val repo = selectedRepo ?: return@Button
                        val labels = labelInput
                            .split(',', '、')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onSave(ConfigDraft(repo, stateFilter, labels, assigneeMe))
                    },
                    enabled = selectedRepo != null,
                ) {
                    Text("完了")
                }
            }
        }
    }
}

@Composable
private fun RepoPicker(
    repos: List<RepoRef>,
    selected: RepoRef?,
    onSelect: (RepoRef) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("対象リポジトリ", style = MaterialTheme.typography.titleMedium)
        repos.forEach { repo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = repo == selected,
                        onClick = { onSelect(repo) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = repo == selected,
                    onClick = { onSelect(repo) },
                )
                Text(repo.fullName)
            }
        }
    }
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
private fun LabelInput(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ラベル絞り込み", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("カンマ区切り (例: bug, p1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
