package com.example.gitissuewidget.domain

/**
 * GitHub Projects v2 のプロジェクト本体。
 * GraphQL の `ProjectV2.id` を `nodeId` に保持する（mutation の入力に必須）。
 */
data class Project(
    val nodeId: String,
    val number: Int,
    val title: String,
)

/**
 * Status フィールド (SingleSelect) の各オプション = カンバンの「カラム」。
 * `optionId` は `updateProjectV2ItemFieldValue` mutation の `singleSelectOptionId` に使用する。
 */
data class ProjectColumn(
    val optionId: String,
    val name: String,
)

/**
 * Project + Status フィールドの ID + そのオプション一覧をまとめたメタ情報。
 * `ProjectMetaCache` の格納単位。
 *
 * @param statusFieldId Status (SingleSelect) フィールドの GraphQL ID
 */
data class ProjectMeta(
    val project: Project,
    val statusFieldId: String,
    val columns: List<ProjectColumn>,
) {
    fun findColumn(name: String): ProjectColumn? =
        columns.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
