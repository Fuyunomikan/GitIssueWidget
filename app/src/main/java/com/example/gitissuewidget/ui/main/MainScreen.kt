package com.example.gitissuewidget.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onCreateIssue: () -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Refresh on every entry to MAIN (e.g., returning from NewIssue/Settings)
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Issue Widget") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                    }
                    IconButton(
                        onClick = onCreateIssue,
                        enabled = uiState.tokenSet && uiState.watchedRepos.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Issue 作成")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                tokenSet = uiState.tokenSet,
                userLogin = uiState.user?.login,
                repoCount = uiState.watchedRepos.size,
                onOpenSettings = onOpenSettings,
            )

            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            when {
                !uiState.tokenSet -> EmptyHint("設定画面でPATを登録してください。")
                uiState.watchedRepos.isEmpty() -> EmptyHint("設定画面で監視リポジトリを追加してください。")
                uiState.issues.isEmpty() && !uiState.loading -> EmptyHint("Issueが見つかりません。")
                else -> IssueList(
                    issues = uiState.issues,
                    onClickIssue = { issue ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri()))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    tokenSet: Boolean,
    userLogin: String?,
    repoCount: Int,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (tokenSet) "ログイン状態: 認証済み" else "ログイン状態: 未ログイン",
                style = MaterialTheme.typography.titleMedium,
                color = if (tokenSet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            )
            Text(
                text = userLogin?.let { "ユーザー: @$it" } ?: "ユーザー: -",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "監視リポジトリ: ${repoCount}件",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!tokenSet || repoCount == 0) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "設定を開く")
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun IssueList(issues: List<Issue>, onClickIssue: (Issue) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(issues, key = { "${it.repoRef.fullName}#${it.number}" }) { issue ->
            IssueRow(issue, onClick = { onClickIssue(issue) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueRow(issue: Issue, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (issue.state == IssueState.OPEN) "● open" else "● closed",
                    color = if (issue.state == IssueState.OPEN) Color(0xFF2E7D32) else Color(0xFF8E24AA),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(text = "#${issue.number}", style = MaterialTheme.typography.labelSmall)
                Text(text = issue.repoRef.fullName, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
            if (issue.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    issue.labels.take(4).forEach { LabelChip(it) }
                }
            }
        }
    }
}

@Composable
private fun LabelChip(label: Label) {
    val bg = parseLabelColor(label.colorHex)
    val textColor = if (isLight(bg)) Color.Black else Color.White
    Surface(color = bg, contentColor = textColor) {
        Text(
            text = label.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun parseLabelColor(hex: String): Color {
    return runCatching {
        val cleaned = hex.removePrefix("#")
        val parsed = cleaned.toLong(16)
        Color(0xFF000000 or parsed)
    }.getOrDefault(Color(0xFFCCCCCC))
}

private fun isLight(color: Color): Boolean {
    val yiq = (color.red * 299 + color.green * 587 + color.blue * 114) / 1000
    return yiq >= 0.5f
}
