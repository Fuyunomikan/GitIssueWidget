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
    val labelFilterMode: LabelFilterMode = LabelFilterMode.AND,
    val assigneeLogin: String? = null,
    val projectTitle: String? = null,
    val projectColumnName: String? = null,
) {
    /** Project モードで動作するかどうか。 */
    val isProjectMode: Boolean get() = !projectTitle.isNullOrBlank()

    /**
     * 通常ラベル群の結合方法。`LABEL_NONE` (ラベルなし) は常にこれと OR で結合される。
     * - [AND]: 指定したラベルをすべて持つ Issue のみ通す（GitHub REST API のデフォルト挙動と同じ）
     * - [OR]: 指定したラベルのいずれかを持つ Issue を通す（クライアント側でフィルタ）
     */
    enum class LabelFilterMode(val displayName: String) {
        AND("AND (すべて一致)"),
        OR("OR (いずれか一致)"),
    }

    companion object {
        /**
         * [labels] に含めると「ラベルが 1 つも付いていない Issue」を表示対象に加えるための予約名。
         * 通常のラベル名と衝突しないよう `__` で囲んでいる。
         * [LabelFilterMode] に関わらず常に OR で結合される。
         */
        const val LABEL_NONE = "__NO_LABEL__"

        /** UI 上の表示文言。チップに表示するラベル候補。 */
        const val LABEL_NONE_DISPLAY = "(ラベルなし)"
    }
}
