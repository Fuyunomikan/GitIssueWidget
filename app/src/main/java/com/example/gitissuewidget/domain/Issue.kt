package com.example.gitissuewidget.domain

data class Issue(
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val state: IssueState,
    val labels: List<Label>,
    val updatedAt: String,
    val repoRef: RepoRef,
)

enum class IssueState { OPEN, CLOSED;

    companion object {
        fun fromApi(value: String): IssueState = when (value.lowercase()) {
            "closed" -> CLOSED
            else -> OPEN
        }
    }
}
