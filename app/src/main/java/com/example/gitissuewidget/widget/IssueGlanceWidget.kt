package com.example.gitissuewidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.gitissuewidget.IssueWidgetApp
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color as ComposeColor

class IssueGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as IssueWidgetApp).container
        val tokenSet = container.tokenStore.hasToken()

        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrNull()
        val widgetConfig = appWidgetId?.let { container.widgetConfigStore.getConfig(it) }

        val repo = widgetConfig?.repoRef
            ?: container.preferenceStore.watchedRepos.first().firstOrNull()

        val sort = container.preferenceStore.sortOption.first()
        val direction = container.preferenceStore.sortDirection.first()
        val perPage = container.preferenceStore.perPage.first()
        val filter = IssueFilter(
            stateFilter = widgetConfig?.stateFilter ?: IssueFilter.StateFilter.OPEN,
            labels = widgetConfig?.labels.orEmpty(),
            assignee = widgetConfig?.assigneeLogin,
            sort = sort,
            direction = direction,
            perPage = perPage,
        )

        val state: WidgetState = when {
            !tokenSet -> WidgetState.NoToken
            repo == null -> WidgetState.NoRepo
            else -> {
                val title = buildHeaderTitle(repo.fullName, filter)
                container.issueRepository.fetchIssues(repo, filter).fold(
                    onSuccess = { fresh ->
                        appWidgetId?.let { container.issueCacheStore.save(it, fresh) }
                        WidgetState.Loaded(title, fresh, staleSinceMillis = null)
                    },
                    onFailure = { error ->
                        val cached = appWidgetId?.let { container.issueCacheStore.load(it) }
                        if (cached != null && cached.issues.isNotEmpty()) {
                            WidgetState.Loaded(title, cached.issues, staleSinceMillis = cached.savedAtMillis)
                        } else {
                            WidgetState.Error(repo.fullName, error.message ?: "取得エラー")
                        }
                    },
                )
            }
        }

        provideContent { WidgetContent(state) }
    }
}

private fun buildHeaderTitle(repoFullName: String, filter: IssueFilter): String {
    val parts = mutableListOf(repoFullName)
    if (filter.stateFilter != IssueFilter.StateFilter.OPEN) parts += filter.stateFilter.apiValue
    if (filter.labels.isNotEmpty()) parts += filter.labels.joinToString(",")
    if (filter.assignee != null) parts += "@${filter.assignee}"
    return parts.joinToString(" · ")
}

private sealed interface WidgetState {
    data object NoToken : WidgetState
    data object NoRepo : WidgetState
    data class Loaded(
        val title: String,
        val issues: List<Issue>,
        val staleSinceMillis: Long? = null,
    ) : WidgetState
    data class Error(val repoName: String, val message: String) : WidgetState
}

@androidx.compose.runtime.Composable
private fun WidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = ComposeColor.White, night = ComposeColor(0xFF1C1B1F)))
            .padding(8.dp),
    ) {
        Header(
            title = when (state) {
                is WidgetState.Loaded -> state.title
                is WidgetState.Error -> state.repoName
                else -> "GitHub Issue"
            },
        )
        if (state is WidgetState.Loaded && state.staleSinceMillis != null) {
            StaleNotice(state.staleSinceMillis)
        }
        Spacer(GlanceModifier.height(4.dp))
        when (state) {
            WidgetState.NoToken -> CenterMessage("PATを設定してください")
            WidgetState.NoRepo -> CenterMessage("リポジトリを追加してください")
            is WidgetState.Error -> CenterMessage(state.message)
            is WidgetState.Loaded -> if (state.issues.isEmpty()) {
                CenterMessage("Issueがありません")
            } else {
                IssueListContent(state.issues)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StaleNotice(savedAtMillis: Long) {
    val timeStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(savedAtMillis))
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(day = ComposeColor(0xFFFFF3E0), night = ComposeColor(0xFF3E2723)))
            .cornerRadius(4.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "オフライン: キャッシュ ($timeStr)",
            style = TextStyle(
                fontSize = 9.sp,
                color = ColorProvider(day = ComposeColor(0xFF8D5B00), night = ComposeColor(0xFFFFB74D)),
            ),
            maxLines = 1,
        )
    }
}

@androidx.compose.runtime.Composable
private fun Header(title: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(day = ComposeColor(0xFF1C1B1F), night = ComposeColor.White),
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Image(
            provider = ImageProvider(android.R.drawable.ic_popup_sync),
            contentDescription = "更新",
            colorFilter = ColorFilter.tint(
                ColorProvider(day = ComposeColor(0xFF555555), night = ComposeColor(0xFFCCCCCC)),
            ),
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@androidx.compose.runtime.Composable
private fun IssueListContent(issues: List<Issue>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(issues, itemId = { "${it.repoRef.fullName}#${it.number}".hashCode().toLong() }) { issue ->
            IssueRow(issue)
        }
    }
}

@androidx.compose.runtime.Composable
private fun IssueRow(issue: Issue) {
    val openIntent = Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(openIntent)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateBadge(issue.state)
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "#${issue.number}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(day = ComposeColor(0xFF666666), night = ComposeColor(0xFFAAAAAA)),
                ),
            )
        }
        Text(
            text = issue.title,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(day = ComposeColor(0xFF1C1B1F), night = ComposeColor.White),
            ),
            maxLines = 2,
        )
        if (issue.labels.isNotEmpty()) {
            Row(modifier = GlanceModifier.padding(top = 2.dp)) {
                issue.labels.take(3).forEach { label ->
                    LabelBadge(label)
                    Spacer(GlanceModifier.width(4.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StateBadge(state: IssueState) {
    val (label, color) = when (state) {
        IssueState.OPEN -> "open" to ComposeColor(0xFF2E7D32)
        IssueState.CLOSED -> "closed" to ComposeColor(0xFF8E24AA)
    }
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(day = color, night = color))
            .cornerRadius(4.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(day = ComposeColor.White, night = ComposeColor.White),
            ),
        )
    }
}

@androidx.compose.runtime.Composable
private fun LabelBadge(label: Label) {
    val bg = parseLabelColor(label.colorHex)
    val textColor = if (isLight(bg)) ComposeColor.Black else ComposeColor.White
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(day = bg, night = bg))
            .cornerRadius(4.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = label.name,
            style = TextStyle(
                fontSize = 9.sp,
                color = ColorProvider(day = textColor, night = textColor),
            ),
            maxLines = 1,
        )
    }
}

@androidx.compose.runtime.Composable
private fun CenterMessage(text: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                color = ColorProvider(day = ComposeColor(0xFF666666), night = ComposeColor(0xFFAAAAAA)),
            ),
        )
    }
}

private fun parseLabelColor(hex: String): ComposeColor = runCatching {
    val cleaned = hex.removePrefix("#")
    val parsed = cleaned.toLong(16)
    ComposeColor(0xFF000000 or parsed)
}.getOrDefault(ComposeColor(0xFFCCCCCC))

private fun isLight(color: ComposeColor): Boolean {
    val yiq = (color.red * 299 + color.green * 587 + color.blue * 114) / 1000
    return yiq >= 0.5f
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        IssueGlanceWidget().update(context, glanceId)
    }
}
