package com.example.gitissuewidget.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * GraphQL リクエストボディ。`variables` は任意の JSON オブジェクト。
 */
@Serializable
data class GraphQlRequest(
    val query: String,
    val variables: JsonObject? = null,
)

/**
 * GraphQL レスポンス。`data` は呼び出し側で必要な型に手動でナビゲートする。
 */
@Serializable
data class GraphQlResponse(
    val data: JsonObject? = null,
    val errors: List<GraphQlError>? = null,
)

@Serializable
data class GraphQlError(
    val message: String,
    val type: String? = null,
)
