package com.example.gitissuewidget.domain

/**
 * メイン画面で Issue カードをスワイプしたときに実行するアクション。
 *
 * GitHub Issue の構造に対応 (Issues をカンバンとして使う運用):
 * - DELETE: state=closed, state_reason="not_planned" （取り下げ扱い）
 * - COMPLETE: state=closed, state_reason="completed" （Done カラムに移動）
 * - PENDING: state=open に戻し、"Pending" ラベルを付与（Pending カラムに移動）
 */
enum class SwipeAction(val label: String) {
    NONE("なし"),
    DELETE("削除"),
    COMPLETE("完了 (Done カラムに移動)"),
    PENDING("Pending カラムに移動"),
}
