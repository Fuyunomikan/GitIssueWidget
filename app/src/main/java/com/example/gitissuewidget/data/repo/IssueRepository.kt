package com.example.gitissuewidget.data.repo

import com.example.gitissuewidget.data.local.ProjectMetaCache
import com.example.gitissuewidget.data.remote.GitHubApi
import com.example.gitissuewidget.data.remote.GitHubGraphQlClient
import com.example.gitissuewidget.data.remote.GraphQlException
import com.example.gitissuewidget.data.remote.dto.CreateIssueRequest
import com.example.gitissuewidget.data.remote.dto.IssueDto
import com.example.gitissuewidget.data.remote.dto.LabelDto
import com.example.gitissuewidget.data.remote.dto.UserDto
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.ProjectMeta
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.User
import retrofit2.HttpException

class IssueRepository(
    private val api: GitHubApi,
    private val graphQl: GitHubGraphQlClient,
    private val projectMetaCache: ProjectMetaCache,
) {

    suspend fun fetchCurrentUser(): Result<User> = runCatching {
        api.getCurrentUser().toDomain()
    }.mapHttpError()

    suspend fun fetchIssues(repo: RepoRef, filter: IssueFilter): Result<List<Issue>> = runCatching {
        api.listIssues(
            owner = repo.owner,
            repo = repo.name,
            state = filter.stateFilter.apiValue,
            labels = filter.labels.takeIf { it.isNotEmpty() }?.joinToString(","),
            assignee = filter.assignee,
            sort = filter.sort.apiValue,
            direction = filter.direction.apiValue,
            perPage = filter.perPage,
        )
            .filter { it.pullRequest == null }
            .map { it.toDomain(repo) }
    }.mapHttpError()

    suspend fun createIssue(
        repo: RepoRef,
        title: String,
        body: String?,
        labels: List<String>,
    ): Result<Issue> = runCatching {
        val request = CreateIssueRequest(title = title, body = body, labels = labels)
        api.createIssue(repo.owner, repo.name, request).toDomain(repo)
    }.mapHttpError()

    suspend fun fetchAvailableLabels(repo: RepoRef): Result<List<Label>> = runCatching {
        api.listLabels(repo.owner, repo.name).map { it.toDomain() }
    }.mapHttpError()

    /**
     * viewer の Project 一覧をキャッシュ込みで取得（設定画面の Project タイトル候補表示用）。
     */
    suspend fun listAvailableProjects(forceRefresh: Boolean = false): Result<List<ProjectMeta>> = runCatching {
        if (forceRefresh) projectMetaCache.refresh() else projectMetaCache.list()
    }.mapHttpError()

    /**
     * Project タイトルから [ProjectMeta] を解決。見つからなければ [GraphQlException]。
     * スワイプアクション・ウィジェット表示の両方で使う。
     */
    suspend fun resolveProjectByTitle(title: String): Result<ProjectMeta> = runCatching {
        projectMetaCache.findByTitle(title)
            ?: throw GraphQlException(
                "Project \"$title\" が見つかりません。タイトルが正しいか、PAT に project スコープがあるか確認してください。",
            )
    }.mapHttpError()

    /**
     * 指定 Project の指定カラムの Issue を取得。`columnName` が null の場合は全カラム。
     * `perPage` は GitHub の `items(first:)` 上限 100 と、カラムフィルタによる削減を見込んで
     * `perPage*3 (≤100)` を fetch してからクライアント側で絞り込む。
     *
     * @param dueDateFieldName Projects v2 の Date 型カスタムフィールドの名前。マッチした値は
     *   各 Issue の [Issue.dueDate] に入る。null/空のときは抽出をスキップ（dueDate は常に null）。
     */
    suspend fun fetchProjectIssues(
        projectMeta: ProjectMeta,
        columnName: String?,
        perPage: Int,
        dueDateFieldName: String? = null,
    ): Result<List<Issue>> = runCatching {
        val fetchSize = (perPage * 3).coerceIn(perPage, 100)
        val items = graphQl.listProjectItems(
            projectNodeId = projectMeta.project.nodeId,
            first = fetchSize,
            dueDateFieldName = dueDateFieldName,
        )
        val filtered = if (columnName.isNullOrBlank()) items
        else items.filter { it.statusOptionName.equals(columnName, ignoreCase = true) }
        filtered.map { it.issue }.take(perPage)
    }.mapHttpError()

    /**
     * 指定 Issue を Project の指定カラムに移動。Issue が Project 未追加なら自動追加してから Status を更新する。
     *
     * @throws GraphQlException 対象カラムが Project に存在しない場合 / Issue.nodeId が空の場合
     */
    suspend fun moveIssueToColumn(
        issueNodeId: String,
        projectMeta: ProjectMeta,
        targetColumnName: String,
    ): Result<Unit> = runCatching {
        if (issueNodeId.isBlank()) {
            throw GraphQlException("Issue の nodeId が空です。一覧を更新してから再試行してください。")
        }
        val column = projectMeta.findColumn(targetColumnName)
            ?: throw GraphQlException(
                "カラム \"$targetColumnName\" が Project \"${projectMeta.project.title}\" に存在しません。",
            )
        val existingItemId = graphQl.findProjectItemId(projectMeta.project.nodeId, issueNodeId)
        val itemId = existingItemId ?: graphQl.addItemToProject(projectMeta.project.nodeId, issueNodeId)
        graphQl.updateItemStatus(
            projectNodeId = projectMeta.project.nodeId,
            itemId = itemId,
            statusFieldId = projectMeta.statusFieldId,
            optionId = column.optionId,
        )
    }.mapHttpError()

    private fun <T> Result<T>.mapHttpError(): Result<T> = recoverCatching { e ->
        when (e) {
            is HttpException -> when (e.code()) {
                401 -> throw GitHubError.Unauthorized
                403 -> throw GitHubError.RateLimited
                404 -> throw GitHubError.NotFound
                else -> throw GitHubError.Http(e.code(), e.message())
            }
            else -> throw e
        }
    }
}

sealed class GitHubError(message: String) : RuntimeException(message) {
    data object Unauthorized : GitHubError("認証エラー: PATが無効か期限切れです")
    data object RateLimited : GitHubError("レート制限/権限不足です")
    data object NotFound : GitHubError("リポジトリが見つかりません")
    data class Http(val code: Int, val msg: String) : GitHubError("HTTP $code $msg")
}

private fun UserDto.toDomain() = User(login = login, name = name, avatarUrl = avatarUrl)

private fun LabelDto.toDomain() = Label(name = name, colorHex = color)

private fun IssueDto.toDomain(repo: RepoRef) = Issue(
    number = number,
    title = title,
    htmlUrl = htmlUrl,
    state = IssueState.fromApi(state),
    labels = labels.map { it.toDomain() },
    updatedAt = updatedAt,
    createdAt = createdAt,
    commentsCount = comments,
    repoRef = repo,
    nodeId = nodeId,
)
