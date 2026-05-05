package com.example.gitissuewidget.data.repo

import com.example.gitissuewidget.data.remote.GitHubApi
import com.example.gitissuewidget.data.remote.dto.IssueDto
import com.example.gitissuewidget.data.remote.dto.LabelDto
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
            sort = filter.sort.apiValue,
            direction = filter.direction.apiValue,
            perPage = filter.perPage,
        )
            .filter { it.pullRequest == null }
            .map { it.toDomain(repo) }
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
    repoRef = repo,
)
