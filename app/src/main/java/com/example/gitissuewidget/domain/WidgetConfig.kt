package com.example.gitissuewidget.domain

data class WidgetConfig(
    val appWidgetId: Int,
    val repoRef: RepoRef,
    val stateFilter: IssueFilter.StateFilter = IssueFilter.StateFilter.OPEN,
    val labels: List<String> = emptyList(),
    val assigneeLogin: String? = null,
)
