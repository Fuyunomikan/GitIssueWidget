package com.example.gitissuewidget.data.repo

import com.example.gitissuewidget.data.remote.GitHubApi
import com.example.gitissuewidget.data.remote.dto.AddLabelsRequest
import com.example.gitissuewidget.data.remote.dto.CreateIssueRequest
import com.example.gitissuewidget.data.remote.dto.IssueDto
import com.example.gitissuewidget.data.remote.dto.LabelDto
import com.example.gitissuewidget.data.remote.dto.UpdateIssueRequest
import com.example.gitissuewidget.data.remote.dto.UserDto
import com.example.gitissuewidget.domain.Issue
import com.example.gitissuewidget.domain.IssueFilter
import com.example.gitissuewidget.domain.IssueState
import com.example.gitissuewidget.domain.Label
import com.example.gitissuewidget.domain.RepoRef
import com.example.gitissuewidget.domain.User
import retrofit2.HttpException

class IssueRepository(private val api: GitHubApi) {

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
     * @param stateReason "completed" or "not_planned"
     */
    suspend fun closeIssue(repo: RepoRef, number: Int, stateReason: String): Result<Issue> = runCatching {
        val request = UpdateIssueRequest(state = "closed", stateReason = stateReason)
        api.updateIssue(repo.owner, repo.name, number, request).toDomain(repo)
    }.mapHttpError()

    /**
     * 「Pending カラムに移動」セマンティクス。
     * Issue が closed なら open に戻し、既存ラベルを保ったまま `"Pending"` ラベルを追加する。
     *
     * 実装メモ: `PATCH /issues/{n}` の `labels` フィールドを使うと存在しないラベル名を
     * GitHub が自動作成するため、リポジトリに事前に "Pending" ラベルが無くても動作する。
     * 一方 `POST /issues/{n}/labels` は存在しないラベルでは 422 エラーになるので使わない。
     * state 変更とラベル変更を 1 リクエストにまとめて API 呼び出しを 1 回に抑える。
     */
    suspend fun moveToPending(
        repo: RepoRef,
        number: Int,
        currentState: IssueState,
        currentLabels: List<String>,
    ): Result<Unit> = runCatching {
        val mergedLabels = (currentLabels + PENDING_LABEL).distinct()
        val request = UpdateIssueRequest(
            state = if (currentState == IssueState.CLOSED) "open" else null,
            labels = mergedLabels,
        )
        api.updateIssue(repo.owner, repo.name, number, request)
        Unit
    }.mapHttpError()

    companion object {
        const val PENDING_LABEL = "Pending"
    }

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
)
