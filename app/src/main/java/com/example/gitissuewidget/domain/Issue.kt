package com.example.gitissuewidget.domain

data class Issue(
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val state: IssueState,
    val labels: List<Label>,
    val updatedAt: String,
    val createdAt: String,
    val commentsCount: Int,
    val repoRef: RepoRef,
    /** GraphQL の `Issue.id`。Projects v2 の操作（item追加/Status更新）に必須。空文字は未取得を表す。 */
    val nodeId: String = "",
)

enum class IssueState { OPEN, CLOSED;

    companion object {
        fun fromApi(value: String): IssueState = when (value.lowercase()) {
            "closed" -> CLOSED
            else -> OPEN
        }
    }
}
