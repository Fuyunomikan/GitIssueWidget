package com.example.gitissuewidget.ui.main

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.SwipeAction

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

    // Confirmation dialog state for both PENDING / COMPLETE swipes (carry the action via the issue+isLeft pair).
    // The actual SwipeAction is re-derived from uiState when the user confirms.
    var pendingConfirm by remember { mutableStateOf<Pair<Issue, Boolean>?>(null) }

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
                .padding(padding),
        ) {
            StatusCard(
                tokenSet = uiState.tokenSet,
                userLogin = uiState.user?.login,
                repoCount = uiState.watchedRepos.size,
                onOpenSettings = onOpenSettings,
            )

            HorizontalDivider()

            StateFilterTabs(
                selected = uiState.selectedStateTab,
                onSelect = viewModel::setStateTab,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                        leftSwipeAction = uiState.leftSwipeAction,
                        rightSwipeAction = uiState.rightSwipeAction,
                        onClickIssue = { issue ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri()))
                            }
                        },
                        onSwipe = { issue, isLeft ->
                            val action = if (isLeft) uiState.leftSwipeAction else uiState.rightSwipeAction
                            // Both PENDING and COMPLETE require explicit confirmation (Project move is destructive).
                            if (action == SwipeAction.PENDING || action == SwipeAction.COMPLETE) {
                                pendingConfirm = issue to isLeft
                                false
                            } else {
                                viewModel.applySwipeAction(issue, isLeft)
                                true
                            }
                        },
                    )
                }
            }
        }
    }

    val pending = pendingConfirm
    if (pending != null) {
        val issue = pending.first
        val isLeft = pending.second
        val action = if (isLeft) uiState.leftSwipeAction else uiState.rightSwipeAction
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text("${action.label}しますか?") },
            text = {
                Text("対象 Issue:\n#${issue.number} ${issue.title}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.applySwipeAction(issue, isLeft)
                        pendingConfirm = null
                    },
                ) { Text("移動") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("キャンセル") }
            },
        )
    }

    // Project 未設定時の警告ダイアログ。スワイプアクションは実行されず処理が中断される。
    val projectMissing = uiState.swipeProjectMissing
    if (projectMissing != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeSwipeProjectMissing,
            title = { Text("スワイプ用 Project が未設定") },
            text = { Text(projectMissing) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeSwipeProjectMissing) { Text("OK") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    tokenSet: Boolean,
    userLogin: String?,
    repoCount: Int,
    onOpenSettings: () -> Unit,
) {
    // Issue カードと識別しやすいよう、ログイン状態セクションだけ黒背景＋白文字で固定表示する。
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black,
        contentColor = Color.White,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (tokenSet) "ログイン状態: 認証済み" else "ログイン状態: 未ログイン",
                style = MaterialTheme.typography.titleMedium,
                color = if (tokenSet) Color(0xFF66BB6A) else Color(0xFFEF9A9A),
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
                    Icon(Icons.Filled.Settings, contentDescription = "設定を開く", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateFilterTabs(
    selected: IssueFilter.StateFilter,
    onSelect: (IssueFilter.StateFilter) -> Unit,
) {
    // Open / Closed の 2 タブのみ。ALL は本画面では使用しない。
    val tabs = listOf(IssueFilter.StateFilter.OPEN, IssueFilter.StateFilter.CLOSED)
    val labels = mapOf(
        IssueFilter.StateFilter.OPEN to "Open",
        IssueFilter.StateFilter.CLOSED to "Closed",
    )
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, state ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(state) },
                text = { Text(labels.getValue(state)) },
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueList(
    issues: List<Issue>,
    leftSwipeAction: SwipeAction,
    rightSwipeAction: SwipeAction,
    onClickIssue: (Issue) -> Unit,
    onSwipe: (Issue, isLeftSwipe: Boolean) -> Boolean,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(issues, key = { "${it.repoRef.fullName}#${it.number}" }) { issue ->
            SwipeableIssueRow(
                issue = issue,
                leftSwipeAction = leftSwipeAction,
                rightSwipeAction = rightSwipeAction,
                onClick = { onClickIssue(issue) },
                onSwipe = { isLeft -> onSwipe(issue, isLeft) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableIssueRow(
    issue: Issue,
    leftSwipeAction: SwipeAction,
    rightSwipeAction: SwipeAction,
    onClick: () -> Unit,
    /** Returns true to dismiss the row, false to snap back. */
    onSwipe: (isLeftSwipe: Boolean) -> Boolean,
) {
    @Suppress("DEPRECATION") // confirmValueChange is used as an action hook;
    // we let the caller decide via onSwipe whether to dismiss or snap back.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (rightSwipeAction != SwipeAction.NONE) onSwipe(false) else false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (leftSwipeAction != SwipeAction.NONE) onSwipe(true) else false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    // When the issue is updated (PENDING action keeps the row in the list with new updatedAt),
    // reset the swipe state so the row is visible again.
    LaunchedEffect(issue.updatedAt) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val action = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> rightSwipeAction
                SwipeToDismissBoxValue.EndToStart -> leftSwipeAction
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            SwipeBackground(action, state.dismissDirection)
        },
        enableDismissFromStartToEnd = rightSwipeAction != SwipeAction.NONE,
        enableDismissFromEndToStart = leftSwipeAction != SwipeAction.NONE,
    ) {
        IssueRow(issue, onClick = onClick)
    }
}

@Composable
private fun SwipeBackground(action: SwipeAction, direction: SwipeToDismissBoxValue) {
    if (action == SwipeAction.NONE || direction == SwipeToDismissBoxValue.Settled) return
    val color = when (action) {
        SwipeAction.COMPLETE -> Color(0xFF388E3C)
        SwipeAction.PENDING -> Color(0xFFF57C00)
        SwipeAction.NONE -> return
    }
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Text(
            text = action.label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
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
                if (!issue.dueDate.isNullOrBlank()) {
                    DueDateChip(issue.dueDate)
                }
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
private fun DueDateChip(dueDate: String) {
    val past = isPastDue(dueDate)
    Text(
        text = formatDueDateLong(dueDate),
        color = if (past) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
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

private fun formatDueDateLong(iso: String): String = runCatching {
    val parts = iso.split('-')
    if (parts.size < 3) return@runCatching iso
    "${parts[0]}/${parts[1].padStart(2, '0')}/${parts[2].padStart(2, '0')}"
}.getOrDefault(iso)

private fun isPastDue(iso: String): Boolean = runCatching {
    java.time.LocalDate.parse(iso).isBefore(java.time.LocalDate.now())
}.getOrDefault(false)
