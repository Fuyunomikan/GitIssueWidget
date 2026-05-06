package com.example.gitissuewidget.widget

import android.appwidget.AppWidgetManager
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
import androidx.glance.action.Action
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
import com.example.gitissuewidget.MainActivity
import com.example.gitissuewidget.R
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.SortDirection
import com.example.gitissuewidget.domain.SortOption
import com.example.gitissuewidget.domain.WidgetConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

        val sort = container.preferenceStore.sortOption.first()
        val direction = container.preferenceStore.sortDirection.first()
        val perPage = container.preferenceStore.perPage.first()
        val showOpenBadge = container.preferenceStore.showOpenBadge.first()
        val showLabels = container.preferenceStore.showLabels.first()
        val showDueDate = container.preferenceStore.showDueDate.first()
        val dueDateWarningDays = container.preferenceStore.dueDateWarningDays.first()
        val dueDateFieldName = container.preferenceStore.dueDateFieldName.first()
        val filter = IssueFilter(
            stateFilter = widgetConfig?.stateFilter ?: IssueFilter.StateFilter.OPEN,
            labels = widgetConfig?.labels.orEmpty(),
            assignee = widgetConfig?.assigneeLogin,
            sort = sort,
            direction = direction,
            perPage = perPage,
        )
        val displayOptions = WidgetDisplayOptions(
            showOpenBadge = showOpenBadge,
            showLabels = showLabels,
            showDueDate = showDueDate,
            dueDateWarningDays = dueDateWarningDays,
        )

        // Project mode takes precedence: if widgetConfig.projectTitle is set, fetch project items.
        // Otherwise fall back to repository-based fetch.
        val state: WidgetState = when {
            !tokenSet -> WidgetState.NoToken
            widgetConfig != null && widgetConfig.isProjectMode ->
                loadProjectMode(container, widgetConfig, filter, appWidgetId, dueDateFieldName)
            else -> {
                val repos: List<RepoRef> = widgetConfig?.repoRefs?.takeIf { it.isNotEmpty() }
                    ?: container.preferenceStore.watchedRepos.first()
                if (repos.isEmpty()) WidgetState.NoRepo
                else loadRepositoryMode(container, repos, filter, appWidgetId)
            }
        }

        val configureAction: Action? = appWidgetId?.let { id ->
            val intent = Intent(context, WidgetQuickEditActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            actionStartActivity(intent)
        }

        val addIssueAction: Action = run {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NEW_ISSUE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            actionStartActivity(intent)
        }

        provideContent { WidgetContent(state, displayOptions, addIssueAction, configureAction) }
    }
}

private data class WidgetDisplayOptions(
    val showOpenBadge: Boolean = true,
    val showLabels: Boolean = true,
    val showDueDate: Boolean = true,
    /** 残日数 < この値（かつ 0 以上）で「あと〇日」を赤表示。0 なら警告しない。 */
    val dueDateWarningDays: Int = 3,
)

private suspend fun loadRepositoryMode(
    container: com.example.gitissuewidget.AppContainer,
    repos: List<RepoRef>,
    filter: IssueFilter,
    appWidgetId: Int?,
): WidgetState {
    val title = buildHeaderTitle(repos, filter)
    val results: List<Pair<RepoRef, Result<List<Issue>>>> = coroutineScope {
        repos.map { repo ->
            async { repo to container.issueRepository.fetchIssues(repo, filter) }
        }.awaitAll()
    }
    val successes = results.filter { it.second.isSuccess }
    return if (successes.isEmpty()) {
        val cached = appWidgetId?.let { container.issueCacheStore.load(it) }
        if (cached != null && cached.issues.isNotEmpty()) {
            WidgetState.Loaded(title, cached.issues, staleSinceMillis = cached.savedAtMillis)
        } else {
            val firstError = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            WidgetState.Error(
                repoName = repos.first().fullName,
                message = firstError?.message ?: "取得エラー",
            )
        }
    } else {
        val merged = successes
            .flatMap { it.second.getOrThrow() }
            .sortedByFilter(filter.sort, filter.direction)
            .take(filter.perPage)
        appWidgetId?.let { container.issueCacheStore.save(it, merged) }
        WidgetState.Loaded(title, merged, staleSinceMillis = null)
    }
}

/**
 * Project モード fetch:
 * 1. swipe / widget で共有される ProjectMetaCache から meta 解決
 * 2. `fetchProjectIssues` で対象カラムの Issue 一覧を取得
 * 3. 旧設定の追加絞り込み (repoRefs / labels / stateFilter) をクライアント側で適用
 * 4. 全失敗時はキャッシュフォールバック
 */
private suspend fun loadProjectMode(
    container: com.example.gitissuewidget.AppContainer,
    widgetConfig: WidgetConfig,
    filter: IssueFilter,
    appWidgetId: Int?,
    dueDateFieldName: String,
): WidgetState {
    val projectTitle = widgetConfig.projectTitle.orEmpty()
    val title = buildProjectHeaderTitle(widgetConfig, filter)

    val metaResult = container.issueRepository.resolveProjectByTitle(projectTitle)
    val meta = metaResult.getOrElse { e ->
        return cacheOrError(container, appWidgetId, title, e.message ?: "Project 取得エラー")
    }

    val itemsResult = container.issueRepository.fetchProjectIssues(
        projectMeta = meta,
        columnName = widgetConfig.projectColumnName,
        perPage = filter.perPage,
        dueDateFieldName = dueDateFieldName,
        applyTakeLimit = false,
    )
    val items = itemsResult.getOrElse { e ->
        return cacheOrError(container, appWidgetId, title, e.message ?: "Project items 取得エラー")
    }

    val filtered = items.applyAdditionalFilters(widgetConfig)
        .sortedByFilter(filter.sort, filter.direction)
        .take(filter.perPage)
    appWidgetId?.let { container.issueCacheStore.save(it, filtered) }
    return WidgetState.Loaded(title, filtered, staleSinceMillis = null)
}

private suspend fun cacheOrError(
    container: com.example.gitissuewidget.AppContainer,
    appWidgetId: Int?,
    title: String,
    errorMessage: String,
): WidgetState {
    val cached = appWidgetId?.let { container.issueCacheStore.load(it) }
    return if (cached != null && cached.issues.isNotEmpty()) {
        WidgetState.Loaded(title, cached.issues, staleSinceMillis = cached.savedAtMillis)
    } else {
        WidgetState.Error(repoName = title, message = errorMessage)
    }
}

private fun List<Issue>.applyAdditionalFilters(config: WidgetConfig): List<Issue> {
    val byRepo = if (config.repoRefs.isEmpty()) this
    else filter { it.repoRef in config.repoRefs }
    val byLabel = byRepo.filterByLabelConfig(config.labels)
    return when (config.stateFilter) {
        IssueFilter.StateFilter.ALL -> byLabel
        IssueFilter.StateFilter.OPEN -> byLabel.filter { it.state == IssueState.OPEN }
        IssueFilter.StateFilter.CLOSED -> byLabel.filter { it.state == IssueState.CLOSED }
    }
}

/**
 * ラベル設定によるフィルタ。
 * - 設定が空 → 全件通す
 * - [WidgetConfig.LABEL_NONE] のみ → ラベル空 Issue のみ
 * - 通常ラベルあり → 通常ラベルすべてを満たす Issue。さらに LABEL_NONE が含まれていればラベル空 Issue も OR で許可
 */
private fun List<Issue>.filterByLabelConfig(configLabels: List<String>): List<Issue> {
    if (configLabels.isEmpty()) return this
    val includeUnlabeled = configLabels.any { it == WidgetConfig.LABEL_NONE }
    val normalLabels = configLabels.filter { it != WidgetConfig.LABEL_NONE }.map { it.lowercase() }
    return filter { issue ->
        val names = issue.labels.map { it.name.lowercase() }.toSet()
        val matchesNormal = normalLabels.isNotEmpty() && normalLabels.all { it in names }
        val matchesUnlabeled = includeUnlabeled && issue.labels.isEmpty()
        matchesNormal || matchesUnlabeled
    }
}

private fun buildProjectHeaderTitle(config: WidgetConfig, filter: IssueFilter): String {
    val parts = mutableListOf<String>()
    parts += config.projectTitle.orEmpty()
    if (!config.projectColumnName.isNullOrBlank()) parts += config.projectColumnName
    if (filter.labels.isNotEmpty()) parts += filter.labels.joinToString(",")
    if (filter.assignee != null) parts += "@${filter.assignee}"
    return parts.joinToString(" · ")
}

private fun buildHeaderTitle(repos: List<RepoRef>, filter: IssueFilter): String {
    val repoLabel = when (repos.size) {
        0 -> "GitHub Issue"
        1 -> repos.first().fullName
        else -> "${repos.first().fullName} +${repos.size - 1}"
    }
    val parts = mutableListOf(repoLabel)
    if (filter.stateFilter != IssueFilter.StateFilter.OPEN) parts += filter.stateFilter.apiValue
    if (filter.labels.isNotEmpty()) parts += filter.labels.joinToString(",")
    if (filter.assignee != null) parts += "@${filter.assignee}"
    return parts.joinToString(" · ")
}

private fun List<Issue>.sortedByFilter(sort: SortOption, direction: SortDirection): List<Issue> {
    val asc = direction == SortDirection.ASC
    if (sort == SortOption.DUE_DATE) {
        // Issue.dueDate は ISO-8601 ("YYYY-MM-DD") なので文字列比較で日付順になる。
        // null (未設定) は方向に関わらず常に末尾へ。
        val (dated, undated) = partition { !it.dueDate.isNullOrBlank() }
        val datedSorted = dated.sortedBy { it.dueDate }
        val ordered = if (asc) datedSorted else datedSorted.reversed()
        return ordered + undated
    }
    val sorted = when (sort) {
        SortOption.UPDATED -> sortedBy { it.updatedAt }
        SortOption.CREATED -> sortedBy { it.createdAt }
        SortOption.COMMENTS -> sortedBy { it.commentsCount }
        SortOption.DUE_DATE -> this // unreachable, handled above
    }
    return if (asc) sorted else sorted.reversed()
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
private fun WidgetContent(
    state: WidgetState,
    displayOptions: WidgetDisplayOptions,
    addIssueAction: Action?,
    configureAction: Action?,
) {
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
            addIssueAction = addIssueAction,
            configureAction = configureAction,
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
                val showRepoInRow = state.issues.map { it.repoRef }.distinct().size > 1
                IssueListContent(state.issues, displayOptions, showRepoInRow)
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
private fun Header(title: String, addIssueAction: Action?, configureAction: Action?) {
    val iconTint = ColorProvider(day = ComposeColor(0xFF555555), night = ComposeColor(0xFFCCCCCC))
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
            colorFilter = ColorFilter.tint(iconTint),
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        )
        if (addIssueAction != null) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = "Issue追加",
                colorFilter = ColorFilter.tint(iconTint),
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(addIssueAction),
            )
        }
        if (configureAction != null) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_more_vert),
                contentDescription = "ウィジェット設定",
                colorFilter = ColorFilter.tint(iconTint),
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(configureAction),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun IssueListContent(
    issues: List<Issue>,
    displayOptions: WidgetDisplayOptions,
    showRepoInRow: Boolean,
) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(issues, itemId = { "${it.repoRef.fullName}#${it.number}".hashCode().toLong() }) { issue ->
            IssueRow(issue, displayOptions, showRepoInRow)
        }
    }
}

@androidx.compose.runtime.Composable
private fun IssueRow(issue: Issue, displayOptions: WidgetDisplayOptions, showRepoInRow: Boolean) {
    val openIntent = Intent(Intent.ACTION_VIEW, issue.htmlUrl.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(openIntent)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayOptions.showOpenBadge) {
                StateBadge(issue.state)
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                text = if (showRepoInRow) "${issue.repoRef.name} #${issue.number}" else "#${issue.number}",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(day = ComposeColor(0xFF666666), night = ComposeColor(0xFFAAAAAA)),
                ),
            )
            if (displayOptions.showDueDate && !issue.dueDate.isNullOrBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                DueDateBadge(issue.dueDate)
                val daysLeft = daysUntilDue(issue.dueDate)
                val warningDays = displayOptions.dueDateWarningDays
                if (daysLeft != null && daysLeft >= 0 && warningDays > 0 && daysLeft < warningDays) {
                    Spacer(GlanceModifier.width(3.dp))
                    DueWarningBadge(daysLeft)
                }
            }
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
        if (displayOptions.showLabels && issue.labels.isNotEmpty()) {
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
private fun DueDateBadge(dueDate: String) {
    val past = isPastDue(dueDate)
    val fgDay = if (past) ComposeColor(0xFFB71C1C) else ComposeColor(0xFF666666)
    val fgNight = if (past) ComposeColor(0xFFEF9A9A) else ComposeColor(0xFFAAAAAA)
    Text(
        text = formatDueDateLong(dueDate),
        style = TextStyle(
            fontSize = 10.sp,
            color = ColorProvider(day = fgDay, night = fgNight),
        ),
        maxLines = 1,
    )
}

/**
 * 期限間近警告のインラインバッジ。「あと〇日」を太字赤で 1 行表示する。
 * 親 [Row] にフラットに置かれることを想定（Glance はネスト Row/Column が増えるとレイアウト制約が
 * 厳しくなり警告テキストが見切れる場合があるため、[DueDateBadge] と兄弟として並べる）。
 */
@androidx.compose.runtime.Composable
private fun DueWarningBadge(daysLeft: Int) {
    Text(
        text = "あと${daysLeft}日",
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(day = ComposeColor(0xFFB71C1C), night = ComposeColor(0xFFEF9A9A)),
        ),
        maxLines = 1,
    )
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

/**
 * "YYYY-MM-DD" を "YYYY/MM/DD" 形式にする。パース失敗時は元の文字列をそのまま返す。
 */
private fun formatDueDateLong(iso: String): String = runCatching {
    val parts = iso.split('-')
    if (parts.size < 3) return@runCatching iso
    "${parts[0]}/${parts[1].padStart(2, '0')}/${parts[2].padStart(2, '0')}"
}.getOrDefault(iso)

/**
 * 期限が今日より過去なら true。パース不能・未来・今日なら false。
 */
private fun isPastDue(iso: String): Boolean = runCatching {
    java.time.LocalDate.parse(iso).isBefore(java.time.LocalDate.now())
}.getOrDefault(false)

/** 今日から期限日までの残日数。今日 = 0、明日 = 1、昨日 = -1。パース不能なら null。 */
private fun daysUntilDue(iso: String): Int? = runCatching {
    java.time.temporal.ChronoUnit.DAYS.between(
        java.time.LocalDate.now(),
        java.time.LocalDate.parse(iso),
    ).toInt()
}.getOrNull()

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        IssueGlanceWidget().update(context, glanceId)
    }
}
