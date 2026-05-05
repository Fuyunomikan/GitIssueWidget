package com.example.gitissuewidget.domain

enum class SortOption(val apiValue: String) {
    CREATED("created"),
    UPDATED("updated"),
    COMMENTS("comments"),
}

enum class SortDirection(val apiValue: String) {
    ASC("asc"),
    DESC("desc"),
}
