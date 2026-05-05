package com.example.gitissuewidget.domain

data class IssueFilter(
    val stateFilter: StateFilter = StateFilter.OPEN,
    val labels: List<String> = emptyList(),
    val assignee: String? = null,
    val sort: SortOption = SortOption.UPDATED,
    val direction: SortDirection = SortDirection.DESC,
    val perPage: Int = 20,
) {
    enum class StateFilter(val apiValue: String) {
        OPEN("open"),
        CLOSED("closed"),
        ALL("all"),
    }
}
