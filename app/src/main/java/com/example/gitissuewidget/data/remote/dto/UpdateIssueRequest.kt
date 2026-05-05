package com.example.gitissuewidget.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateIssueRequest(
    val state: String? = null,
    @SerialName("state_reason") val stateReason: String? = null,
    /**
     * 渡すと Issue のラベルセットを「置換」する。
     * GitHub は配列内の存在しないラベル名をデフォルト色で自動作成する。
     */
    val labels: List<String>? = null,
)

@Serializable
data class AddLabelsRequest(
    val labels: List<String>,
)
