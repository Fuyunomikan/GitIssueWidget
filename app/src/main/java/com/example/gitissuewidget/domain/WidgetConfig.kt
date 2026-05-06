package com.example.gitissuewidget.domain

/**
 * 1 ウィジェットの設定。
 *
 * 表示モードは [projectTitle] の有無で分岐:
 * - **Project モード** (`projectTitle != null`): viewer の Projects v2 から指定タイトルの Project を解決し、
 *   [projectColumnName] で指定されたカラム (Status SingleSelect オプション) の Issue を表示。
 *   [repoRefs] / [labels] / [stateFilter] / [assigneeLogin] は **追加絞り込み** として適用。
 * - **リポジトリモード** (`projectTitle == null`): 旧来通り [repoRefs] のリポジトリから REST 経由で Issue を取得。
 *
 * @param projectTitle Projects v2 のタイトル。null/空文字 → リポジトリモード
 * @param projectColumnName Project の Status カラム名。null/空 → Project 全カラム表示
 */
data class WidgetConfig(
    val appWidgetId: Int,
    val repoRefs: List<RepoRef>,
    val stateFilter: IssueFilter.StateFilter = IssueFilter.StateFilter.OPEN,
    val labels: List<String> = emptyList(),
    val assigneeLogin: String? = null,
    val projectTitle: String? = null,
    val projectColumnName: String? = null,
) {
    /** Project モードで動作するかどうか。 */
    val isProjectMode: Boolean get() = !projectTitle.isNullOrBlank()
}
