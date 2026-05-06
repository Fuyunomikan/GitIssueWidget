package com.example.gitissuewidget.domain

enum class SortOption {
    CREATED,
    UPDATED,
    COMMENTS,

    /**
     * Projects v2 の Date 型カスタムフィールド（"Due Date" 等）でソートする。
     * REST API はこのソートをサポートしないため、REST 経由 fetch では UPDATED にフォールバックする
     * （[apiValue] が "updated" を返す）。実際のソートはクライアント側で [Issue.dueDate] を使って行う。
     */
    DUE_DATE;

    /**
     * REST API の `sort=` クエリ値。GitHub REST `/issues` は created/updated/comments のみ受け付けるため、
     * DUE_DATE は "updated" にフォールバックする。
     */
    val apiValue: String
        get() = when (this) {
            CREATED -> "created"
            UPDATED -> "updated"
            COMMENTS -> "comments"
            DUE_DATE -> "updated"
        }

    /** UI 表示用ラベル。 */
    val label: String
        get() = when (this) {
            CREATED -> "created"
            UPDATED -> "updated"
            COMMENTS -> "comments"
            DUE_DATE -> "due date"
        }
}

enum class SortDirection(val apiValue: String) {
    ASC("asc"),
    DESC("desc"),
}
