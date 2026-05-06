package com.example.gitissuewidget.domain

/**
 * メイン画面で Issue カードをスワイプしたときに実行するアクション。
 *
 * Projects v2 のカラム（Status SingleSelect オプション）への **移動** として実装される。
 * 設定画面で指定された「スワイプ用 Project」のタイトルを使い、対象カラムへ Issue を移動する。
 *
 * - PENDING: 設定画面で指定された Pending カラムに移動（デフォルト名: "Pending"）
 * - COMPLETE: 設定画面で指定された Done カラムに移動（デフォルト名: "Done"）
 *
 * Issue close 系の操作（旧 DELETE）は廃止。close したい場合は GitHub 側 / Project ワークフローで自動化する。
 */
enum class SwipeAction(val label: String) {
    NONE("なし"),
    PENDING("Pending カラムに移動"),
    COMPLETE("Done カラムに移動"),
}
